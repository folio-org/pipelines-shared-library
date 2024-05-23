CREATE DATABASE <% out << (db_name) %>;
CREATE USER <% out << (db_username) %> PASSWORD '<% out << (db_password) %>';
ALTER DATABASE <% out << (db_name) %> OWNER TO '<% out << (db_username) %>';
ALTER DATABASE <% out << (db_name) %> SET search_path TO public;
REVOKE CREATE ON SCHEMA public FROM public;
GRANT ALL ON SCHEMA public TO <% out << (db_username) %>;
GRANT USAGE ON SCHEMA public TO <% out << (db_username) %>;
