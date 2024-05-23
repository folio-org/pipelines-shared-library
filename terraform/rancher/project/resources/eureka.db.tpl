            %{ for db in dbs ~}
            CREATE DATABASE ${db};
            CREATE USER ${db}_admin PASSWORD '${pg_password}';
            ALTER DATABASE ${db} OWNER TO ${db}_admin;
            ALTER DATABASE ${db} SET search_path TO public;
            REVOKE CREATE ON SCHEMA public FROM public;
            GRANT ALL ON SCHEMA public TO ${db}_admin;
            GRANT USAGE ON SCHEMA public TO ${db}_admin;
            %{ endfor ~}
