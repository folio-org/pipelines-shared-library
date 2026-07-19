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
import re
import secrets
import textwrap
import time
from contextlib import asynccontextmanager
from threading import Lock as ThreadLock
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
# Separate audit logger — wire this to a separate sink (CloudWatch, Splunk, etc.)
# if you need tamper-evident audit trails. At minimum it is searchable in pod logs.
_audit_log = logging.getLogger("db-mcp.audit")

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
# Input validation helpers
# ---------------------------------------------------------------------------

# PostgreSQL maximum identifier length is 63 bytes (NAMEDATALEN - 1).
_IDENT_RE = re.compile(r"^[a-zA-Z_][a-zA-Z0-9_$]*$")
_IDENT_MAX_LEN = 63


def _safe_ident(value: str, field: str = "identifier") -> str:
    """Validate *value* as a safe PostgreSQL identifier (schema/table/role/db name).

    Raises ValueError if the value contains characters that could cause SQL
    injection when interpolated into identifier positions (where $1 params
    cannot be used).  Returns the value unchanged when valid.

    >>> _safe_ident("public")
    'public'
    >>> _safe_ident("my_table")
    'my_table'
    >>> _safe_ident("'; DROP TABLE users; --")
    ValueError: Invalid identifier ...
    """
    if not isinstance(value, str):
        raise ValueError(f"{field} must be a string, got {type(value).__name__}")
    if not value:
        raise ValueError(f"{field} must not be empty")
    if len(value) > _IDENT_MAX_LEN:
        raise ValueError(
            f"{field} exceeds maximum PostgreSQL identifier length of "
            f"{_IDENT_MAX_LEN} characters (got {len(value)})"
        )
    if not _IDENT_RE.match(value):
        raise ValueError(
            f"Invalid {field}: {value!r}. "
            "Identifiers must match [a-zA-Z_][a-zA-Z0-9_$]* "
            "(no spaces, quotes, semicolons, or other special characters)."
        )
    return value


def _safe_int(
    value: Any,
    field: str,
    min_val: int | None = None,
    max_val: int | None = None,
) -> int:
    """Coerce *value* to int and validate against optional range bounds.

    Raises ValueError with a descriptive message on any failure.
    """
    try:
        v = int(value)
    except (TypeError, ValueError):
        raise ValueError(f"{field} must be an integer, got {value!r}")
    if min_val is not None and v < min_val:
        raise ValueError(f"{field} must be >= {min_val}, got {v}")
    if max_val is not None and v > max_val:
        raise ValueError(f"{field} must be <= {max_val}, got {v}")
    return v


def _audit(operation: str, params: dict[str, Any], result: str = "ok") -> None:
    """Emit a structured audit log entry for destructive / privileged operations.

    Format is intentionally machine-parseable so log aggregators (CloudWatch
    Insights, Splunk, etc.) can create alerts on unexpected destructive actions.
    """
    _audit_log.warning(
        "AUDIT op=%s params=%s result=%s",
        operation,
        json.dumps(params, default=str),
        result,
    )


# ---------------------------------------------------------------------------
# IAM auth token cache
# ---------------------------------------------------------------------------
# RDS IAM auth tokens are valid for 15 minutes.  We cache with a 12-minute TTL
# to avoid expiry mid-connection while keeping boto3 calls to a minimum.

_IAM_TOKEN_TTL_SECONDS = 720  # 12 min
_iam_token: dict[str, Any] = {"value": None, "expires_at": 0.0}
_iam_token_lock = ThreadLock()  # boto3 is synchronous; ThreadLock is appropriate


def _get_rds_iam_password() -> str:
    """Return a valid RDS IAM auth token, refreshing if expired or not yet fetched.

    Thread-safe: uses a threading.Lock so that concurrent FastAPI worker threads
    (if any) do not each trigger a boto3 call simultaneously.
    """
    with _iam_token_lock:
        if _iam_token["value"] and time.monotonic() < _iam_token["expires_at"]:
            return _iam_token["value"]  # type: ignore[return-value]
        client = boto3.client("rds", region_name=AWS_REGION)
        token: str = client.generate_db_auth_token(
            DBHostname=PGHOST,
            Port=PGPORT,
            DBUsername=PGUSER,
            Region=AWS_REGION,
        )
        _iam_token["value"] = token
        _iam_token["expires_at"] = time.monotonic() + _IAM_TOKEN_TTL_SECONDS
        log.info("RDS IAM auth token refreshed (TTL=%ds)", _IAM_TOKEN_TTL_SECONDS)
        return token


# ---------------------------------------------------------------------------
# Connection pool management
# ---------------------------------------------------------------------------
# Pools are created once at startup (or on first use of a non-default database)
# and reused for the lifetime of the process.  A per-database pool dict allows
# agents to query secondary databases without paying pool-creation overhead.

_pools: dict[str, asyncpg.Pool] = {}
# asyncio.Lock must be created inside a running event loop, so we initialise it
# in the lifespan context manager below rather than at module level.
_pool_creation_lock: asyncio.Lock


async def _build_pool(database: str) -> asyncpg.Pool:
    """Create a new asyncpg pool for *database*."""
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


async def _get_pool(database: str = PGDATABASE) -> asyncpg.Pool:
    """Return a live pool for *database*, creating one lazily if needed.

    Double-checked locking prevents redundant pool creation when multiple
    coroutines request the same new database concurrently (the check before the
    lock is a fast-path; the check inside the lock is the authoritative guard).
    """
    # Fast path — no lock needed for a database we've already pooled
    if database in _pools:
        return _pools[database]
    # Slow path — need to create a new pool; serialise creation
    async with _pool_creation_lock:
        if database not in _pools:  # re-check inside lock (another coroutine may have won)
            log.info("Creating new connection pool for database=%s", database)
            _pools[database] = await _build_pool(database)
        return _pools[database]


# ---------------------------------------------------------------------------
# Database helpers
# ---------------------------------------------------------------------------

async def _query(sql: str, *params: Any, database: str = PGDATABASE) -> list[dict]:
    """Execute *sql* with positional *params* and return rows as a list of dicts.

    Use $1, $2, … placeholders in *sql* for value-position parameters.
    Identifier-position values (table/schema names) must be pre-validated with
    _safe_ident() before interpolation.
    """
    pool = await _get_pool(database)
    async with pool.acquire() as conn:
        rows = await conn.fetch(sql, *params)
        return [dict(r) for r in rows]


async def _execute(sql: str, *params: Any, database: str = PGDATABASE) -> str:
    """Execute a DML/DDL statement with positional *params* and return the command tag."""
    pool = await _get_pool(database)
    async with pool.acquire() as conn:
        result = await conn.execute(sql, *params)
        return str(result)


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
            rows = await _query(sql, database=db)
            return _rows_to_text(rows)
        tag = await _execute(sql, database=db)
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
        rows = await _query(explain_sql, database=db)
        if fmt == "JSON":
            return json.dumps([dict(r) for r in rows], indent=2, default=str)
        return "\n".join(str(list(r.values())[0]) for r in rows)

    # ── list_databases ─────────────────────────────────────────────────────
    elif name == "list_databases":
        rows = await _query(
            "SELECT datname, pg_catalog.pg_encoding_to_char(encoding) AS encoding, "
            "datcollate, datctype, pg_size_pretty(pg_database_size(datname)) AS size "
            "FROM pg_catalog.pg_database ORDER BY datname;",
            database=db,
        )
        return _rows_to_text(rows)

    # ── list_schemas ───────────────────────────────────────────────────────
    elif name == "list_schemas":
        rows = await _query(
            "SELECT schema_name, schema_owner "
            "FROM information_schema.schemata ORDER BY schema_name;",
            database=db,
        )
        return _rows_to_text(rows)

    # ── list_tables ────────────────────────────────────────────────────────
    elif name == "list_tables":
        # schema is an identifier here but the WHERE clause filters a text
        # column value, so we use a $1 parameter for the actual comparison.
        # _safe_ident() is still called to ensure no unintended characters are
        # passed in (belt-and-suspenders: the $1 param is the true guard).
        schema = _safe_ident(args.get("schema", "public"), "schema")
        rows = await _query(
            "SELECT table_name, table_type "
            "FROM information_schema.tables "
            "WHERE table_schema = $1 ORDER BY table_type, table_name;",
            schema,
            database=db,
        )
        return _rows_to_text(rows)

    # ── describe_table ─────────────────────────────────────────────────────
    elif name == "describe_table":
        table = _safe_ident(args["table"], "table")
        schema = _safe_ident(args.get("schema", "public"), "schema")
        # All three queries use $1/$2 parameters for the WHERE clause values;
        # the pg_indexes JOIN uses the validated identifiers only in the SELECT
        # target, not user-controlled WHERE clauses.
        cols = await _query(
            textwrap.dedent("""
                SELECT column_name, data_type, character_maximum_length,
                       is_nullable, column_default
                FROM information_schema.columns
                WHERE table_schema = $1 AND table_name = $2
                ORDER BY ordinal_position;
            """),
            schema, table,
            database=db,
        )
        idx = await _query(
            textwrap.dedent("""
                SELECT indexname, indexdef,
                       indisprimary, indisunique
                FROM pg_indexes
                JOIN pg_index ON pg_indexes.indexname = (
                    SELECT relname FROM pg_class WHERE oid = pg_index.indexrelid
                )
                WHERE schemaname = $1 AND tablename = $2;
            """),
            schema, table,
            database=db,
        )
        con = await _query(
            textwrap.dedent("""
                SELECT constraint_name, constraint_type
                FROM information_schema.table_constraints
                WHERE table_schema = $1 AND table_name = $2;
            """),
            schema, table,
            database=db,
        )
        out = ["=== COLUMNS ===", _rows_to_text(cols),
               "\n=== INDEXES ===", _rows_to_text(idx),
               "\n=== CONSTRAINTS ===", _rows_to_text(con)]
        return "\n".join(out)

    # ── table_stats ────────────────────────────────────────────────────────
    elif name == "table_stats":
        schema = _safe_ident(args.get("schema", "public"), "schema")
        # table name is optional; validate only when provided
        table_raw: str | None = args.get("table")
        if table_raw is not None:
            table_val = _safe_ident(table_raw, "table")
            rows = await _query(
                textwrap.dedent("""
                    SELECT schemaname, relname AS table_name,
                           seq_scan, seq_tup_read, idx_scan, idx_tup_fetch,
                           n_tup_ins, n_tup_upd, n_tup_del,
                           n_live_tup, n_dead_tup,
                           last_autovacuum, last_autoanalyze
                    FROM pg_stat_user_tables
                    WHERE schemaname = $1 AND relname = $2
                    ORDER BY n_dead_tup DESC;
                """),
                schema, table_val,
                database=db,
            )
        else:
            rows = await _query(
                textwrap.dedent("""
                    SELECT schemaname, relname AS table_name,
                           seq_scan, seq_tup_read, idx_scan, idx_tup_fetch,
                           n_tup_ins, n_tup_upd, n_tup_del,
                           n_live_tup, n_dead_tup,
                           last_autovacuum, last_autoanalyze
                    FROM pg_stat_user_tables
                    WHERE schemaname = $1
                    ORDER BY n_dead_tup DESC;
                """),
                schema,
                database=db,
            )
        return _rows_to_text(rows)

    # ── index_stats ────────────────────────────────────────────────────────
    elif name == "index_stats":
        schema = _safe_ident(args.get("schema", "public"), "schema")
        rows = await _query(
            textwrap.dedent("""
                SELECT relname AS table_name, indexrelname AS index_name,
                       idx_scan, idx_tup_read, idx_tup_fetch,
                       pg_size_pretty(pg_relation_size(indexrelid)) AS index_size
                FROM pg_stat_user_indexes
                WHERE schemaname = $1
                ORDER BY idx_scan ASC;
            """),
            schema,
            database=db,
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
            database=db,
        )
        return _rows_to_text(rows)

    # ── long_running_queries ───────────────────────────────────────────────
    elif name == "long_running_queries":
        min_sec = _safe_int(args.get("min_seconds", 30), "min_seconds", min_val=0)
        rows = await _query(
            textwrap.dedent("""
                SELECT pid, usename, datname, state,
                       now() - query_start AS duration,
                       left(query, 200) AS query_snippet
                FROM pg_stat_activity
                WHERE state != 'idle'
                  AND query_start IS NOT NULL
                  AND extract(epoch from (now() - query_start)) > $1
                ORDER BY duration DESC;
            """),
            min_sec,
            database=db,
        )
        return _rows_to_text(rows)

    # ── vacuum_table ───────────────────────────────────────────────────────
    elif name == "vacuum_table":
        table = _safe_ident(args["table"], "table")
        schema = _safe_ident(args.get("schema", "public"), "schema")
        full = bool(args.get("full", False))
        analyze = bool(args.get("analyze", False))
        opts = []
        if full:
            opts.append("FULL")
        if analyze:
            opts.append("ANALYZE")
        opts_str = f"({', '.join(opts)})" if opts else ""
        # VACUUM/ANALYZE/REINDEX do not support $1 params for table names —
        # identifier validation via _safe_ident() is the correct mitigation.
        sql = f"VACUUM {opts_str} {schema}.{table};"
        if full:
            _audit("vacuum_full", {"schema": schema, "table": table, "database": db})
        tag = await _execute(sql, database=db)
        return f"OK: {tag}"

    # ── analyze_table ──────────────────────────────────────────────────────
    elif name == "analyze_table":
        schema = _safe_ident(args.get("schema", "public"), "schema")
        if args.get("table"):
            table = _safe_ident(args["table"], "table")
            sql = f"ANALYZE {schema}.{table};"
        else:
            sql = "ANALYZE;"
        tag = await _execute(sql, database=db)
        return f"OK: {tag}"

    # ── reindex_table ──────────────────────────────────────────────────────
    elif name == "reindex_table":
        table = _safe_ident(args["table"], "table")
        schema = _safe_ident(args.get("schema", "public"), "schema")
        tag = await _execute(f"REINDEX TABLE {schema}.{table};", database=db)
        return f"OK: {tag}"

    # ── replication_status ─────────────────────────────────────────────────
    elif name == "replication_status":
        repl = await _query(
            "SELECT client_addr, usename, application_name, state, "
            "sent_lsn, write_lsn, flush_lsn, replay_lsn, sync_state "
            "FROM pg_stat_replication ORDER BY client_addr;",
            database=db,
        )
        slots = await _query(
            "SELECT slot_name, plugin, slot_type, database, active, "
            "pg_size_pretty(pg_wal_lsn_diff(pg_current_wal_lsn(), confirmed_flush_lsn)) AS lag "
            "FROM pg_replication_slots ORDER BY slot_name;",
            database=db,
        )
        return "\n=== REPLICATION CONNECTIONS ===\n" + _rows_to_text(repl) + \
               "\n\n=== REPLICATION SLOTS ===\n" + _rows_to_text(slots)

    # ── list_roles ─────────────────────────────────────────────────────────
    elif name == "list_roles":
        rows = await _query(
            "SELECT rolname, rolsuper, rolinherit, rolcreaterole, rolcreatedb, "
            "rolcanlogin, rolreplication, rolconnlimit "
            "FROM pg_roles ORDER BY rolname;",
            database=db,
        )
        return _rows_to_text(rows)

    # ── create_role ────────────────────────────────────────────────────────
    elif name == "create_role":
        role = _safe_ident(args["role_name"], "role_name")
        attrs = []
        attrs.append("LOGIN" if args.get("login", True) else "NOLOGIN")
        if args.get("superuser"):
            attrs.append("SUPERUSER")
        if args.get("create_db"):
            attrs.append("CREATEDB")
        # Two-step approach: CREATE ROLE sets structural attributes (no password
        # in the DDL string), then ALTER ROLE uses a $1 parameter for the
        # password so it never appears in the query string or server logs.
        create_sql = f"CREATE ROLE {role} {' '.join(attrs)};"
        tag = await _execute(create_sql, database=db)
        if args.get("password"):
            await _execute(
                f"ALTER ROLE {role} PASSWORD $1;",
                args["password"],
                database=db,
            )
        _audit("create_role", {"role": role, "attrs": attrs, "has_password": bool(args.get("password"))})
        return f"OK: {tag}"

    # ── drop_role ──────────────────────────────────────────────────────────
    elif name == "drop_role":
        role = _safe_ident(args["role_name"], "role_name")
        _audit("drop_role", {"role": role})
        tag = await _execute(f"DROP ROLE IF EXISTS {role};", database=db)
        return f"OK: {tag}"

    # ── create_database ────────────────────────────────────────────────────
    elif name == "create_database":
        db_name = _safe_ident(args["db_name"], "db_name")
        sql = f"CREATE DATABASE {db_name}"
        owner_params: dict[str, Any] = {"db_name": db_name}
        if args.get("owner"):
            owner = _safe_ident(args["owner"], "owner")
            sql += f" OWNER {owner}"
            owner_params["owner"] = owner
        sql += ";"
        _audit("create_database", owner_params)
        tag = await _execute(sql, database="postgres")
        return f"OK: {tag}"

    # ── drop_database ──────────────────────────────────────────────────────
    elif name == "drop_database":
        name_ = _safe_ident(args["db_name"], "db_name")
        force = args.get("force", False)
        _audit("drop_database", {"db_name": name_, "force": force})
        if force:
            # datname comparison uses a $1 parameter (value position → safe)
            await _execute(
                "SELECT pg_terminate_backend(pid) FROM pg_stat_activity "
                "WHERE datname = $1 AND pid <> pg_backend_pid();",
                name_,
                database="postgres",
            )
        tag = await _execute(f"DROP DATABASE IF EXISTS {name_};", database="postgres")
        return f"OK: {tag}"

    # ── list_extensions ────────────────────────────────────────────────────
    elif name == "list_extensions":
        installed = await _query(
            "SELECT extname, extversion, obj_description(oid, 'pg_extension') AS description "
            "FROM pg_extension ORDER BY extname;",
            database=db,
        )
        result = "=== INSTALLED EXTENSIONS ===\n" + _rows_to_text(installed)
        if args.get("available"):
            avail = await _query(
                "SELECT name, default_version, comment FROM pg_available_extensions "
                "ORDER BY name;",
                database=db,
            )
            result += "\n\n=== AVAILABLE EXTENSIONS ===\n" + _rows_to_text(avail)
        return result

    # ── table_bloat ────────────────────────────────────────────────────────
    elif name == "table_bloat":
        schema = _safe_ident(args.get("schema", "public"), "schema")
        min_dead = _safe_int(args.get("min_dead_tuples", 10000), "min_dead_tuples", min_val=0)
        rows = await _query(
            textwrap.dedent("""
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
                WHERE schemaname = $1
                  AND n_dead_tup >= $2
                ORDER BY n_dead_tup DESC;
            """),
            schema, min_dead,
            database=db,
        )
        return _rows_to_text(rows)

    # ── missing_indexes ────────────────────────────────────────────────────
    elif name == "missing_indexes":
        min_scans = _safe_int(args.get("min_seq_scans", 100), "min_seq_scans", min_val=0)
        rows = await _query(
            textwrap.dedent("""
                SELECT schemaname, relname AS table_name,
                       seq_scan, seq_tup_read,
                       idx_scan,
                       pg_size_pretty(pg_relation_size(relid)) AS table_size,
                       round(seq_scan::numeric / NULLIF(idx_scan, 0), 2) AS seq_to_idx_ratio
                FROM pg_stat_user_tables
                WHERE seq_scan > $1
                  AND (idx_scan IS NULL OR seq_scan > idx_scan * 2)
                ORDER BY seq_tup_read DESC;
            """),
            min_scans,
            database=db,
        )
        return _rows_to_text(rows)

    # ── unused_indexes ─────────────────────────────────────────────────────
    elif name == "unused_indexes":
        schema = _safe_ident(args.get("schema", "public"), "schema")
        max_scans = _safe_int(args.get("max_idx_scans", 0), "max_idx_scans", min_val=0)
        rows = await _query(
            textwrap.dedent("""
                SELECT s.schemaname, s.relname AS table_name, s.indexrelname AS index_name,
                       s.idx_scan, pg_size_pretty(pg_relation_size(s.indexrelid)) AS index_size,
                       i.indexdef
                FROM pg_stat_user_indexes AS s
                JOIN pg_indexes AS i
                    ON i.schemaname = s.schemaname
                    AND i.tablename = s.relname
                    AND i.indexname = s.indexrelname
                WHERE s.schemaname = $1
                  AND s.idx_scan <= $2
                  AND NOT i.indexdef LIKE '%UNIQUE%'
                ORDER BY pg_relation_size(s.indexrelid) DESC;
            """),
            schema, max_scans,
            database=db,
        )
        return _rows_to_text(rows)

    # ── database_sizes ─────────────────────────────────────────────────────
    elif name == "database_sizes":
        rows = await _query(
            "SELECT datname, pg_size_pretty(pg_database_size(datname)) AS size "
            "FROM pg_database ORDER BY pg_database_size(datname) DESC;",
            database=db,
        )
        return _rows_to_text(rows)

    # ── table_sizes ────────────────────────────────────────────────────────
    elif name == "table_sizes":
        schema = _safe_ident(args.get("schema", "public"), "schema")
        rows = await _query(
            textwrap.dedent("""
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
                WHERE table_schema = $1
                  AND table_type = 'BASE TABLE'
                ORDER BY pg_total_relation_size(
                    quote_ident(table_schema) || '.' || quote_ident(table_name)
                ) DESC;
            """),
            schema,
            database=db,
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
            database=db,
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
            database=db,
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
            database=db,
        )
        return _rows_to_text(rows)

    # ── kill_query ─────────────────────────────────────────────────────────
    elif name == "kill_query":
        # PostgreSQL PIDs are OS PIDs; valid range on Linux is 1–4194304.
        pid = _safe_int(args["pid"], "pid", min_val=1, max_val=4_194_304)
        cancel_only = args.get("cancel_only", False)
        fn = "pg_cancel_backend" if cancel_only else "pg_terminate_backend"
        _audit("kill_query", {"pid": pid, "cancel_only": cancel_only})
        rows = await _query(f"SELECT {fn}($1) AS result;", pid, database=db)
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
    global _pool_creation_lock
    # asyncio.Lock must be created inside a running event loop
    _pool_creation_lock = asyncio.Lock()
    # Pre-warm the default database pool so the first request is not slow
    _pools[PGDATABASE] = await _build_pool(PGDATABASE)
    log.info(
        "db-mcp started — host=%s port=%s db=%s use_iam=%s",
        PGHOST, PGPORT, PGDATABASE, USE_IAM_AUTH,
    )
    yield
    # Gracefully close all pools on shutdown
    for pool_db, pool in list(_pools.items()):
        try:
            await pool.close()
            log.info("Connection pool closed for database=%s", pool_db)
        except Exception:  # noqa: BLE001
            log.warning("Error closing pool for database=%s", pool_db, exc_info=True)
    _pools.clear()
    log.info("db-mcp shut down — all pools closed")


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
