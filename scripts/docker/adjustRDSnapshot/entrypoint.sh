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

echo "Cleanuping old info..."
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
		psql -h "$PGHOST" -p "$PGPORT" -U "$PGUSER" -d "$PGDATABASE" -c "DO \$\$ BEGIN IF EXISTS (SELECT FROM information_schema.tables WHERE table_schema = '${n}_mod_email' AND table_name = 'smtp_configuration') THEN DELETE FROM ${n}_mod_email.smtp_configuration; END IF; END \$\$;"
		psql -h "$PGHOST" -p "$PGPORT" -U "$PGUSER" -d "$PGDATABASE" -c "DO \$\$ BEGIN IF EXISTS (SELECT FROM information_schema.tables WHERE table_schema = '${n}_mod_email' AND table_name = 'settings') THEN DELETE FROM ${n}_mod_email.settings; END IF; END \$\$;"
		psql -h "$PGHOST" -p "$PGPORT" -U "$PGUSER" -d "$PGDATABASE" -c "DO \$\$ BEGIN IF EXISTS (SELECT FROM information_schema.tables WHERE table_schema = '${n}_mod_configuration' AND table_name = 'config_data') THEN DELETE FROM ${n}_mod_configuration.config_data WHERE jsonb ->> 'module' = 'SMTP_SERVER'; END IF; END \$\$;"
	fi
done

echo "Downloading and preparing authority cleanup script for ECS tenants..."
CLEANUP_SQL_URL="https://raw.githubusercontent.com/folio-org/mod-entities-links/refs/heads/master/src/main/resources/db/scripts/cleanup_authority_propagated_data.sql"
CLEANUP_SQL_FILE="/tmp/cleanup_authority_propagated_data.sql"

if curl -fsSL -o "$CLEANUP_SQL_FILE" "$CLEANUP_SQL_URL"; then
	if [[ -f "$CLEANUP_SQL_FILE" && -s "$CLEANUP_SQL_FILE" ]]; then
		echo "Successfully downloaded cleanup script ($(wc -l < "$CLEANUP_SQL_FILE") lines)"
		echo "Creating authority cleanup procedures in database..."
		if psql -h "$PGHOST" -p "$PGPORT" -U "$PGUSER" -d "$PGDATABASE" -f "$CLEANUP_SQL_FILE"; then
			echo "Procedures created successfully"

			for e in $ecs; do
				if [[ -n "$e" ]]; then
					echo "Running authority cleanup for consortia tenant: $e"
					psql -h "$PGHOST" -p "$PGPORT" -U "$PGUSER" -d "$PGDATABASE" <<-EOSQL
						CALL cleanup_member_tenant_propagated_data('$e');
					EOSQL
				fi
			done

			echo "Dropping cleanup procedures..."
			psql -h "$PGHOST" -p "$PGPORT" -U "$PGUSER" -d "$PGDATABASE" <<-EOSQL
				DROP PROCEDURE IF EXISTS cleanup_member_tenant_propagated_data(TEXT);
				DROP PROCEDURE IF EXISTS cleanup_all_member_tenants();
			EOSQL
		else
			echo "Error: Failed to create cleanup procedures in database"
		fi

		rm -f "$CLEANUP_SQL_FILE"
	else
		echo "Warning: Downloaded file is empty or does not exist"
	fi
else
	echo "Warning: Failed to download cleanup script from $CLEANUP_SQL_URL"
fi

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
		psql -h "$PGHOST" -p "$PGPORT" -U "$PGUSER" -d "$PGDATABASE" -c "DO \$\$ BEGIN IF EXISTS (SELECT FROM information_schema.tables WHERE table_schema = '${e}_mod_email' AND table_name = 'smtp_configuration') THEN DELETE FROM ${e}_mod_email.smtp_configuration; END IF; END \$\$;"
		psql -h "$PGHOST" -p "$PGPORT" -U "$PGUSER" -d "$PGDATABASE" -c "DO \$\$ BEGIN IF EXISTS (SELECT FROM information_schema.tables WHERE table_schema = '${e}_mod_email' AND table_name = 'settings') THEN DELETE FROM ${e}_mod_email.settings; END IF; END \$\$;"
		psql -h "$PGHOST" -p "$PGPORT" -U "$PGUSER" -d "$PGDATABASE" -c "DO \$\$ BEGIN IF EXISTS (SELECT FROM information_schema.tables WHERE table_schema = '${e}_mod_configuration' AND table_name = 'config_data') THEN DELETE FROM ${e}_mod_configuration.config_data WHERE jsonb ->> 'module' = 'SMTP_SERVER'; END IF; END \$\$;"
	fi
done

echo "RDS DB Preparation Complete"
