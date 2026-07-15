FROM ubuntu:24.04

# Set environment variables for non-interactive install
ENV DEBIAN_FRONTEND=noninteractive

RUN <<EOF
apt-get update
# https://pythonspeed.com/articles/security-updates-in-docker/
apt-get upgrade -y
apt-get install -y wget curl ca-certificates gnupg2 lsb-release
echo "deb http://apt.postgresql.org/pub/repos/apt $(lsb_release -cs)-pgdg main" > /etc/apt/sources.list.d/pgdg.list
wget --quiet -O - https://www.postgresql.org/media/keys/ACCC4CF8.asc | apt-key add -
apt-get update
apt-get install -y postgresql-17
rm -rf /var/lib/apt/lists/*
EOF

# Expose default PostgreSQL port
EXPOSE 5432

# Document environment variables for remote PostgreSQL connection
ENV PGHOST=localhost
ENV PGPORT=5432
ENV PGUSER=postgres
ENV PGDATABASE=folio
ENV PGPASSWORD=somepassword
ENV DBS_2_DROP="kong keycloak"
ENV ROLES_2_DROP="keycloak keycloak_admin kong kong_admin"

# Copy entrypoint script
COPY entrypoint.sh /entrypoint.sh
RUN chmod +x /entrypoint.sh

CMD ["/entrypoint.sh"]
