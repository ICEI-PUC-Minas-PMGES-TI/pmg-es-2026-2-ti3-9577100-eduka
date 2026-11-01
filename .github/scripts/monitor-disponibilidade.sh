#!/usr/bin/env bash
# Monitor de disponibilidade do Eduka (chamado por .github/workflows/monitor-disponibilidade.yml).
#
# Consulta o health do backend (e, se configurado, o frontend). So declara queda depois de
# MONITOR_TENTATIVAS falhas seguidas, com MONITOR_INTERVALO_SEGUNDOS entre elas. A queda abre uma
# issue com o label "indisponibilidade" (ou comenta na que ja esta aberta); a volta comenta e fecha.
#
# Entradas (variaveis de ambiente):
#   MONITOR_HEALTH_URL          URL do GET /health (obrigatoria; sem ela o script avisa e sai com 0)
#   MONITOR_FRONTEND_URL        URL do frontend (opcional)
#   GH_TOKEN, GH_REPO           usados pelo gh (no Actions: github.token e github.repository)
#   MONITOR_TENTATIVAS          padrao 3
#   MONITOR_INTERVALO_SEGUNDOS  padrao 20
#   MONITOR_TIMEOUT_SEGUNDOS    padrao 10, por tentativa
#   MONITOR_REAVISO_MINUTOS     padrao 60: com a issue aberta, so comenta de novo apos esse tempo
#                               sem atividade nela (0 comenta a cada execucao)
#
# Sai com 1 so no run que ABRE a issue de queda (o run fica vermelho, segundo aviso alem da issue) e com 0
# nos demais casos: tudo no ar, monitor nao configurado ou queda cuja issue ja estava aberta (comentada
# ou nao). Assim o GitHub nao manda um e-mail de run falho a cada 5 minutos, o que anularia o re-aviso
# de 1 por hora. Nunca imprime as URLs: o log do Actions pode ser publico.
set -euo pipefail

LABEL="indisponibilidade"
TENTATIVAS="${MONITOR_TENTATIVAS:-3}"
INTERVALO="${MONITOR_INTERVALO_SEGUNDOS:-20}"
TIMEOUT="${MONITOR_TIMEOUT_SEGUNDOS:-10}"
REAVISO_MIN="${MONITOR_REAVISO_MINUTOS:-60}"

if [ -z "${MONITOR_HEALTH_URL:-}" ]; then
  echo "::warning title=Monitor de disponibilidade desativado::A variavel MONITOR_HEALTH_URL nao esta configurada (Settings > Secrets and variables > Actions > Variables). Nada foi verificado."
  exit 0
fi

ULTIMO_CODIGO="000"

# Retorna 0 se a URL responde 2xx em ate TENTATIVAS tentativas; senao 1, com o ultimo codigo em ULTIMO_CODIGO.
verificar() {
  local nome="$1" url="$2" tentativa=1 codigo
  while :; do
    codigo="$(curl -sS -o /dev/null -w '%{http_code}' --max-time "$TIMEOUT" "$url" 2>/dev/null)" || codigo="000"
    case "$codigo" in
      2??)
        echo "$nome: OK (HTTP $codigo) na tentativa $tentativa/$TENTATIVAS"
        return 0
        ;;
    esac
    ULTIMO_CODIGO="$codigo"
    echo "$nome: tentativa $tentativa/$TENTATIVAS falhou (HTTP $codigo)"
    if [ "$tentativa" -ge "$TENTATIVAS" ]; then
      return 1
    fi
    tentativa=$((tentativa + 1))
    sleep "$INTERVALO"
  done
}

descrever() {
  if [ "$2" = "000" ]; then
    echo "- $1: sem resposta (conexao recusada, DNS ou tempo esgotado)"
  else
    echo "- $1: HTTP $2"
  fi
}

falhas=()
verificar "Backend /health" "$MONITOR_HEALTH_URL" || falhas+=("$(descrever "Backend (GET /health)" "$ULTIMO_CODIGO")")
if [ -n "${MONITOR_FRONTEND_URL:-}" ]; then
  verificar "Frontend" "$MONITOR_FRONTEND_URL" || falhas+=("$(descrever "Frontend" "$ULTIMO_CODIGO")")
fi

link_run="${GITHUB_SERVER_URL:-https://github.com}/${GITHUB_REPOSITORY:-repositorio}/actions/runs/${GITHUB_RUN_ID:-0}"
agora="$(date -u '+%Y-%m-%d %H:%M UTC')"
aberta="$(gh issue list --label "$LABEL" --state open --limit 1 --json number --jq '.[0].number // empty')"

if [ "${#falhas[@]}" -eq 0 ]; then
  if [ -n "$aberta" ]; then
    echo "Servico de volta; fechando a issue #$aberta."
    gh issue close "$aberta" --comment "O servico voltou a responder em $agora. Fechando automaticamente. Verificacao: $link_run"
  fi
  exit 0
fi

abriu_issue=0
lista="$(printf '%s\n' "${falhas[@]}")"
echo "QUEDA confirmada apos $TENTATIVAS tentativas:"
echo "$lista"

if [ -n "$aberta" ]; then
  atualizada="$(gh issue view "$aberta" --json updatedAt --jq '.updatedAt')"
  # Data ilegivel: na duvida, comenta (melhor um aviso a mais do que silenciar a queda).
  parada_min="$REAVISO_MIN"
  if ts="$(date -u -d "$atualizada" +%s 2>/dev/null)"; then
    parada_min=$(( ( $(date -u +%s) - ts ) / 60 ))
  fi
  if [ "$parada_min" -ge "$REAVISO_MIN" ]; then
    echo "Issue #$aberta ja aberta, ha $parada_min min sem atividade; comentando."
    gh issue comment "$aberta" --body "O servico continua fora do ar em $agora.

$lista

Verificacao: $link_run"
  else
    echo "Issue #$aberta ja aberta e atualizada ha $parada_min min (< $REAVISO_MIN); sem novo comentario."
  fi
else
  gh label create "$LABEL" --color B60205 --description "Servico fora do ar (aberta pelo monitor de disponibilidade)" 2>/dev/null || true
  gh issue create --label "$LABEL" --title "Indisponibilidade: o Eduka nao esta respondendo" --body "O monitor de disponibilidade confirmou a queda em $agora, apos $TENTATIVAS tentativas seguidas.

$lista

Verificacao: $link_run

Esta issue e fechada automaticamente quando o servico voltar. Enquanto estiver fora do ar, o monitor comenta aqui no maximo a cada $REAVISO_MIN minutos."
  abriu_issue=1
fi

# So o run que abre a issue fica vermelho (o aviso do GitHub por e-mail); nas execucoes seguintes da
# mesma queda sai 0, senao cada run de 5 em 5 minutos mandaria um aviso e anularia o re-aviso por hora.
if [ "$abriu_issue" -eq 1 ]; then
  exit 1
fi
exit 0
