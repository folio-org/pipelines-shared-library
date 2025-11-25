#!/bin/bash
set -e

# Connection parameters from environment variables
PGHOST=${PGHOST:-localhost}
PGPORT=${PGPORT:-5432}
PGUSER=${PGUSER:-postgres}
PGDATABASE=${PGDATABASE:-folio}
PGPASSWORD=${PGPASSWORD:-}
DBS_2_DROP=${DBS_2_DROP:-kong keycloak}
ROLES_2_DROP=${ROLES_2_DROP:-keycloak keycloak_admin kong kong_admin}

non_ecs="fs09000000 fs09000002 fs09000003"

ecs="cs00000int cs00000int_0001 cs00000int_0002 cs00000int_0003 cs00000int_0004 cs00000int_0005 cs00000int_0006 cs00000int_0007 cs00000int_0008 cs00000int_0009 cs00000int_0010 cs00000int_0011"

export PGPASSWORD

for db in $DBS_2_DROP; do
	if [[ -n "$db" ]]; then
		echo "Altering owner of database: $db to $PGUSER"
        psql -h "$PGHOST" -p "$PGPORT" -U "$PGUSER" -d "$PGDATABASE" -c "ALTER DATABASE \"$db\" OWNER TO \"$PGUSER\";"
		echo "Dropping database: $db"
		psql -h "$PGHOST" -p "$PGPORT" -U "$PGUSER" -d "$PGDATABASE" -c "DROP DATABASE IF EXISTS \"$db\";"
	fi
done

for role in $ROLES_2_DROP; do
	if [[ -n "$role" ]]; then
		echo "Dropping role: $role"
		psql -h "$PGHOST" -p "$PGPORT" -U "$PGUSER" -d "$PGDATABASE" -c "DROP ROLE IF EXISTS \"$role\";"
	fi
done

echo "Cleaning old info..."
psql -h "$PGHOST" -p "$PGPORT" -U "$PGUSER" -d "$PGDATABASE" -c "TRUNCATE TABLE public.tenant CASCADE;"
psql -h "$PGHOST" -p "$PGPORT" -U "$PGUSER" -d "$PGDATABASE" -c "DELETE FROM public.module;"
psql -h "$PGHOST" -p "$PGPORT" -U "$PGUSER" -d "$PGDATABASE" -c "DELETE FROM public.entitlement;"
psql -h "$PGHOST" -p "$PGPORT" -U "$PGUSER" -d "$PGDATABASE" -c "DELETE FROM public.entitlement_module;"
psql -h "$PGHOST" -p "$PGPORT" -U "$PGUSER" -d "$PGDATABASE" -c "DELETE FROM public.application;"
psql -h "$PGHOST" -p "$PGPORT" -U "$PGUSER" -d "$PGDATABASE" -c "DELETE FROM public.application_flow;"
psql -h "$PGHOST" -p "$PGPORT" -U "$PGUSER" -d "$PGDATABASE" -c "DELETE FROM fs09000000_mod_agreements.subscription_agreement_tag;"

for n in $non_ecs; do
	if [[ -n "$n" ]]; then
		echo "Dropping schemas for tenant: $n"
		psql -h "$PGHOST" -p "$PGPORT" -U "$PGUSER" -d "$PGDATABASE" -c "DROP SCHEMA IF EXISTS ${n}_mod_roles_keycloak CASCADE;"
		psql -h "$PGHOST" -p "$PGPORT" -U "$PGUSER" -d "$PGDATABASE" -c "DROP SCHEMA IF EXISTS ${n}_mod_users_keycloak CASCADE;"
		psql -h "$PGHOST" -p "$PGPORT" -U "$PGUSER" -d "$PGDATABASE" -c "DROP ROLE IF EXISTS ${n}_mod_roles_keycloak;"
		psql -h "$PGHOST" -p "$PGPORT" -U "$PGUSER" -d "$PGDATABASE" -c "DROP ROLE IF EXISTS ${n}_mod_users_keycloak;"
		psql -h "$PGHOST" -p "$PGPORT" -U "$PGUSER" -d "$PGDATABASE" -c "DELETE FROM ${n}_mod_users.users;"
		psql -h "$PGHOST" -p "$PGPORT" -U "$PGUSER" -d "$PGDATABASE" -c "DELETE FROM ${n}_mod_email.smtp_configuration;"
		psql -h "$PGHOST" -p "$PGPORT" -U "$PGUSER" -d "$PGDATABASE" -c "DELETE FROM ${n}_mod_email.settings;"
		psql -h "$PGHOST" -p "$PGPORT" -U "$PGUSER" -d "$PGDATABASE" -c "DELETE FROM ${n}_mod_configuration.config_data where jsonb ->> 'module' = 'SMTP_SERVER';"
	fi
done

for e in $ecs; do
	if [[ -n "$e" ]]; then
		echo "Dropping schemas for consortia tenant: $e"
		psql -h "$PGHOST" -p "$PGPORT" -U "$PGUSER" -d "$PGDATABASE" -c "DROP SCHEMA IF EXISTS ${e}_mod_roles_keycloak CASCADE;"
		psql -h "$PGHOST" -p "$PGPORT" -U "$PGUSER" -d "$PGDATABASE" -c "DROP SCHEMA IF EXISTS ${e}_mod_users_keycloak CASCADE;"
		psql -h "$PGHOST" -p "$PGPORT" -U "$PGUSER" -d "$PGDATABASE" -c "DROP SCHEMA IF EXISTS ${e}_mod_consortia CASCADE;"
		psql -h "$PGHOST" -p "$PGPORT" -U "$PGUSER" -d "$PGDATABASE" -c "DROP SCHEMA IF EXISTS ${e}_mod_consortia_keycloak CASCADE;"
		psql -h "$PGHOST" -p "$PGPORT" -U "$PGUSER" -d "$PGDATABASE" -c "DROP ROLE IF EXISTS ${e}_mod_roles_keycloak;"
		psql -h "$PGHOST" -p "$PGPORT" -U "$PGUSER" -d "$PGDATABASE" -c "DROP ROLE IF EXISTS ${e}_mod_users_keycloak;"
		psql -h "$PGHOST" -p "$PGPORT" -U "$PGUSER" -d "$PGDATABASE" -c "DROP ROLE IF EXISTS ${e}_mod_consortia;"
		psql -h "$PGHOST" -p "$PGPORT" -U "$PGUSER" -d "$PGDATABASE" -c "DROP ROLE IF EXISTS ${e}_mod_consortia_keycloak;"
		echo "Cleaning up data for consortia tenant: $e"
		psql -h "$PGHOST" -p "$PGPORT" -U "$PGUSER" -d "$PGDATABASE" -c "DELETE FROM ${e}_mod_users.users;"
		psql -h "$PGHOST" -p "$PGPORT" -U "$PGUSER" -d "$PGDATABASE" -c "DELETE FROM ${e}_mod_users.user_tenant;"
		psql -h "$PGHOST" -p "$PGPORT" -U "$PGUSER" -d "$PGDATABASE" -c "DELETE FROM ${e}_mod_email.smtp_configuration;"
		psql -h "$PGHOST" -p "$PGPORT" -U "$PGUSER" -d "$PGDATABASE" -c "DELETE FROM ${e}_mod_email.settings;"
		psql -h "$PGHOST" -p "$PGPORT" -U "$PGUSER" -d "$PGDATABASE" -c "DELETE FROM ${e}_mod_configuration.config_data where jsonb ->> 'module' = 'SMTP_SERVER';"
	fi
done

echo "RDS DB Preparation Complete"
