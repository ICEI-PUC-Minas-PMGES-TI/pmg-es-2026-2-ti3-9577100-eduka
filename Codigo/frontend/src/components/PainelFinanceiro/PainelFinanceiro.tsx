import type { ReactNode } from 'react'
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
import { formatarDiaMes, formatarMoeda, hojeIso } from '../../utils/formatters'
import { Badge } from '../Badge/Badge'
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

/** Gráfico de barras dos 6 últimos meses: cobranças pagas e despesas pagas. */
export function EntrouESaiu({ meses }: { meses: MesFinanceiro[] }) {
  const semMovimento = meses.every((mes) => mes.entrou === 0 && mes.saiu === 0)
  return (
    <Painel titulo="Dinheiro que entrou e saiu" descricao="Cobranças recebidas e despesas pagas nos últimos 6 meses.">
      {semMovimento ? (
        <PainelVazio>Nenhum dinheiro entrou ou saiu nos últimos 6 meses.</PainelVazio>
      ) : (
        <GraficoComparativo
          rotulo="Dinheiro que entrou e saiu por mês"
          series={['Entrou', 'Saiu']}
          formatar={formatarMoeda}
          grupos={meses.map((mes) => {
            const nome = nomeDoMes(mes.mes)
            return { rotulo: nome.curto, titulo: nome.extenso, valores: [mes.entrou, mes.saiu] }
          })}
        />
      )}
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
