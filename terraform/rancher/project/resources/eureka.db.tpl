%{ for db in dbs ~}
CREATE DATABASE ${db};
CREATE USER ${db} PASSWORD '${pg_password}';
ALTER DATABASE ${db} OWNER TO ${db};
ALTER DATABASE ${db} SET search_path TO public;
REVOKE CREATE ON SCHEMA public FROM public;
GRANT ALL ON SCHEMA public TO ${db};
GRANT USAGE ON SCHEMA public TO ${db};
%{ endfor ~}
