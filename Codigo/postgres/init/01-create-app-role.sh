#!/bin/bash
set -euo pipefail

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" \
    -v app_user="$DB_APP_USER" -v app_password="$DB_APP_PASSWORD" -v dbname="$POSTGRES_DB" <<-'EOSQL'
    CREATE ROLE :"app_user" LOGIN PASSWORD :'app_password';
    ALTER DATABASE :"dbname" OWNER TO :"app_user";
    ALTER SCHEMA public OWNER TO :"app_user";
    GRANT ALL PRIVILEGES ON DATABASE :"dbname" TO :"app_user";
EOSQL
