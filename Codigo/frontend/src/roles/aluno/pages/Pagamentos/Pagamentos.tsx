import { useState } from 'react'
import { Badge } from '../../../../components/Badge/Badge'
import { Button } from '../../../../components/Button/Button'
import { StatCard, StatCards } from '../../../../components/StatCard/StatCard'
import { buscarHistoricoFinanceiro } from '../../../../services/pagamentoAlunoService'
import {
  podePagar,
  ROTULOS_STATUS_COBRANCA_ALUNO,
  type CobrancaDoHistorico,
  type HistoricoFinanceiro,
  type StatusCobrancaAluno,
} from '../../../../types/pagamentoAluno'
import { formatarMoeda, plural } from '../../../../utils/formatters'
import { formatarData } from '../../../secretaria/components/PainelSecretaria/formatacao'
import { ModalPagamentoPix } from '../../components/ModalPagamentoPix/ModalPagamentoPix'
import { useDadosDoAluno } from '../../hooks/useDadosDoAluno'
import { AlunoLayout } from '../../layouts/AlunoLayout'
import styles from './Pagamentos.module.css'

const TOM_STATUS: Record<StatusCobrancaAluno, 'success' | 'danger' | 'neutral' | 'warning'> = {
  PENDENTE: 'warning',
  EM_ATRASO: 'danger',
  PAGA: 'success',
  CANCELADA: 'neutral',
}

function dataDaBaixa(iso: string): string {
  return new Date(iso).toLocaleDateString('pt-BR')
}

/** Mensalidades e taxas do aluno: o que está em aberto (com o botão Pagar) e o histórico. */
export function Pagamentos() {
  const { dados: historico, carregando, erro, recarregar } = useDadosDoAluno<HistoricoFinanceiro | null>(
    buscarHistoricoFinanceiro,
    null,
  )
  const [pagando, setPagando] = useState<CobrancaDoHistorico | null>(null)
  const [pagouAlguma, setPagouAlguma] = useState(false)

  const cobrancas = historico?.cobrancas ?? []
  // Em aberto: a que vence (ou venceu) primeiro no topo.
  const emAberto = cobrancas
    .filter((c) => podePagar(c.status))
    .sort((a, b) => a.dataVencimento.localeCompare(b.dataVencimento))
  const encerradas = cobrancas.filter((c) => !podePagar(c.status))

  function fecharPagamento() {
    setPagando(null)
    // A lista só muda se algum pagamento caiu enquanto o modal estava aberto.
    if (pagouAlguma) {
      setPagouAlguma(false)
      recarregar()
    }
  }

  return (
    <AlunoLayout titulo="Pagamentos" subtitulo="Suas mensalidades e taxas. Pague com PIX sem sair do Eduka.">
      {carregando && <div className={styles.mensagem}>Carregando seus pagamentos…</div>}

      {!carregando && erro && (
        <div className={styles.mensagem} role="alert">
          <span>{erro}</span>
          <Button size="sm" onClick={recarregar}>
            Tentar novamente
          </Button>
        </div>
      )}

      {!carregando && !erro && historico && (
        <>
          <StatCards rotulo="Resumo financeiro">
            <StatCard
              label="Em aberto"
              value={formatarMoeda(historico.resumo.valorEmAberto)}
              descricao={plural(
                historico.resumo.quantidadePendente + historico.resumo.quantidadeEmAtraso,
                '1 cobrança',
                `${historico.resumo.quantidadePendente + historico.resumo.quantidadeEmAtraso} cobranças`,
              )}
              destaque
            />
            <StatCard
              label="Em atraso"
              value={formatarMoeda(historico.resumo.valorEmAtraso)}
              descricao={historico.resumo.quantidadeEmAtraso === 0 ? 'Nada vencido' : 'Já passou do vencimento'}
              alerta={historico.resumo.quantidadeEmAtraso > 0}
            />
            <StatCard
              label="Pago"
              value={formatarMoeda(historico.resumo.valorPago)}
              descricao={plural(historico.resumo.quantidadePaga, '1 cobrança paga', `${historico.resumo.quantidadePaga} cobranças pagas`)}
            />
          </StatCards>

          {cobrancas.length === 0 && (
            <div className={styles.mensagem}>
              <strong>Nenhuma cobrança por aqui.</strong>
              <span>Quando a escola lançar uma mensalidade ou taxa, ela aparece nesta tela.</span>
            </div>
          )}

          {emAberto.length > 0 && (
            <section className={styles.cartao} aria-labelledby="titulo-em-aberto">
              <h2 id="titulo-em-aberto" className={styles.tituloSecao}>
                Em aberto
              </h2>
              <TabelaCobrancas cobrancas={emAberto} onPagar={setPagando} />
            </section>
          )}

          {encerradas.length > 0 && (
            <section className={styles.cartao} aria-labelledby="titulo-historico">
              <h2 id="titulo-historico" className={styles.tituloSecao}>
                Histórico
              </h2>
              <TabelaCobrancas cobrancas={encerradas} />
            </section>
          )}
        </>
      )}

      {pagando && (
        <ModalPagamentoPix cobranca={pagando} onPago={() => setPagouAlguma(true)} onFechar={fecharPagamento} />
      )}
    </AlunoLayout>
  )
}

type TabelaCobrancasProps = {
  cobrancas: CobrancaDoHistorico[]
  /** Sem onPagar, a coluna de ação mostra só a data da baixa. */
  onPagar?: (cobranca: CobrancaDoHistorico) => void
}

function TabelaCobrancas({ cobrancas, onPagar }: TabelaCobrancasProps) {
  return (
    <div className={styles.rolagem}>
      <table className={styles.tabela}>
        <thead>
          <tr>
            <th scope="col">Descrição</th>
            <th scope="col">Vencimento</th>
            <th scope="col" className={styles.numero}>
              Valor
            </th>
            <th scope="col">Situação</th>
            <th scope="col" className={styles.acao}>
              <span className={styles.somenteLeitor}>Ação</span>
            </th>
          </tr>
        </thead>
        <tbody>
          {cobrancas.map((c) => (
            <tr key={c.id}>
              <td>
                <span className={styles.descricao}>{c.descricao}</span>
                {c.turmaNome && <span className={styles.secundario}>{c.turmaNome}</span>}
              </td>
              <td className={styles.data}>{formatarData(c.dataVencimento)}</td>
              <td className={styles.numero}>{formatarMoeda(c.valor)}</td>
              <td>
                <Badge tone={TOM_STATUS[c.status]}>{ROTULOS_STATUS_COBRANCA_ALUNO[c.status]}</Badge>
              </td>
              <td className={styles.acao}>
                {onPagar && podePagar(c.status) ? (
                  <Button size="sm" variant="primary" icon="card" onClick={() => onPagar(c)}>
                    Pagar
                  </Button>
                ) : (
                  c.pagaEm && <span className={styles.secundario}>Paga em {dataDaBaixa(c.pagaEm)}</span>
                )}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}
