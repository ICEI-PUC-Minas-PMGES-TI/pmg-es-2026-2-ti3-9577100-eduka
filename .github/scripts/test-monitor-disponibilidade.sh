#!/usr/bin/env bash
# Testa monitor-disponibilidade.sh sem rede e sem tocar no GitHub: curl e gh sao substituidos por
# stubs no PATH. Uso: bash .github/scripts/test-monitor-disponibilidade.sh
set -uo pipefail

AQUI="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SCRIPT="$AQUI/monitor-disponibilidade.sh"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
mkdir -p "$TMP/bin"

# curl: o codigo vem de CURL_HEALTH / CURL_FRONT (lista separada por espaco, uma por chamada; a
# ultima se repete). Registra cada chamada em curl.log.
cat >"$TMP/bin/curl" <<'STUB'
#!/usr/bin/env bash
url="${*: -1}"
case "$url" in *front*) tipo=FRONT; lista="${CURL_FRONT:-200}" ;; *) tipo=HEALTH; lista="${CURL_HEALTH:-200}" ;; esac
n=$(cat "$STATE/curl.$tipo" 2>/dev/null || echo 0)
n=$((n + 1)); echo "$n" >"$STATE/curl.$tipo"
read -r -a codigos <<<"$lista"
i=$((n - 1)); [ "$i" -ge "${#codigos[@]}" ] && i=$((${#codigos[@]} - 1))
echo "$tipo" >>"$STATE/curl.log"
printf '%s' "${codigos[$i]}"
STUB

# gh: registra os argumentos em gh.log e responde so o que o script consulta.
cat >"$TMP/bin/gh" <<'STUB'
#!/usr/bin/env bash
echo "$*" >>"$STATE/gh.log"
case "$1 $2" in
  "issue list") printf '%s' "${OPEN_ISSUE:-}" ;;
  "issue view") printf '%s' "${ISSUE_UPDATED_AT:-}" ;;
  "issue create") echo "https://github.com/x/y/issues/99" ;;
esac
STUB
chmod +x "$TMP/bin/curl" "$TMP/bin/gh"

falhas=0
caso=0

# rodar <nome> [VAR=valor ...]: roda o script num estado limpo; deixa STATUS e SAIDA.
rodar() {
  caso=$((caso + 1)); NOME="$1"; REPROVADO=0; shift
  export STATE="$TMP/estado.$caso"; mkdir -p "$STATE"
  SAIDA="$(env -i PATH="$TMP/bin:/usr/bin:/bin" STATE="$STATE" MONITOR_INTERVALO_SEGUNDOS=0 "$@" bash "$SCRIPT" 2>&1)"
  STATUS=$?
}
gh_log() { cat "$STATE/gh.log" 2>/dev/null || true; }
REPROVADO=0
falha() { echo "FAIL - $NOME: $1"; echo "$SAIDA" | sed 's/^/       /'; REPROVADO=1; }
fim() { if [ "$REPROVADO" -eq 0 ]; then echo "ok   - $NOME"; else falhas=$((falhas + 1)); fi; }
espera() { eval "$1" || falha "condicao falsa: $1"; }
espera_status() { [ "$STATUS" -eq "$1" ] || falha "status $STATUS, esperado $1"; }
espera_gh() { gh_log | grep -q -- "$1" || falha "gh deveria ter sido chamado com: $1"; }
nega_gh() { ! gh_log | grep -q -- "$1" || falha "gh nao deveria ter sido chamado com: $1"; }
espera_saida() { grep -q -- "$1" <<<"$SAIDA" || falha "saida deveria conter: $1"; }
chamadas() { local n; n="$(grep -c "^$1$" "$STATE/curl.log" 2>/dev/null)"; echo "${n:-0}"; }
agora_menos_min() { date -u -d "-$1 minutes" '+%Y-%m-%dT%H:%M:%SZ'; }

rodar "sem MONITOR_HEALTH_URL: avisa e termina com 0, sem consultar nada"
espera_status 0
espera_saida "::warning"
espera '[ ! -e "$STATE/curl.log" ]'
espera '[ ! -e "$STATE/gh.log" ]'
fim

rodar "no ar e sem issue aberta: nada a fazer" MONITOR_HEALTH_URL=https://api CURL_HEALTH="200"
espera_status 0
nega_gh "issue create" && nega_gh "issue close" && nega_gh "issue comment"
fim

rodar "no ar com issue aberta: fecha a issue com comentario" MONITOR_HEALTH_URL=https://api CURL_HEALTH="200" OPEN_ISSUE=7
espera_status 0
espera_gh "issue close 7 --comment" && nega_gh "issue create"
fim

rodar "falha transitoria (503, 503, 200): nao e queda" MONITOR_HEALTH_URL=https://api CURL_HEALTH="503 503 200"
espera_status 0
espera '[ "$(chamadas HEALTH)" -eq 3 ]'
nega_gh "issue create"
fim

rodar "queda (3x 503) sem issue: cria o label e abre a issue com o label" MONITOR_HEALTH_URL=https://api CURL_HEALTH="503"
espera_status 1
espera '[ "$(chamadas HEALTH)" -eq 3 ]'
espera_gh "label create indisponibilidade"
espera_gh "issue create --label indisponibilidade" && espera_gh "HTTP 503"
fim

rodar "queda sem resposta (000): descreve como sem resposta" MONITOR_HEALTH_URL=https://api CURL_HEALTH="000"
espera_status 1
espera_gh "sem resposta"
fim

rodar "queda com issue aberta e atividade recente: nao duplica nem comenta" MONITOR_HEALTH_URL=https://api CURL_HEALTH="503" \
  OPEN_ISSUE=7 ISSUE_UPDATED_AT="$(agora_menos_min 10)"
espera_status 1
nega_gh "issue create" && nega_gh "issue comment"
fim

rodar "queda com issue aberta parada ha 2h: comenta nela, sem criar outra" MONITOR_HEALTH_URL=https://api CURL_HEALTH="503" \
  OPEN_ISSUE=7 ISSUE_UPDATED_AT="$(agora_menos_min 120)"
espera_status 1
espera_gh "issue comment 7" && nega_gh "issue create"
fim

rodar "MONITOR_REAVISO_MINUTOS=0: comenta a cada execucao" MONITOR_HEALTH_URL=https://api CURL_HEALTH="503" \
  OPEN_ISSUE=7 ISSUE_UPDATED_AT="$(agora_menos_min 1)" MONITOR_REAVISO_MINUTOS=0
espera_status 1
espera_gh "issue comment 7"
fim

rodar "sem MONITOR_FRONTEND_URL: o frontend nao e consultado" MONITOR_HEALTH_URL=https://api CURL_HEALTH="200" CURL_FRONT="503"
espera_status 0
espera '[ "$(chamadas FRONT)" -eq 0 ]'
fim

rodar "so o frontend cai: abre issue citando o frontend" MONITOR_HEALTH_URL=https://api MONITOR_FRONTEND_URL=https://front \
  CURL_HEALTH="200" CURL_FRONT="502"
espera_status 1
espera_gh "issue create" && espera_gh "Frontend: HTTP 502" && nega_gh "Backend"
fim

rodar "as URLs nao aparecem no log" MONITOR_HEALTH_URL=https://segredo.exemplo/health CURL_HEALTH="503"
espera_status 1
espera '! grep -q "segredo.exemplo" <<<"$SAIDA"'
espera '! gh_log | grep -q "segredo.exemplo"'
fim

echo
if [ "$falhas" -eq 0 ]; then echo "Todos os $caso casos passaram."; else echo "$falhas de $caso casos falharam."; exit 1; fi
