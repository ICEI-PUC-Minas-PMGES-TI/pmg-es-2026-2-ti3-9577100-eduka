import { useState } from 'react'
import { Badge } from '../../../../components/Badge/Badge'
import { Button } from '../../../../components/Button/Button'
import { StatCard, StatCards } from '../../../../components/StatCard/StatCard'
import { buscarHistoricoFinanceiro } from '../../../../services/pagamentoAlunoService'
import {
  podePagar,
  ROTULOS_FORMA_PAGAMENTO,
  ROTULOS_STATUS_COBRANCA_ALUNO,
  type CobrancaDoHistorico,
  type FormaPagamento,
  type HistoricoFinanceiro,
  type StatusCobrancaAluno,
} from '../../../../types/pagamentoAluno'
import { formatarMoeda, hojeIso, plural } from '../../../../utils/formatters'
import { formatarData } from '../../../secretaria/components/PainelSecretaria/formatacao'
import { ModalPagamento } from '../../components/ModalPagamento/ModalPagamento'
import { useDadosDoAluno } from '../../hooks/useDadosDoAluno'
import { AlunoLayout } from '../../layouts/AlunoLayout'
import styles from './Pagamentos.module.css'

const TOM_STATUS: Record<StatusCobrancaAluno, 'success' | 'danger' | 'neutral' | 'warning'> = {
  PENDENTE: 'warning',
  EM_ATRASO: 'danger',
  PAGA: 'success',
  CANCELADA: 'neutral',
}

const UM_DIA_MS = 24 * 60 * 60 * 1000

/** "vence hoje", "vence em 3 dias", "venceu há 2 dias" (datas ISO, sem fuso). */
function prazo(vencimento: string): string {
  const dias = Math.round((Date.parse(vencimento) - Date.parse(hojeIso())) / UM_DIA_MS)
  if (dias === 0) return 'Vence hoje'
  if (dias > 0) return `Vence em ${dias} ${plural(dias, 'dia', 'dias')}`
  return `Venceu há ${-dias} ${plural(-dias, 'dia', 'dias')}`
}

/** Como a cobrança foi paga: pagamento online registrado, ou só a data da baixa feita pela escola. */
function descreverPagamento(c: CobrancaDoHistorico): string | null {
  if (c.pagamento) {
    return `${ROTULOS_FORMA_PAGAMENTO[c.pagamento.forma]} em ${formatarData(c.pagamento.dataPagamento)}`
  }
  if (c.pagaEm) return `Baixa pela escola em ${new Date(c.pagaEm).toLocaleDateString('pt-BR')}`
  return null
}

type Pagando = { cobranca: CobrancaDoHistorico; forma: FormaPagamento }

/**
 * Pagamentos do aluno: a próxima mensalidade em destaque, com PIX e boleto
 * (baixar o PDF e copiar o código), as demais em aberto e o histórico do que
 * já foi pago ou cancelado.
 */
export function Pagamentos() {
  const { dados: historico, carregando, erro, recarregar } = useDadosDoAluno<HistoricoFinanceiro | null>(
    buscarHistoricoFinanceiro,
    null,
  )
  const [pagando, setPagando] = useState<Pagando | null>(null)
  const [pagouAlguma, setPagouAlguma] = useState(false)

  const cobrancas = historico?.cobrancas ?? []
  // Em aberto: a que vence (ou venceu) primeiro vem antes. A primeira é a "próxima".
  const emAberto = cobrancas
    .filter((c) => podePagar(c.status))
    .sort((a, b) => a.dataVencimento.localeCompare(b.dataVencimento))
  const [proxima, ...outrasEmAberto] = emAberto
  const encerradas = cobrancas.filter((c) => !podePagar(c.status))

  function pagar(cobranca: CobrancaDoHistorico, forma: FormaPagamento) {
    setPagando({ cobranca, forma })
  }

  function fecharPagamento() {
    setPagando(null)
    // A lista só muda se algum pagamento caiu enquanto o modal estava aberto.
    if (pagouAlguma) {
      setPagouAlguma(false)
      recarregar()
    }
  }

  return (
    <AlunoLayout titulo="Pagamentos" subtitulo="Suas mensalidades e taxas. Pague com PIX ou boleto sem sair do Eduka.">
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
          {proxima ? (
            <ProximaMensalidade cobranca={proxima} onPagar={pagar} />
          ) : (
            cobrancas.length > 0 && (
              <div className={styles.mensagem}>
                <strong>Tudo em dia!</strong>
                <span>Você não tem nenhuma cobrança em aberto.</span>
              </div>
            )
          )}

          {cobrancas.length > 0 && (
            <StatCards rotulo="Resumo financeiro">
              <StatCard
                label="Total em aberto"
                value={formatarMoeda(historico.resumo.valorEmAberto)}
                descricao={plural(emAberto.length, '1 cobrança', `${emAberto.length} cobranças`)}
              />
              <StatCard
                label="Em atraso"
                value={formatarMoeda(historico.resumo.valorEmAtraso)}
                descricao={historico.resumo.quantidadeEmAtraso === 0 ? 'Nada vencido' : 'Já passou do vencimento'}
                alerta={historico.resumo.quantidadeEmAtraso > 0}
              />
              <StatCard
                label="Total pago"
                value={formatarMoeda(historico.resumo.valorPago)}
                descricao={plural(
                  historico.resumo.quantidadePaga,
                  '1 cobrança paga',
                  `${historico.resumo.quantidadePaga} cobranças pagas`,
                )}
              />
            </StatCards>
          )}

          {cobrancas.length === 0 && (
            <div className={styles.mensagem}>
              <strong>Nenhuma cobrança por aqui.</strong>
              <span>Quando a escola lançar uma mensalidade ou taxa, ela aparece nesta tela.</span>
            </div>
          )}

          {outrasEmAberto.length > 0 && (
            <section className={styles.cartao} aria-labelledby="titulo-outras">
              <h2 id="titulo-outras" className={styles.tituloSecao}>
                Outras cobranças em aberto
              </h2>
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
                        <span className={styles.somenteLeitor}>Pagar</span>
                      </th>
                    </tr>
                  </thead>
                  <tbody>
                    {outrasEmAberto.map((c) => (
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
                          <BotoesPagar cobranca={c} onPagar={pagar} tamanho="sm" />
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </section>
          )}

          {encerradas.length > 0 && (
            <section className={styles.cartao} aria-labelledby="titulo-historico">
              <h2 id="titulo-historico" className={styles.tituloSecao}>
                Histórico de pagamentos
              </h2>
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
                      <th scope="col">Pagamento</th>
                    </tr>
                  </thead>
                  <tbody>
                    {encerradas.map((c) => (
                      <tr key={c.id}>
                        <td>
                          <span className={styles.descricao}>{c.descricao}</span>
                          {c.turmaNome && <span className={styles.secundario}>{c.turmaNome}</span>}
                        </td>
                        <td className={styles.data}>{formatarData(c.dataVencimento)}</td>
                        <td className={styles.numero}>
                          {formatarMoeda(c.pagamento?.valorPago ?? c.valor)}
                        </td>
                        <td>
                          <Badge tone={TOM_STATUS[c.status]}>{ROTULOS_STATUS_COBRANCA_ALUNO[c.status]}</Badge>
                        </td>
                        <td className={styles.secundario}>{descreverPagamento(c) ?? '—'}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </section>
          )}
        </>
      )}

      {pagando && (
        <ModalPagamento
          cobranca={pagando.cobranca}
          forma={pagando.forma}
          onPago={() => setPagouAlguma(true)}
          onFechar={fecharPagamento}
        />
      )}
    </AlunoLayout>
  )
}

type AcaoPagar = (cobranca: CobrancaDoHistorico, forma: FormaPagamento) => void

/** Cartão de destaque da cobrança em aberto que vence primeiro. */
function ProximaMensalidade({ cobranca, onPagar }: { cobranca: CobrancaDoHistorico; onPagar: AcaoPagar }) {
  const atrasada = cobranca.status === 'EM_ATRASO'
  return (
    <section className={`${styles.proxima} ${atrasada ? styles.proximaAtrasada : ''}`} aria-labelledby="titulo-proxima">
      <div className={styles.proximaInfo}>
        <span id="titulo-proxima" className={styles.proximaRotulo}>
          {atrasada ? 'Cobrança em atraso' : 'Próxima mensalidade'}
        </span>
        <strong className={styles.proximaValor}>{formatarMoeda(cobranca.valor)}</strong>
        <span className={styles.proximaDescricao}>{cobranca.descricao}</span>
        <span className={styles.proximaPrazo}>
          <Badge tone={TOM_STATUS[cobranca.status]}>{prazo(cobranca.dataVencimento)}</Badge>
          <span className={styles.secundarioInline}>Vencimento {formatarData(cobranca.dataVencimento)}</span>
        </span>
      </div>
      <div className={styles.proximaAcoes}>
        <BotoesPagar cobranca={cobranca} onPagar={onPagar} tamanho="md" />
      </div>
    </section>
  )
}

/** "Pagar com PIX" e "Boleto": o boleto abre com o PDF para baixar e o código para copiar. */
function BotoesPagar({
  cobranca,
  onPagar,
  tamanho,
}: {
  cobranca: CobrancaDoHistorico
  onPagar: AcaoPagar
  tamanho: 'sm' | 'md'
}) {
  return (
    <div className={styles.botoes}>
      <Button size={tamanho} variant="primary" icon="card" onClick={() => onPagar(cobranca, 'PIX')}>
        Pagar com PIX
      </Button>
      <Button
        size={tamanho}
        variant="outline"
        icon="fileText"
        onClick={() => onPagar(cobranca, 'BOLETO')}
        aria-label={`Boleto de ${cobranca.descricao}: baixar o PDF ou copiar o código`}
      >
        Boleto
      </Button>
    </div>
  )
}
