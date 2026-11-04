import { useMemo, useState, type ReactNode } from 'react'
import type { PerfilUsuario } from '../../services/authService'
import type {
  AcaoRecente,
  CobrancaDaSemana,
  EntidadeAuditada,
  MesFinanceiro,
  OperacaoAuditoria,
  PagamentoRecente,
} from '../../types/dashboard'
import { ROTULOS_FORMA_PAGAMENTO } from '../../types/pagamentoAluno'
import { formatarDiaMes, formatarMoeda, formatarMoedaCompacta, hojeIso } from '../../utils/formatters'
import { useCarregamento } from '../../hooks/useCarregamento'
import { obterMovimentoFinanceiro } from '../../services/dashboardService'
import { Badge } from '../Badge/Badge'
import { Button } from '../Button/Button'
import { formatarIso } from '../CampoData/datas'
import { CampoPeriodo, type Periodo } from '../CampoPeriodo/CampoPeriodo'
import { GraficoComparativo, Painel, PainelVazio } from '../Dashboard/Dashboard'
import styles from './PainelFinanceiro.module.css'

// Blocos do resumo financeiro (GET /dashboard/financeiro), comuns ao Início do
// FINANCEIRO e ao do MASTER.

const MESES = ['jan', 'fev', 'mar', 'abr', 'mai', 'jun', 'jul', 'ago', 'set', 'out', 'nov', 'dez']
const MESES_POR_EXTENSO = [
  'janeiro',
  'fevereiro',
  'março',
  'abril',
  'maio',
  'junho',
  'julho',
  'agosto',
  'setembro',
  'outubro',
  'novembro',
  'dezembro',
]

// Os instantes do backend mostrados no fuso da escola, como o "hoje" dele.
const DIA_E_HORA = new Intl.DateTimeFormat('pt-BR', {
  timeZone: 'America/Sao_Paulo',
  day: '2-digit',
  month: '2-digit',
  hour: '2-digit',
  minute: '2-digit',
})

const ROTULOS_PERFIL: Record<PerfilUsuario, string> = {
  SUPER_ADMIN: 'Eduka',
  MASTER: 'Gestão',
  SECRETARIA: 'Secretaria',
  FINANCEIRO: 'Financeiro',
  PROFESSOR: 'Professor',
  ALUNO: 'Aluno',
}

const VERBOS: Record<OperacaoAuditoria, string> = {
  CRIAR: 'Criou',
  EDITAR: 'Editou',
  CANCELAR: 'Cancelou',
  PAGAR: 'Registrou o pagamento de',
  CONFIRMAR_PAGAMENTO: 'Confirmou o pagamento de',
  PAGO_APOS_CANCELAR: 'Recebeu o pagamento depois de cancelar',
  ESTORNAR: 'Estornou',
  CONTESTAR: 'Contestou',
  ATIVAR: 'Ativou',
  ALTERAR_SITUACAO: 'Alterou a situação de',
}

const OBJETOS: Record<EntidadeAuditada, string> = {
  COBRANCA: 'uma cobrança',
  PAGAMENTO: 'um pagamento',
  DESPESA: 'uma despesa',
  CONTA_RECEBIMENTO: 'a conta de recebimento',
  FATURA_PLATAFORMA: 'uma fatura do Eduka',
}

/** "Registrou o pagamento de" + "uma cobrança"; "de a conta" vira "da conta". */
function descreverAcao(acao: AcaoRecente): string {
  const verbo = VERBOS[acao.operacao]
  const objeto = OBJETOS[acao.entidade]
  const contracao = verbo.endsWith(' de') && /^[ao] /.exec(objeto)
  return contracao ? `${verbo.slice(0, -3)} d${objeto}` : `${verbo} ${objeto}`
}

/** "2026-10" -> rótulo curto ("out") e por extenso ("outubro de 2026"). */
function nomeDoMes(mes: string): { curto: string; extenso: string } {
  const [ano, numero] = mes.split('-').map(Number)
  return { curto: MESES[numero - 1], extenso: `${MESES_POR_EXTENSO[numero - 1]} de ${ano}` }
}

/** "27/10 às 14:32", no fuso da escola. */
function diaEHora(instante: string): string {
  return DIA_E_HORA.format(new Date(instante)).replace(', ', ' às ')
}

/** Atalhos de período: os últimos meses, que o painel já trouxe; trocar entre eles não busca de novo. */
const PERIODOS = [3, 6, 12]
const PERIODO_INICIAL = 6

/** Maior período do calendário, como o backend aceita. */
const MAXIMO_DE_MESES = 24

// Estável: o useCarregamento pede o valor inicial sempre igual.
const SEM_MESES: MesFinanceiro[] = []

/** Os últimos meses (atalho) ou um período escolhido no calendário. */
type FiltroDoGrafico = { tipo: 'meses'; meses: number } | { tipo: 'periodo'; periodo: Periodo }

/** Quantos meses de calendário o período toca, contando os das pontas. */
function mesesNoPeriodo({ de, ate }: Periodo): number {
  const [anoDe, mesDe] = de.split('-').map(Number)
  const [anoAte, mesAte] = ate.split('-').map(Number)
  return (anoAte - anoDe) * 12 + (mesAte - mesDe) + 1
}

/**
 * Gráfico de barras de cobranças pagas e despesas pagas por mês. Os atalhos
 * mostram os últimos 3, 6 ou 12 meses; "Personalizado" abre um calendário e
 * busca o período escolhido.
 */
export function EntrouESaiu({ meses }: { meses: MesFinanceiro[] }) {
  const [filtro, setFiltro] = useState<FiltroDoGrafico>({ tipo: 'meses', meses: PERIODO_INICIAL })
  const [erroPeriodo, setErroPeriodo] = useState<string | null>(null)
  const periodo = filtro.tipo === 'periodo' ? filtro.periodo : null

  const buscarPeriodo = useMemo(
    () => (periodo ? (signal: AbortSignal) => obterMovimentoFinanceiro(periodo, signal) : null),
    [periodo],
  )
  const doCalendario = useCarregamento(buscarPeriodo, SEM_MESES, 'Não foi possível carregar o período.')

  const doPeriodo = filtro.tipo === 'meses' ? meses.slice(-filtro.meses) : doCalendario.dados
  const entrou = doPeriodo.reduce((soma, mes) => soma + mes.entrou, 0)
  const saiu = doPeriodo.reduce((soma, mes) => soma + mes.saiu, 0)
  const saldo = entrou - saiu
  // Com mais de um ano na tela, o mês curto leva o ano junto ("out/25").
  const variosAnos = new Set(doPeriodo.map((mes) => mes.mes.slice(0, 4))).size > 1

  const quando =
    filtro.tipo === 'meses'
      ? `nos últimos ${filtro.meses} meses`
      : filtro.periodo.de === filtro.periodo.ate
        ? `em ${formatarIso(filtro.periodo.de)}`
        : `de ${formatarIso(filtro.periodo.de)} a ${formatarIso(filtro.periodo.ate)}`

  function escolherPeriodo(escolhido: Periodo) {
    if (mesesNoPeriodo(escolhido) > MAXIMO_DE_MESES) {
      setErroPeriodo(`Escolha um período de até ${MAXIMO_DE_MESES} meses.`)
      return
    }
    setErroPeriodo(null)
    setFiltro({ tipo: 'periodo', periodo: escolhido })
  }

  function escolherMeses(quantidade: number) {
    setErroPeriodo(null)
    setFiltro({ tipo: 'meses', meses: quantidade })
  }

  function conteudo() {
    if (periodo && doCalendario.carregando) {
      return <PainelVazio>Carregando o período…</PainelVazio>
    }
    if (periodo && doCalendario.erro) {
      return (
        <div className={styles.erroGrafico} role="alert">
          <span>{doCalendario.erro}</span>
          <Button size="sm" onClick={doCalendario.recarregar}>
            Tentar novamente
          </Button>
        </div>
      )
    }
    if (entrou === 0 && saiu === 0) {
      return <PainelVazio>{`Nenhum dinheiro entrou ou saiu ${quando}.`}</PainelVazio>
    }
    return (
      <GraficoComparativo
        rotulo="Dinheiro que entrou e saiu por mês"
        series={['Entrou', 'Saiu']}
        formatar={formatarMoeda}
        formatarEixo={formatarMoedaCompacta}
        resumo={[formatarMoeda(entrou), formatarMoeda(saiu)]}
        aoLadoDaLegenda={
          <Badge tone={saldo < 0 ? 'danger' : 'success'}>
            Saldo {saldo < 0 ? '−' : '+'} {formatarMoeda(Math.abs(saldo))}
          </Badge>
        }
        grupos={doPeriodo.map((mes) => {
          const nome = nomeDoMes(mes.mes)
          return {
            rotulo: variosAnos ? `${nome.curto}/${mes.mes.slice(2, 4)}` : nome.curto,
            titulo: nome.extenso,
            valores: [mes.entrou, mes.saiu],
          }
        })}
      />
    )
  }

  return (
    <Painel
      titulo="Dinheiro que entrou e saiu"
      descricao={`Cobranças recebidas e despesas pagas ${quando}.`}
      acao={
        <div className={styles.periodos} role="group" aria-label="Período do gráfico">
          {PERIODOS.map((opcao) => {
            const ativo = filtro.tipo === 'meses' && filtro.meses === opcao
            return (
              <button
                key={opcao}
                type="button"
                aria-pressed={ativo}
                className={`${styles.periodo} ${ativo ? styles.periodoAtivo : ''}`}
                onClick={() => escolherMeses(opcao)}
              >
                {opcao} meses
              </button>
            )
          })}
          <CampoPeriodo
            valor={periodo}
            onChange={escolherPeriodo}
            placeholder="Personalizado"
            max={hojeIso()}
            aria-pressed={periodo !== null}
            className={`${styles.periodo} ${periodo ? styles.periodoAtivo : ''}`}
          />
        </div>
      }
    >
      {erroPeriodo && (
        <p className={styles.avisoPeriodo} role="alert">
          {erroPeriodo}
        </p>
      )}
      {conteudo()}
    </Painel>
  )
}

/** Cobranças em atraso e as que vencem nos próximos dias, com o atraso em vermelho. */
export function VencemNaSemana({ cobrancas, acao }: { cobrancas: CobrancaDaSemana[]; acao?: ReactNode }) {
  const hoje = hojeIso()
  return (
    <Painel titulo="Vencem esta semana" descricao="As em atraso e as que vencem nos próximos dias." acao={acao}>
      {cobrancas.length === 0 ? (
        <PainelVazio>Nenhuma cobrança vence esta semana e nada está em atraso.</PainelVazio>
      ) : (
        <ul className={styles.lista}>
          {cobrancas.map((cobranca) => {
            const atrasada = cobranca.status === 'EM_ATRASO'
            const [, mes, dia] = cobranca.dataVencimento.split('-')
            return (
              <li key={cobranca.id} className={styles.item}>
                <div className={`${styles.data} ${atrasada ? styles.dataAtrasada : ''}`} aria-hidden="true">
                  <strong>{dia}</strong>
                  <span>{MESES[Number(mes) - 1]}</span>
                </div>
                <div className={styles.corpo}>
                  <strong className={styles.titulo}>{cobranca.alunoNome ?? 'Aluno sem nome'}</strong>
                  <span className={styles.contexto}>
                    {cobranca.descricao} · vence {formatarDiaMes(cobranca.dataVencimento)}
                  </span>
                </div>
                <div className={styles.lado}>
                  <strong className={styles.valor}>{formatarMoeda(cobranca.valor)}</strong>
                  {atrasada ? (
                    <Badge tone="danger" icon="alert">
                      Em atraso
                    </Badge>
                  ) : cobranca.dataVencimento === hoje ? (
                    <Badge tone="warning">Vence hoje</Badge>
                  ) : (
                    <Badge tone="neutral">A vencer</Badge>
                  )}
                </div>
              </li>
            )
          })}
        </ul>
      )}
    </Painel>
  )
}

/** Pagamentos confirmados pelo Asaas e baixas manuais, mais recentes primeiro. */
export function UltimosPagamentos({ pagamentos }: { pagamentos: PagamentoRecente[] }) {
  return (
    <Painel titulo="Últimos pagamentos" descricao="Confirmados pelo Asaas ou com baixa manual.">
      {pagamentos.length === 0 ? (
        <PainelVazio>Nenhum pagamento recebido ainda.</PainelVazio>
      ) : (
        <ul className={styles.lista}>
          {pagamentos.map((pagamento) => (
            <li key={pagamento.cobrancaId} className={styles.item}>
              <div className={styles.corpo}>
                <strong className={styles.titulo}>{pagamento.alunoNome ?? 'Aluno sem nome'}</strong>
                <span className={styles.contexto}>
                  {pagamento.descricao} · {pagamento.forma ? ROTULOS_FORMA_PAGAMENTO[pagamento.forma] : 'Baixa manual'}{' '}
                  · {diaEHora(pagamento.pagoEm)}
                </span>
              </div>
              <strong className={`${styles.valor} ${styles.recebido}`}>{formatarMoeda(pagamento.valor)}</strong>
            </li>
          ))}
        </ul>
      )}
    </Painel>
  )
}

/** Operações recentes do log de auditoria, com quem fez e quando. */
export function UltimasAcoes({ acoes }: { acoes: AcaoRecente[] }) {
  return (
    <Painel titulo="Últimas ações" descricao="O que foi feito no financeiro da escola, por quem e quando.">
      {acoes.length === 0 ? (
        <PainelVazio>Nenhuma ação registrada ainda.</PainelVazio>
      ) : (
        <ul className={styles.lista}>
          {acoes.map((acao, indice) => (
            <li key={`${acao.registroId}-${acao.dataHora}-${indice}`} className={styles.item}>
              <div className={styles.corpo}>
                <strong className={styles.titulo}>
                  {acao.usuarioId === null ? 'Asaas' : (acao.usuarioNome ?? 'Usuário sem nome')}
                  {acao.perfil && <span className={styles.perfil}> · {ROTULOS_PERFIL[acao.perfil]}</span>}
                </strong>
                <span className={styles.contexto}>{descreverAcao(acao)}</span>
              </div>
              <span className={styles.hora}>{diaEHora(acao.dataHora)}</span>
            </li>
          ))}
        </ul>
      )}
    </Painel>
  )
}
