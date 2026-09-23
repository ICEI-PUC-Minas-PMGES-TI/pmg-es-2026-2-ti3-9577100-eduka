#!/bin/sh
# Carrega mock.sql no banco de dev. Roda como servico "mock" do docker-compose.yml
# (nunca no de prod).
#
# O mock precisa das tabelas criadas pelo Flyway, que so roda quando o backend
# sobe. Por isso espera ate a migration mais recente de /migrations aparecer
# aplicada em flyway_schema_history, e so entao executa o script.
set -eu

export PGHOST=postgres PGUSER="$POSTGRES_USER" PGDATABASE="$POSTGRES_DB" PGPASSWORD="$POSTGRES_PASSWORD"

# V6__adiciona_plano_escola.sql -> 6 ; V1_2__x.sql -> 1.2 (formato gravado pelo Flyway)
ULTIMA=$(ls /migrations | sed -n 's/^V\([0-9_.]*\)__.*\.sql$/\1/p' | tr '_' '.' \
    | sort -t. -k1,1n -k2,2n -k3,3n | tail -n 1)

if [ -z "$ULTIMA" ]; then
    echo "mock: nenhuma migration encontrada em /migrations" >&2
    exit 1
fi

echo "mock: aguardando o Flyway aplicar a V$ULTIMA..."
TENTATIVAS=120 # 120 x 5s = 10 min (o primeiro build do backend e lento)
until [ "$(psql -tAc "SELECT 1 FROM flyway_schema_history WHERE version = '$ULTIMA' AND success" 2>/dev/null)" = "1" ]; do
    TENTATIVAS=$((TENTATIVAS - 1))
    if [ "$TENTATIVAS" -le 0 ]; then
        echo "mock: V$ULTIMA nao foi aplicada a tempo. Veja os logs do backend." >&2
        exit 1
    fi
    sleep 5
done

psql -v ON_ERROR_STOP=1 -f /mock/mock.sql
echo "mock: dados mockados carregados."
