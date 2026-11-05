import { Badge } from '../../../../components/Badge/Badge'
import { ModalAlerta, ModalAviso } from '../../../../components/Modal/Modal'
import { useDetalhe } from '../../../../hooks/useDetalhe'
import { buscarHistoricoFinanceiroDoAluno } from '../../../../services/alunoService'
import {
  ROTULOS_FORMA_PAGAMENTO,
  ROTULOS_STATUS_COBRANCA_ALUNO,
  type CobrancaDoHistorico,
  type StatusCobrancaAluno,
} from '../../../../types/pagamentoAluno'
import { formatarMoeda } from '../../../../utils/formatters'
import { formatarData } from '../PainelSecretaria/formatacao'
import styles from './FinanceiroAluno.module.css'

const TOM_STATUS: Record<StatusCobrancaAluno, 'success' | 'danger' | 'neutral' | 'warning'> = {
  PENDENTE: 'warning',
  EM_ATRASO: 'danger',
  PAGA: 'success',
  CANCELADA: 'neutral',
}

/** Como a cobrança foi paga: pagamento online registrado, ou só a data da baixa manual. */
function descreverPagamento(c: CobrancaDoHistorico): string | null {
  if (c.pagamento) {
    const como = c.pagamento.forma ? ROTULOS_FORMA_PAGAMENTO[c.pagamento.forma] : 'Baixa manual'
    const estornado =
      c.pagamento.valorEstornado > 0 ? ` · ${formatarMoeda(c.pagamento.valorEstornado)} estornados` : ''
    return `${como} · ${formatarData(c.pagamento.dataPagamento)} · ${formatarMoeda(c.pagamento.valorPago)}${estornado}`
  }
  if (c.pagaEm) {
    return `Baixa manual · ${new Date(c.pagaEm).toLocaleDateString('pt-BR')}`
  }
  return null
}

/**
 * Aba "Financeiro" do cadastro do aluno: resumo e todas as cobranças, com o
 * pagamento de cada uma (GET /alunos/{id}/financeiro). Só leitura: lançar,
 * editar e dar baixa ficam na tela de Cobranças do financeiro.
 */
export function FinanceiroAluno({ alunoId }: { alunoId: string }) {
  const { dados, carregando, erro } = useDetalhe(buscarHistoricoFinanceiroDoAluno, alunoId)

  if (carregando) return <ModalAviso>Carregando o histórico financeiro…</ModalAviso>
  if (erro) return <ModalAlerta>{erro}</ModalAlerta>
  if (!dados) return null

  const { resumo, cobrancas } = dados

  return (
    <div className={styles.conteudo}>
      <dl className={styles.resumo} aria-label="Resumo financeiro">
        <div className={styles.indicador}>
          <dt>Em aberto</dt>
          <dd>{formatarMoeda(resumo.valorEmAberto)}</dd>
        </div>
        <div className={styles.indicador}>
          <dt>Em atraso</dt>
          <dd className={resumo.valorEmAtraso > 0 ? styles.alerta : undefined}>
            {formatarMoeda(resumo.valorEmAtraso)}
          </dd>
        </div>
        <div className={styles.indicador}>
          <dt>Pago</dt>
          <dd>{formatarMoeda(resumo.valorPago)}</dd>
        </div>
      </dl>

      {cobrancas.length === 0 ? (
        <ModalAviso>Nenhuma cobrança lançada para este aluno.</ModalAviso>
      ) : (
        <div className={styles.rolagem}>
          <table className={styles.tabela}>
            <caption className={styles.somenteLeitor}>
              Cobranças do aluno, da mais recente para a mais antiga
            </caption>
            <thead>
              <tr>
                <th scope="col">Descrição</th>
                <th scope="col">Vencimento</th>
                <th scope="col" className={styles.numero}>
                  Valor
                </th>
                <th scope="col">Situação</th>
                <th scope="col">Pagamento</th>
              </tr>
            </thead>
            <tbody>
              {cobrancas.map((c) => (
                <tr key={c.id}>
                  <td className={styles.colPrincipal}>
                    <span className={styles.descricao}>{c.descricao}</span>
                    {c.turmaNome && <span className={styles.secundario}>{c.turmaNome}</span>}
                  </td>
                  <td data-rotulo="Vencimento" className={styles.data}>{formatarData(c.dataVencimento)}</td>
                  <td data-rotulo="Valor" className={styles.numero}>{formatarMoeda(c.valor)}</td>
                  <td data-rotulo="Situação">
                    <Badge tone={TOM_STATUS[c.status]}>{ROTULOS_STATUS_COBRANCA_ALUNO[c.status]}</Badge>
                  </td>
                  <td data-rotulo="Pagamento" className={styles.secundario}>{descreverPagamento(c) ?? '—'}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  )
}
