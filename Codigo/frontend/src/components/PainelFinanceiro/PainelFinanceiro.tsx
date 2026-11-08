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
import { usePaginasDoPainel } from '../../hooks/usePaginacao'
import { obterMovimentoFinanceiro } from '../../services/dashboardService'
import { Badge } from '../Badge/Badge'
import { Button } from '../Button/Button'
import { formatarIso } from '../CampoData/datas'
import { CampoPeriodo, type Periodo } from '../CampoPeriodo/CampoPeriodo'
import { GraficoComparativo, PaginasDoPainel, Painel, PainelVazio } from '../Dashboard/Dashboard'
import { erroDoPeriodo, MESES, rotuladorDeMeses } from './textos'
import styles from './PainelFinanceiro.module.css'

// Blocos do resumo financeiro (GET /dashboard/financeiro), comuns ao Início do
// FINANCEIRO e ao do MASTER.

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

/** "27/10 às 14:32", no fuso da escola. */
function diaEHora(instante: string): string {
  return DIA_E_HORA.format(new Date(instante)).replace(', ', ' às ')
}

/** Atalhos de período: os últimos meses, que o painel já trouxe; trocar entre eles não busca de novo. */
const PERIODOS = [3, 6, 12]
const PERIODO_INICIAL = 6

// Estável: o useCarregamento pede o valor inicial sempre igual.
const SEM_MESES: MesFinanceiro[] = []

/** Os últimos meses (atalho) ou um período escolhido no calendário. */
type FiltroDoGrafico = { tipo: 'meses'; meses: number } | { tipo: 'periodo'; periodo: Periodo }

type PilulasProps<T extends string | number> = {
  /** Lido pelo leitor de tela, ex.: "Período do gráfico". */
  rotulo: string
  opcoes: { valor: T; rotulo: string }[]
  /** Opção ativa; null quando nenhuma está (ex.: período escolhido à mão). */
  valor: T | null
  onChange: (valor: T) => void
  /** Pílula a mais no fim do grupo (ex.: o calendário do período). */
  children?: ReactNode
}

/** Botões de escolha única em forma de pílula, para filtros e agrupamentos. */
export function Pilulas<T extends string | number>({ rotulo, opcoes, valor, onChange, children }: PilulasProps<T>) {
  return (
    <div className={styles.periodos} role="group" aria-label={rotulo}>
      {opcoes.map((opcao) => {
        const ativa = opcao.valor === valor
        return (
          <button
            key={opcao.valor}
            type="button"
            aria-pressed={ativa}
            className={`${styles.periodo} ${ativa ? styles.periodoAtivo : ''}`}
            onClick={() => onChange(opcao.valor)}
          >
            {opcao.rotulo}
          </button>
        )
      })}
      {children}
    </div>
  )
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
  const rotularMes = rotuladorDeMeses(doPeriodo.map((mes) => mes.mes))

  const quando =
    filtro.tipo === 'meses'
      ? `nos últimos ${filtro.meses} meses`
      : filtro.periodo.de === filtro.periodo.ate
        ? `em ${formatarIso(filtro.periodo.de)}`
        : `de ${formatarIso(filtro.periodo.de)} a ${formatarIso(filtro.periodo.ate)}`

  function escolherPeriodo(escolhido: Periodo) {
    const erro = erroDoPeriodo(escolhido)
    setErroPeriodo(erro)
    if (!erro) setFiltro({ tipo: 'periodo', periodo: escolhido })
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
        grupos={doPeriodo.map((mes) => ({ ...rotularMes(mes.mes), valores: [mes.entrou, mes.saiu] }))}
      />
    )
  }

  return (
    <Painel
      titulo="Dinheiro que entrou e saiu"
      descricao={`Cobranças recebidas e despesas pagas ${quando}.`}
      acao={
        <Pilulas
          rotulo="Período do gráfico"
          opcoes={PERIODOS.map((meses) => ({ valor: meses, rotulo: `${meses} meses` }))}
          valor={filtro.tipo === 'meses' ? filtro.meses : null}
          onChange={escolherMeses}
        >
          <CampoPeriodo
            valor={periodo}
            onChange={escolherPeriodo}
            placeholder="Personalizado"
            max={hojeIso()}
            aria-pressed={periodo !== null}
            className={`${styles.periodo} ${periodo ? styles.periodoAtivo : ''}`}
          />
        </Pilulas>
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

/** Aba do painel de vencimentos: as já vencidas ou as dos próximos dias. */
export type AbaVencimentos = 'EM_ATRASO' | 'SEMANA'

type VencemNaSemanaProps = {
  cobrancas: CobrancaDaSemana[]
  /**
   * Quantas cobranças estão em atraso na escola. O backend manda só as mais
   * antigas; com o total, a aba mostra o número certo e avisa o corte.
   */
  totalEmAtraso?: number
  /** Botão no canto do painel, de acordo com a aba aberta (ex.: ver a lista filtrada). */
  acao?: (aba: AbaVencimentos) => ReactNode
}

/**
 * Cobranças em atraso e as que vencem nos próximos dias, em duas abas, 5 por
 * página. Abre na de atraso quando há alguma.
 */
export function VencemNaSemana({ cobrancas, totalEmAtraso, acao }: VencemNaSemanaProps) {
  const hoje = hojeIso()
  const atrasadas = useMemo(() => cobrancas.filter((c) => c.status === 'EM_ATRASO'), [cobrancas])
  const daSemana = useMemo(() => cobrancas.filter((c) => c.status !== 'EM_ATRASO'), [cobrancas])
  const [escolhida, setEscolhida] = useState<AbaVencimentos | null>(null)
  const aba = escolhida ?? (atrasadas.length > 0 ? 'EM_ATRASO' : 'SEMANA')
  const lista = aba === 'EM_ATRASO' ? atrasadas : daSemana
  const paginacao = usePaginasDoPainel(lista, aba)
  const emAtraso = Math.max(totalEmAtraso ?? 0, atrasadas.length)
  const cortadas = aba === 'EM_ATRASO' && emAtraso > atrasadas.length

  return (
    <Painel
      titulo="Vencimentos"
      descricao={
        aba === 'EM_ATRASO'
          ? cortadas
            ? `As ${atrasadas.length} mais antigas de ${emAtraso} cobranças em atraso.`
            : 'Vencidas e ainda não pagas, a mais antiga primeiro.'
          : 'As que vencem de hoje até os próximos 6 dias.'
      }
      acao={acao?.(aba)}
    >
      <Pilulas<AbaVencimentos>
        rotulo="Quais vencimentos mostrar"
        opcoes={[
          { valor: 'EM_ATRASO', rotulo: `Em atraso (${emAtraso})` },
          { valor: 'SEMANA', rotulo: `Esta semana (${daSemana.length})` },
        ]}
        valor={aba}
        onChange={setEscolhida}
      />
      {lista.length === 0 ? (
        <PainelVazio>
          {aba === 'EM_ATRASO' ? 'Nenhuma cobrança em atraso.' : 'Nenhuma cobrança vence nos próximos dias.'}
        </PainelVazio>
      ) : (
        <ul className={styles.lista}>
          {paginacao.itensDaPagina.map((cobranca) => {
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
      <PaginasDoPainel
        paginacao={paginacao}
        rotulo={aba === 'EM_ATRASO' ? 'Páginas das cobranças em atraso' : 'Páginas das cobranças da semana'}
      />
    </Painel>
  )
}

/** Pagamentos confirmados pelo Asaas e baixas manuais, mais recentes primeiro. */
export function UltimosPagamentos({ pagamentos }: { pagamentos: PagamentoRecente[] }) {
  const paginacao = usePaginasDoPainel(pagamentos)
  return (
    <Painel titulo="Últimos pagamentos" descricao="Confirmados pelo Asaas ou com baixa manual.">
      {pagamentos.length === 0 ? (
        <PainelVazio>Nenhum pagamento recebido ainda.</PainelVazio>
      ) : (
        <ul className={styles.lista}>
          {paginacao.itensDaPagina.map((pagamento) => (
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
      <PaginasDoPainel paginacao={paginacao} rotulo="Páginas dos últimos pagamentos" />
    </Painel>
  )
}

/** Operações recentes do log de auditoria, com quem fez e quando. */
export function UltimasAcoes({ acoes }: { acoes: AcaoRecente[] }) {
  const paginacao = usePaginasDoPainel(acoes)
  return (
    <Painel titulo="Últimas ações" descricao="O que foi feito no financeiro da escola, por quem e quando.">
      {acoes.length === 0 ? (
        <PainelVazio>Nenhuma ação registrada ainda.</PainelVazio>
      ) : (
        <ul className={styles.lista}>
          {paginacao.itensDaPagina.map((acao, indice) => (
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
      <PaginasDoPainel paginacao={paginacao} rotulo="Páginas das últimas ações" />
    </Painel>
  )
}
