#!/usr/bin/env python3
"""
PostgreSQL MCP (Model Context Protocol) Server
------------------------------------------------
Exposes full DBA capabilities to AI agents via the MCP protocol over SSE transport.

Supported operations:
  - SQL query execution (SELECT / DML / DDL with explicit flag)
  - EXPLAIN / EXPLAIN ANALYZE query plans
  - Schema inspection (tables, columns, indexes, constraints, sequences, views)
  - Table/index statistics and pg_stat_* views
  - Lock analysis (pg_locks + pg_stat_activity join)
  - Long-running query detection
  - VACUUM / ANALYZE / REINDEX execution
  - Replication status (pg_stat_replication, pg_replication_slots)
  - Role and database management (list, create, drop, grant)
  - Extension management
  - Table bloat estimation
  - Missing / unused index analysis
  - Database size reporting
  - Connection pool status
  - Cache hit ratio
  - Checkpoint / WAL statistics

Connection supports:
  - Embedded PostgreSQL (cluster-internal DNS, plain password auth)
  - AWS RDS Aurora (external endpoint + optional IAM auth token via boto3)

Configuration via environment variables:
  PGHOST      PostgreSQL host
  PGPORT      PostgreSQL port (default: 5432)
  PGDATABASE  Default database (default: postgres)
  PGUSER      Username
  PGPASSWORD  Password (ignored when USE_IAM_AUTH=true)
  USE_IAM_AUTH  'true' to obtain an IAM auth token from AWS (RDS only)
  AWS_REGION  AWS region for IAM token (default: us-west-2)
  MCP_HOST    Bind host (default: 0.0.0.0)
  MCP_PORT    Bind port  (default: 8000)
"""

from __future__ import annotations

import asyncio
import json
import logging
import os
import secrets
import textwrap
from contextlib import asynccontextmanager
from typing import Any

import asyncpg
import boto3
import uvicorn
from fastapi import FastAPI, Request
from fastapi.responses import JSONResponse, StreamingResponse
from mcp.server import Server
from mcp.server.sse import SseServerTransport
from mcp.types import (
    TextContent,
    Tool,
)

# ---------------------------------------------------------------------------
# Logging
# ---------------------------------------------------------------------------
logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s  %(levelname)-8s  %(name)s  %(message)s",
)
log = logging.getLogger("db-mcp")

# ---------------------------------------------------------------------------
# Configuration
# ---------------------------------------------------------------------------
# env var names match db-credentials secret keys
PGHOST = os.environ["DB_HOST"]
PGPORT = int(os.environ.get("DB_PORT", 5432))
PGDATABASE = os.environ.get("DB_DATABASE", "postgres")
PGUSER = os.environ["DB_USERNAME"]
PGPASSWORD = os.environ.get("DB_PASSWORD", "")
USE_IAM_AUTH = os.environ.get("USE_IAM_AUTH", "false").lower() == "true"
AWS_REGION = os.environ.get("AWS_REGION", "us-west-2")
MCP_HOST = os.environ.get("MCP_HOST", "0.0.0.0")
MCP_PORT = int(os.environ.get("MCP_PORT", 8000))

# Protection token — injected from rancher2_secret.db-credentials (DB_MCP_TOKEN key).
# All MCP endpoints require  Authorization: Bearer <token>  except /health.
_MCP_TOKEN: str = os.environ.get("DB_MCP_TOKEN", "")
if not _MCP_TOKEN:
    raise RuntimeError(
        "DB_MCP_TOKEN environment variable is required but not set. "
        "Inject it via the db-credentials Kubernetes secret."
    )

# ---------------------------------------------------------------------------
# Database helpers
# ---------------------------------------------------------------------------

def _get_rds_iam_password() -> str:
    """Generate a temporary RDS IAM auth token (valid 15 min)."""
    client = boto3.client("rds", region_name=AWS_REGION)
    token = client.generate_db_auth_token(
        DBHostname=PGHOST,
        Port=PGPORT,
        DBUsername=PGUSER,
        Region=AWS_REGION,
    )
    log.info("RDS IAM auth token refreshed")
    return token


async def _get_pool(database: str = PGDATABASE) -> asyncpg.Pool:
    """Create a short-lived connection pool for one operation."""
    password = _get_rds_iam_password() if USE_IAM_AUTH else PGPASSWORD
    ssl_ctx: Any = "require" if USE_IAM_AUTH else None
    return await asyncpg.create_pool(
        host=PGHOST,
        port=PGPORT,
        database=database,
        user=PGUSER,
        password=password,
        ssl=ssl_ctx,
        min_size=1,
        max_size=5,
        command_timeout=120,
    )


async def _query(sql: str, database: str = PGDATABASE) -> list[dict]:
    """Execute *sql* and return rows as a list of dicts."""
    pool = await _get_pool(database)
    try:
        async with pool.acquire() as conn:
            rows = await conn.fetch(sql)
            return [dict(r) for r in rows]
    finally:
        await pool.close()


async def _execute(sql: str, database: str = PGDATABASE) -> str:
    """Execute a DML/DDL statement and return the command tag."""
    pool = await _get_pool(database)
    try:
        async with pool.acquire() as conn:
            result = await conn.execute(sql)
            return str(result)
    finally:
        await pool.close()


def _rows_to_text(rows: list[dict]) -> str:
    if not rows:
        return "(no rows)"
    keys = list(rows[0].keys())
    header = " | ".join(keys)
    sep = "-+-".join("-" * len(k) for k in keys)
    lines = [header, sep] + [" | ".join(str(r.get(k, "")) for k in keys) for r in rows]
    return "\n".join(lines)


# ---------------------------------------------------------------------------
# MCP Server definition
# ---------------------------------------------------------------------------
mcp_server = Server("db-mcp")


# ── Tool registry ──────────────────────────────────────────────────────────

@mcp_server.list_tools()
async def list_tools() -> list[Tool]:
    return [
        Tool(
            name="execute_query",
            description=(
                "Execute a SQL statement against PostgreSQL. "
                "Safe SELECT queries are always allowed. "
                "Pass allow_write=true for DML/DDL (INSERT/UPDATE/DELETE/CREATE/DROP/ALTER)."
            ),
            inputSchema={
                "type": "object",
                "properties": {
                    "sql": {"type": "string", "description": "SQL to execute"},
                    "database": {"type": "string", "description": "Target database (default: env PGDATABASE)"},
                    "allow_write": {"type": "boolean", "description": "Set true to allow DML/DDL", "default": False},
                },
                "required": ["sql"],
            },
        ),
        Tool(
            name="explain_query",
            description="Return EXPLAIN or EXPLAIN ANALYZE output for a SQL statement.",
            inputSchema={
                "type": "object",
                "properties": {
                    "sql": {"type": "string"},
                    "analyze": {"type": "boolean", "description": "Run EXPLAIN ANALYZE (executes the query)", "default": False},
                    "buffers": {"type": "boolean", "description": "Include buffer usage (only with analyze=true)", "default": False},
                    "format": {"type": "string", "enum": ["text", "json"], "default": "text"},
                    "database": {"type": "string"},
                },
                "required": ["sql"],
            },
        ),
        Tool(
            name="list_databases",
            description="List all databases in the PostgreSQL cluster.",
            inputSchema={"type": "object", "properties": {}},
        ),
        Tool(
            name="list_schemas",
            description="List schemas in a database.",
            inputSchema={
                "type": "object",
                "properties": {"database": {"type": "string"}},
            },
        ),
        Tool(
            name="list_tables",
            description="List tables (and views/sequences) in a schema.",
            inputSchema={
                "type": "object",
                "properties": {
                    "schema": {"type": "string", "default": "public"},
                    "database": {"type": "string"},
                },
            },
        ),
        Tool(
            name="describe_table",
            description="Return column definitions, constraints, and indexes for a table.",
            inputSchema={
                "type": "object",
                "properties": {
                    "table": {"type": "string"},
                    "schema": {"type": "string", "default": "public"},
                    "database": {"type": "string"},
                },
                "required": ["table"],
            },
        ),
        Tool(
            name="table_stats",
            description="Return pg_stat_user_tables statistics for one or all tables.",
            inputSchema={
                "type": "object",
                "properties": {
                    "table": {"type": "string", "description": "Specific table name, or omit for all"},
                    "schema": {"type": "string", "default": "public"},
                    "database": {"type": "string"},
                },
            },
        ),
        Tool(
            name="index_stats",
            description="Return pg_stat_user_indexes for a schema.",
            inputSchema={
                "type": "object",
                "properties": {
                    "schema": {"type": "string", "default": "public"},
                    "database": {"type": "string"},
                },
            },
        ),
        Tool(
            name="active_locks",
            description="Show currently held locks joined with blocking/waiting sessions.",
            inputSchema={"type": "object", "properties": {"database": {"type": "string"}}},
        ),
        Tool(
            name="long_running_queries",
            description="List queries running longer than N seconds.",
            inputSchema={
                "type": "object",
                "properties": {
                    "min_seconds": {"type": "integer", "default": 30},
                },
            },
        ),
        Tool(
            name="vacuum_table",
            description=(
                "Run VACUUM (optionally ANALYZE or FULL) on a table. "
                "VACUUM FULL rewrites the table — only use during maintenance windows."
            ),
            inputSchema={
                "type": "object",
                "properties": {
                    "table": {"type": "string"},
                    "schema": {"type": "string", "default": "public"},
                    "analyze": {"type": "boolean", "default": False},
                    "full": {"type": "boolean", "default": False},
                    "database": {"type": "string"},
                },
                "required": ["table"],
            },
        ),
        Tool(
            name="analyze_table",
            description="Run ANALYZE on a specific table or the entire database.",
            inputSchema={
                "type": "object",
                "properties": {
                    "table": {"type": "string", "description": "Omit to analyze entire database"},
                    "schema": {"type": "string", "default": "public"},
                    "database": {"type": "string"},
                },
            },
        ),
        Tool(
            name="reindex_table",
            description="Run REINDEX TABLE for a given table.",
            inputSchema={
                "type": "object",
                "properties": {
                    "table": {"type": "string"},
                    "schema": {"type": "string", "default": "public"},
                    "database": {"type": "string"},
                },
                "required": ["table"],
            },
        ),
        Tool(
            name="replication_status",
            description="Show pg_stat_replication and pg_replication_slots.",
            inputSchema={"type": "object", "properties": {}},
        ),
        Tool(
            name="list_roles",
            description="List all PostgreSQL roles/users.",
            inputSchema={"type": "object", "properties": {}},
        ),
        Tool(
            name="create_role",
            description="Create a new PostgreSQL role.",
            inputSchema={
                "type": "object",
                "properties": {
                    "role_name": {"type": "string"},
                    "password": {"type": "string"},
                    "login": {"type": "boolean", "default": True},
                    "superuser": {"type": "boolean", "default": False},
                    "create_db": {"type": "boolean", "default": False},
                },
                "required": ["role_name"],
            },
        ),
        Tool(
            name="drop_role",
            description="Drop a PostgreSQL role.",
            inputSchema={
                "type": "object",
                "properties": {"role_name": {"type": "string"}},
                "required": ["role_name"],
            },
        ),
        Tool(
            name="create_database",
            description="Create a new database.",
            inputSchema={
                "type": "object",
                "properties": {
                    "db_name": {"type": "string"},
                    "owner": {"type": "string"},
                },
                "required": ["db_name"],
            },
        ),
        Tool(
            name="drop_database",
            description="Drop a database. Use with caution — this is irreversible.",
            inputSchema={
                "type": "object",
                "properties": {
                    "db_name": {"type": "string"},
                    "force": {"type": "boolean", "default": False, "description": "Terminate active connections before drop"},
                },
                "required": ["db_name"],
            },
        ),
        Tool(
            name="list_extensions",
            description="List installed (and available) extensions in a database.",
            inputSchema={
                "type": "object",
                "properties": {
                    "available": {"type": "boolean", "default": False, "description": "Also list available (not yet installed) extensions"},
                    "database": {"type": "string"},
                },
            },
        ),
        Tool(
            name="table_bloat",
            description="Estimate dead-tuple bloat for user tables using pg_stat_user_tables.",
            inputSchema={
                "type": "object",
                "properties": {
                    "schema": {"type": "string", "default": "public"},
                    "min_dead_tuples": {"type": "integer", "default": 10000},
                    "database": {"type": "string"},
                },
            },
        ),
        Tool(
            name="missing_indexes",
            description="Suggest missing indexes based on sequential scan statistics.",
            inputSchema={
                "type": "object",
                "properties": {
                    "min_seq_scans": {"type": "integer", "default": 100},
                    "database": {"type": "string"},
                },
            },
        ),
        Tool(
            name="unused_indexes",
            description="List indexes that have never (or rarely) been used.",
            inputSchema={
                "type": "object",
                "properties": {
                    "max_idx_scans": {"type": "integer", "default": 0},
                    "schema": {"type": "string", "default": "public"},
                    "database": {"type": "string"},
                },
            },
        ),
        Tool(
            name="database_sizes",
            description="Report sizes of all databases.",
            inputSchema={"type": "object", "properties": {}},
        ),
        Tool(
            name="table_sizes",
            description="Report sizes (table + toast + index) for tables in a schema.",
            inputSchema={
                "type": "object",
                "properties": {
                    "schema": {"type": "string", "default": "public"},
                    "database": {"type": "string"},
                },
            },
        ),
        Tool(
            name="cache_hit_ratio",
            description="Report buffer cache hit ratio for tables and indexes.",
            inputSchema={"type": "object", "properties": {"database": {"type": "string"}}},
        ),
        Tool(
            name="checkpoint_stats",
            description="Show pg_stat_bgwriter checkpoint and WAL statistics.",
            inputSchema={"type": "object", "properties": {}},
        ),
        Tool(
            name="connection_stats",
            description="Show current connection counts grouped by state and user.",
            inputSchema={"type": "object", "properties": {}},
        ),
        Tool(
            name="kill_query",
            description="Terminate a backend by PID (pg_terminate_backend).",
            inputSchema={
                "type": "object",
                "properties": {
                    "pid": {"type": "integer"},
                    "cancel_only": {"type": "boolean", "default": False, "description": "Use pg_cancel_backend instead of terminate"},
                },
                "required": ["pid"],
            },
        ),
    ]


# ── Tool call handler ──────────────────────────────────────────────────────

@mcp_server.call_tool()
async def call_tool(name: str, arguments: dict) -> list[TextContent]:
    try:
        result = await _dispatch(name, arguments)
        return [TextContent(type="text", text=result)]
    except Exception as exc:  # noqa: BLE001
        log.exception("Tool %s failed: %s", name, exc)
        return [TextContent(type="text", text=f"ERROR: {exc}")]


async def _dispatch(name: str, args: dict) -> str:  # noqa: C901  (complex but intentional)
    db = args.get("database", PGDATABASE)

    # ── execute_query ──────────────────────────────────────────────────────
    if name == "execute_query":
        sql = args["sql"].strip()
        allow_write = args.get("allow_write", False)
        first_word = sql.split()[0].upper() if sql else ""
        read_only_prefixes = {"SELECT", "WITH", "EXPLAIN", "SHOW", "TABLE"}
        if first_word not in read_only_prefixes and not allow_write:
            return "BLOCKED: Non-SELECT statement requires allow_write=true."
        if first_word in {"SELECT", "WITH", "TABLE", "EXPLAIN", "SHOW"}:
            rows = await _query(sql, db)
            return _rows_to_text(rows)
        tag = await _execute(sql, db)
        return f"OK: {tag}"

    # ── explain_query ──────────────────────────────────────────────────────
    elif name == "explain_query":
        sql = args["sql"]
        analyze = args.get("analyze", False)
        buffers = args.get("buffers", False)
        fmt = args.get("format", "text").upper()
        options = []
        if analyze:
            options.append("ANALYZE TRUE")
        if buffers and analyze:
            options.append("BUFFERS TRUE")
        options.append(f"FORMAT {fmt}")
        opts_str = ", ".join(options)
        explain_sql = f"EXPLAIN ({opts_str}) {sql}"
        rows = await _query(explain_sql, db)
        if fmt == "JSON":
            return json.dumps([dict(r) for r in rows], indent=2, default=str)
        return "\n".join(str(list(r.values())[0]) for r in rows)

    # ── list_databases ─────────────────────────────────────────────────────
    elif name == "list_databases":
        rows = await _query(
            "SELECT datname, pg_catalog.pg_encoding_to_char(encoding) AS encoding, "
            "datcollate, datctype, pg_size_pretty(pg_database_size(datname)) AS size "
            "FROM pg_catalog.pg_database ORDER BY datname;",
            db,
        )
        return _rows_to_text(rows)

    # ── list_schemas ───────────────────────────────────────────────────────
    elif name == "list_schemas":
        rows = await _query(
            "SELECT schema_name, schema_owner "
            "FROM information_schema.schemata ORDER BY schema_name;",
            db,
        )
        return _rows_to_text(rows)

    # ── list_tables ────────────────────────────────────────────────────────
    elif name == "list_tables":
        schema = args.get("schema", "public")
        rows = await _query(
            f"SELECT table_name, table_type "
            f"FROM information_schema.tables "
            f"WHERE table_schema = '{schema}' ORDER BY table_type, table_name;",
            db,
        )
        return _rows_to_text(rows)

    # ── describe_table ─────────────────────────────────────────────────────
    elif name == "describe_table":
        table = args["table"]
        schema = args.get("schema", "public")
        cols = await _query(
            textwrap.dedent(f"""
                SELECT column_name, data_type, character_maximum_length,
                       is_nullable, column_default
                FROM information_schema.columns
                WHERE table_schema = '{schema}' AND table_name = '{table}'
                ORDER BY ordinal_position;
            """),
            db,
        )
        idx = await _query(
            textwrap.dedent(f"""
                SELECT indexname, indexdef, indisprimary, indisunique
                FROM pg_indexes
                JOIN pg_index ON pg_indexes.indexname = (
                    SELECT relname FROM pg_class WHERE oid = pg_index.indexrelid
                )
                WHERE schemaname = '{schema}' AND tablename = '{table}';
            """),
            db,
        )
        con = await _query(
            textwrap.dedent(f"""
                SELECT constraint_name, constraint_type
                FROM information_schema.table_constraints
                WHERE table_schema = '{schema}' AND table_name = '{table}';
            """),
            db,
        )
        out = ["=== COLUMNS ===", _rows_to_text(cols),
               "\n=== INDEXES ===", _rows_to_text(idx),
               "\n=== CONSTRAINTS ===", _rows_to_text(con)]
        return "\n".join(out)

    # ── table_stats ────────────────────────────────────────────────────────
    elif name == "table_stats":
        schema = args.get("schema", "public")
        table_filter = f"AND relname = '{args['table']}'" if args.get("table") else ""
        rows = await _query(
            textwrap.dedent(f"""
                SELECT schemaname, relname AS table_name,
                       seq_scan, seq_tup_read, idx_scan, idx_tup_fetch,
                       n_tup_ins, n_tup_upd, n_tup_del,
                       n_live_tup, n_dead_tup,
                       last_autovacuum, last_autoanalyze
                FROM pg_stat_user_tables
                WHERE schemaname = '{schema}' {table_filter}
                ORDER BY n_dead_tup DESC;
            """),
            db,
        )
        return _rows_to_text(rows)

    # ── index_stats ────────────────────────────────────────────────────────
    elif name == "index_stats":
        schema = args.get("schema", "public")
        rows = await _query(
            textwrap.dedent(f"""
                SELECT relname AS table_name, indexrelname AS index_name,
                       idx_scan, idx_tup_read, idx_tup_fetch,
                       pg_size_pretty(pg_relation_size(indexrelid)) AS index_size
                FROM pg_stat_user_indexes
                WHERE schemaname = '{schema}'
                ORDER BY idx_scan ASC;
            """),
            db,
        )
        return _rows_to_text(rows)

    # ── active_locks ───────────────────────────────────────────────────────
    elif name == "active_locks":
        rows = await _query(
            textwrap.dedent("""
                SELECT
                    blocked.pid AS blocked_pid,
                    blocked.query AS blocked_query,
                    blocking.pid AS blocking_pid,
                    blocking.query AS blocking_query,
                    l.locktype, l.mode, l.granted,
                    now() - blocked.query_start AS blocked_duration
                FROM pg_stat_activity AS blocked
                JOIN pg_locks AS l ON l.pid = blocked.pid AND NOT l.granted
                JOIN pg_locks AS blocking_l
                    ON blocking_l.locktype = l.locktype
                    AND blocking_l.database IS NOT DISTINCT FROM l.database
                    AND blocking_l.relation IS NOT DISTINCT FROM l.relation
                    AND blocking_l.pid != l.pid
                    AND blocking_l.granted
                JOIN pg_stat_activity AS blocking ON blocking.pid = blocking_l.pid
                ORDER BY blocked_duration DESC NULLS LAST;
            """),
            db,
        )
        return _rows_to_text(rows)

    # ── long_running_queries ───────────────────────────────────────────────
    elif name == "long_running_queries":
        min_sec = args.get("min_seconds", 30)
        rows = await _query(
            textwrap.dedent(f"""
                SELECT pid, usename, datname, state,
                       now() - query_start AS duration,
                       left(query, 200) AS query_snippet
                FROM pg_stat_activity
                WHERE state != 'idle'
                  AND query_start IS NOT NULL
                  AND now() - query_start > interval '{min_sec} seconds'
                ORDER BY duration DESC;
            """),
            db,
        )
        return _rows_to_text(rows)

    # ── vacuum_table ───────────────────────────────────────────────────────
    elif name == "vacuum_table":
        table = args["table"]
        schema = args.get("schema", "public")
        opts = []
        if args.get("full"):
            opts.append("FULL")
        if args.get("analyze"):
            opts.append("ANALYZE")
        opts_str = f"({', '.join(opts)})" if opts else ""
        sql = f"VACUUM {opts_str} {schema}.{table};"
        tag = await _execute(sql, db)
        return f"OK: {tag}"

    # ── analyze_table ──────────────────────────────────────────────────────
    elif name == "analyze_table":
        schema = args.get("schema", "public")
        if args.get("table"):
            sql = f"ANALYZE {schema}.{args['table']};"
        else:
            sql = "ANALYZE;"
        tag = await _execute(sql, db)
        return f"OK: {tag}"

    # ── reindex_table ──────────────────────────────────────────────────────
    elif name == "reindex_table":
        table = args["table"]
        schema = args.get("schema", "public")
        tag = await _execute(f"REINDEX TABLE {schema}.{table};", db)
        return f"OK: {tag}"

    # ── replication_status ─────────────────────────────────────────────────
    elif name == "replication_status":
        repl = await _query(
            "SELECT client_addr, usename, application_name, state, "
            "sent_lsn, write_lsn, flush_lsn, replay_lsn, sync_state "
            "FROM pg_stat_replication ORDER BY client_addr;",
            db,
        )
        slots = await _query(
            "SELECT slot_name, plugin, slot_type, database, active, "
            "pg_size_pretty(pg_wal_lsn_diff(pg_current_wal_lsn(), confirmed_flush_lsn)) AS lag "
            "FROM pg_replication_slots ORDER BY slot_name;",
            db,
        )
        return "\n=== REPLICATION CONNECTIONS ===\n" + _rows_to_text(repl) + \
               "\n\n=== REPLICATION SLOTS ===\n" + _rows_to_text(slots)

    # ── list_roles ─────────────────────────────────────────────────────────
    elif name == "list_roles":
        rows = await _query(
            "SELECT rolname, rolsuper, rolinherit, rolcreaterole, rolcreatedb, "
            "rolcanlogin, rolreplication, rolconnlimit "
            "FROM pg_roles ORDER BY rolname;",
            db,
        )
        return _rows_to_text(rows)

    # ── create_role ────────────────────────────────────────────────────────
    elif name == "create_role":
        role = args["role_name"]
        attrs = []
        attrs.append("LOGIN" if args.get("login", True) else "NOLOGIN")
        if args.get("superuser"):
            attrs.append("SUPERUSER")
        if args.get("create_db"):
            attrs.append("CREATEDB")
        if args.get("password"):
            attrs.append(f"PASSWORD '{args['password']}'")
        sql = f"CREATE ROLE {role} {' '.join(attrs)};"
        tag = await _execute(sql, db)
        return f"OK: {tag}"

    # ── drop_role ──────────────────────────────────────────────────────────
    elif name == "drop_role":
        tag = await _execute(f"DROP ROLE IF EXISTS {args['role_name']};", db)
        return f"OK: {tag}"

    # ── create_database ────────────────────────────────────────────────────
    elif name == "create_database":
        sql = f"CREATE DATABASE {args['db_name']}"
        if args.get("owner"):
            sql += f" OWNER {args['owner']}"
        sql += ";"
        tag = await _execute(sql, "postgres")
        return f"OK: {tag}"

    # ── drop_database ──────────────────────────────────────────────────────
    elif name == "drop_database":
        name_ = args["db_name"]
        force = args.get("force", False)
        if force:
            await _execute(
                f"SELECT pg_terminate_backend(pid) FROM pg_stat_activity "
                f"WHERE datname = '{name_}' AND pid <> pg_backend_pid();",
                "postgres",
            )
        tag = await _execute(f"DROP DATABASE IF EXISTS {name_};", "postgres")
        return f"OK: {tag}"

    # ── list_extensions ────────────────────────────────────────────────────
    elif name == "list_extensions":
        installed = await _query(
            "SELECT extname, extversion, obj_description(oid, 'pg_extension') AS description "
            "FROM pg_extension ORDER BY extname;",
            db,
        )
        result = "=== INSTALLED EXTENSIONS ===\n" + _rows_to_text(installed)
        if args.get("available"):
            avail = await _query(
                "SELECT name, default_version, comment FROM pg_available_extensions "
                "ORDER BY name;",
                db,
            )
            result += "\n\n=== AVAILABLE EXTENSIONS ===\n" + _rows_to_text(avail)
        return result

    # ── table_bloat ────────────────────────────────────────────────────────
    elif name == "table_bloat":
        schema = args.get("schema", "public")
        min_dead = args.get("min_dead_tuples", 10000)
        rows = await _query(
            textwrap.dedent(f"""
                SELECT schemaname, relname AS table_name,
                       n_dead_tup AS dead_tuples,
                       n_live_tup AS live_tuples,
                       CASE WHEN n_live_tup > 0
                            THEN round(100.0 * n_dead_tup / (n_live_tup + n_dead_tup), 2)
                            ELSE 0
                       END AS dead_pct,
                       pg_size_pretty(pg_total_relation_size(relid)) AS total_size,
                       last_autovacuum, last_vacuum
                FROM pg_stat_user_tables
                WHERE schemaname = '{schema}'
                  AND n_dead_tup >= {min_dead}
                ORDER BY n_dead_tup DESC;
            """),
            db,
        )
        return _rows_to_text(rows)

    # ── missing_indexes ────────────────────────────────────────────────────
    elif name == "missing_indexes":
        min_scans = args.get("min_seq_scans", 100)
        rows = await _query(
            textwrap.dedent(f"""
                SELECT schemaname, relname AS table_name,
                       seq_scan, seq_tup_read,
                       idx_scan,
                       pg_size_pretty(pg_relation_size(relid)) AS table_size,
                       round(seq_scan::numeric / NULLIF(idx_scan, 0), 2) AS seq_to_idx_ratio
                FROM pg_stat_user_tables
                WHERE seq_scan > {min_scans}
                  AND (idx_scan IS NULL OR seq_scan > idx_scan * 2)
                ORDER BY seq_tup_read DESC;
            """),
            db,
        )
        return _rows_to_text(rows)

    # ── unused_indexes ─────────────────────────────────────────────────────
    elif name == "unused_indexes":
        schema = args.get("schema", "public")
        max_scans = args.get("max_idx_scans", 0)
        rows = await _query(
            textwrap.dedent(f"""
                SELECT s.schemaname, s.relname AS table_name, s.indexrelname AS index_name,
                       s.idx_scan, pg_size_pretty(pg_relation_size(s.indexrelid)) AS index_size,
                       i.indexdef
                FROM pg_stat_user_indexes AS s
                JOIN pg_indexes AS i
                    ON i.schemaname = s.schemaname
                    AND i.tablename = s.relname
                    AND i.indexname = s.indexrelname
                WHERE s.schemaname = '{schema}'
                  AND s.idx_scan <= {max_scans}
                  AND NOT i.indexdef LIKE '%UNIQUE%'
                ORDER BY pg_relation_size(s.indexrelid) DESC;
            """),
            db,
        )
        return _rows_to_text(rows)

    # ── database_sizes ─────────────────────────────────────────────────────
    elif name == "database_sizes":
        rows = await _query(
            "SELECT datname, pg_size_pretty(pg_database_size(datname)) AS size "
            "FROM pg_database ORDER BY pg_database_size(datname) DESC;",
            db,
        )
        return _rows_to_text(rows)

    # ── table_sizes ────────────────────────────────────────────────────────
    elif name == "table_sizes":
        schema = args.get("schema", "public")
        rows = await _query(
            textwrap.dedent(f"""
                SELECT table_schema, table_name,
                       pg_size_pretty(pg_total_relation_size(
                           quote_ident(table_schema) || '.' || quote_ident(table_name)
                       )) AS total_size,
                       pg_size_pretty(pg_relation_size(
                           quote_ident(table_schema) || '.' || quote_ident(table_name)
                       )) AS table_size,
                       pg_size_pretty(
                           pg_total_relation_size(
                               quote_ident(table_schema) || '.' || quote_ident(table_name))
                           - pg_relation_size(
                               quote_ident(table_schema) || '.' || quote_ident(table_name))
                       ) AS index_size
                FROM information_schema.tables
                WHERE table_schema = '{schema}'
                  AND table_type = 'BASE TABLE'
                ORDER BY pg_total_relation_size(
                    quote_ident(table_schema) || '.' || quote_ident(table_name)
                ) DESC;
            """),
            db,
        )
        return _rows_to_text(rows)

    # ── cache_hit_ratio ────────────────────────────────────────────────────
    elif name == "cache_hit_ratio":
        rows = await _query(
            textwrap.dedent("""
                SELECT
                    'tables' AS type,
                    sum(heap_blks_hit) AS hits,
                    sum(heap_blks_read) AS reads,
                    round(
                        100.0 * sum(heap_blks_hit)
                        / NULLIF(sum(heap_blks_hit) + sum(heap_blks_read), 0), 2
                    ) AS hit_pct
                FROM pg_statio_user_tables
                UNION ALL
                SELECT
                    'indexes',
                    sum(idx_blks_hit),
                    sum(idx_blks_read),
                    round(
                        100.0 * sum(idx_blks_hit)
                        / NULLIF(sum(idx_blks_hit) + sum(idx_blks_read), 0), 2
                    )
                FROM pg_statio_user_indexes;
            """),
            db,
        )
        return _rows_to_text(rows)

    # ── checkpoint_stats ───────────────────────────────────────────────────
    elif name == "checkpoint_stats":
        rows = await _query(
            "SELECT checkpoints_timed, checkpoints_req, "
            "checkpoint_write_time, checkpoint_sync_time, "
            "buffers_checkpoint, buffers_clean, buffers_backend, "
            "buffers_alloc, stats_reset "
            "FROM pg_stat_bgwriter;",
            db,
        )
        return _rows_to_text(rows)

    # ── connection_stats ───────────────────────────────────────────────────
    elif name == "connection_stats":
        rows = await _query(
            textwrap.dedent("""
                SELECT usename, datname, state, count(*) AS connections
                FROM pg_stat_activity
                WHERE pid <> pg_backend_pid()
                GROUP BY usename, datname, state
                ORDER BY connections DESC;
            """),
            db,
        )
        return _rows_to_text(rows)

    # ── kill_query ─────────────────────────────────────────────────────────
    elif name == "kill_query":
        pid = args["pid"]
        cancel_only = args.get("cancel_only", False)
        fn = "pg_cancel_backend" if cancel_only else "pg_terminate_backend"
        rows = await _query(f"SELECT {fn}({pid}) AS result;", db)
        return _rows_to_text(rows)

    else:
        return f"ERROR: Unknown tool '{name}'"


# ---------------------------------------------------------------------------
# FastAPI / SSE transport wiring
# ---------------------------------------------------------------------------

sse_transport = SseServerTransport("/messages/")


async def handle_sse(request: Request):
    async with sse_transport.connect_sse(
        request.scope,
        request.receive,
        request._send,  # noqa: SLF001
    ) as streams:
        await mcp_server.run(
            streams[0], streams[1], mcp_server.create_initialization_options()
        )


@asynccontextmanager
async def lifespan(app: FastAPI):  # noqa: ARG001
    log.info(
        "db-mcp starting — host=%s port=%s db=%s use_iam=%s",
        PGHOST, PGPORT, PGDATABASE, USE_IAM_AUTH,
    )
    yield
    log.info("db-mcp shutting down")


app = FastAPI(lifespan=lifespan, title="db-mcp", version="1.0.0")


# ---------------------------------------------------------------------------
# Bearer-token auth middleware
# ---------------------------------------------------------------------------
# All paths are protected except /health (used by liveness/readiness probes).
# Clients must send:  Authorization: Bearer <DB_MCP_TOKEN>
# Uses secrets.compare_digest to prevent timing-attack token comparison.

_PUBLIC_PATHS = {"/health"}


@app.middleware("http")
async def require_bearer_token(request: Request, call_next):
    if request.url.path in _PUBLIC_PATHS:
        return await call_next(request)

    auth_header = request.headers.get("Authorization", "")
    if not auth_header.startswith("Bearer "):
        log.warning("401 missing/malformed Authorization header — path=%s", request.url.path)
        return JSONResponse(
            {"detail": "Missing or malformed Authorization header. Expected: Bearer <token>"},
            status_code=401,
            headers={"WWW-Authenticate": "Bearer"},
        )

    provided = auth_header.removeprefix("Bearer ").strip()
    if not secrets.compare_digest(provided, _MCP_TOKEN):
        log.warning("403 invalid bearer token — path=%s", request.url.path)
        return JSONResponse(
            {"detail": "Invalid bearer token."},
            status_code=403,
        )

    return await call_next(request)


@app.get("/health")
async def health():
    """Liveness/readiness probe — verifies DB connectivity."""
    try:
        rows = await _query("SELECT 1 AS ok;")
        return JSONResponse({"status": "ok", "db": rows[0]["ok"]})
    except Exception as exc:  # noqa: BLE001
        return JSONResponse({"status": "error", "detail": str(exc)}, status_code=500)


@app.get("/sse")
async def sse_endpoint(request: Request):
    return await handle_sse(request)


@app.post("/messages/")
async def message_endpoint(request: Request):
    return await sse_transport.handle_post_message(
        request.scope, request.receive, request._send  # noqa: SLF001
    )


# ---------------------------------------------------------------------------
# Entry-point
# ---------------------------------------------------------------------------

if __name__ == "__main__":
    uvicorn.run(
        app,
        host=MCP_HOST,
        port=MCP_PORT,
        log_level="info",
        access_log=True,
    )
