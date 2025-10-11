-- Optimized database creation with transaction batching
BEGIN;

-- Set performance optimizations for initialization
SET synchronous_commit = off;
SET fsync = off;
SET full_page_writes = off;
SET checkpoint_completion_target = 0.9;

%{ for db in dbs ~}
-- Create database ${db}
CREATE DATABASE ${db} WITH 
  ENCODING 'UTF8' 
  LC_COLLATE='en_US.utf8' 
  LC_CTYPE='en_US.utf8' 
  TEMPLATE=template0;
CREATE USER ${db} WITH PASSWORD '${pg_password}' CREATEDB;
ALTER DATABASE ${db} OWNER TO ${db};
%{ endfor ~}

COMMIT;

-- Configure each database separately (faster than in transaction)
%{ for db in dbs ~}
\c ${db}
SET search_path TO public;
REVOKE CREATE ON SCHEMA public FROM public;
GRANT ALL ON SCHEMA public TO ${db};
GRANT USAGE ON SCHEMA public TO ${db};
%{ endfor ~}
