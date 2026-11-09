import type { ReactNode, Ref } from 'react'
import type { EstadoPaginacao } from '../../hooks/usePaginacao'
import { formatarData } from '../../roles/secretaria/components/PainelSecretaria/formatacao'
import { ROTULOS_STATUS_FATURA, type FaturaPlataforma, type StatusFatura } from '../../types/faturaPlataforma'
import { ROTULOS_FORMA_PAGAMENTO } from '../../types/pagamentoAluno'
import { formatarMoeda } from '../../utils/formatters'
import { Badge } from '../Badge/Badge'
import { Paginacao } from '../Paginacao/Paginacao'
import styles from './TabelaFaturas.module.css'

const TOM_STATUS: Record<StatusFatura, 'success' | 'danger' | 'neutral' | 'warning'> = {
  PENDENTE: 'warning',
  EM_ATRASO: 'danger',
  PAGA: 'success',
  CANCELADA: 'neutral',
}

/** "2026-11" -> "11/2026". */
function formatarCompetencia(competencia: string): string {
  const [ano, mes] = competencia.split('-')
  return ano && mes ? `${mes}/${ano}` : competencia
}

function descreverSituacao(fatura: FaturaPlataforma): string | null {
  if (fatura.pagamento) {
    return `${ROTULOS_FORMA_PAGAMENTO[fatura.pagamento.forma]} em ${formatarData(fatura.pagamento.dataPagamento)}`
  }
  return fatura.situacaoPagamento === 'PROCESSANDO' ? 'Gerando o pagamento…' : null
}

type TabelaFaturasProps = {
  /**
   * As faturas já paginadas: do usePaginacao (lista carregada inteira) ou do
   * useListaPaginada (página do backend).
   */
  paginacao: EstadoPaginacao<FaturaPlataforma>
  /** Recebe o topo da tabela, para onde a troca de página rola. */
  topo: Ref<HTMLElement>
  /** Outra página a caminho do backend: a atual fica e a navegação espera. */
  atualizando?: boolean
  /** Texto quando não há fatura. */
  vazio: string
  /** Botões da linha, a partir das `acoes` que o backend liberou. */
  acoes: (fatura: FaturaPlataforma) => ReactNode
  /** Coluna da escola, para a lista de todas as escolas (IU24). */
  mostrarEscola?: boolean
}

/** Faturas da plataforma (Fluxo A), da competência mais recente para a mais antiga. */
export function TabelaFaturas({
  paginacao,
  topo,
  atualizando = false,
  vazio,
  acoes,
  mostrarEscola = false,
}: TabelaFaturasProps) {
  return (
    <>
      <section ref={topo} className={styles.cartao} aria-label="Faturas da plataforma" aria-busy={atualizando}>
        {paginacao.total === 0 ? (
          <p className={styles.vazio}>{vazio}</p>
        ) : (
          <div className={styles.rolagem}>
            <table className={styles.tabela}>
              <thead>
                <tr>
                  {mostrarEscola && <th scope="col">Escola</th>}
                  <th scope="col">Competência</th>
                  <th scope="col">Vencimento</th>
                  <th scope="col" className={styles.numero}>
                    Valor
                  </th>
                  <th scope="col">Situação</th>
                  <th scope="col" className={styles.acao}>
                    <span className={styles.somenteLeitor}>Ações</span>
                  </th>
                </tr>
              </thead>
              <tbody>
                {paginacao.itensDaPagina.map((f) => {
                  const situacao = descreverSituacao(f)
                  return (
                    <tr key={f.id}>
                      {mostrarEscola && (
                        <td>
                          <span className={styles.descricao}>{f.escolaNome}</span>
                        </td>
                      )}
                      <td>
                        <span className={styles.descricao}>{formatarCompetencia(f.competencia)}</span>
                        <span className={styles.secundario}>{f.descricao}</span>
                      </td>
                      <td className={styles.nowrap}>{formatarData(f.dataVencimento)}</td>
                      <td className={styles.numero}>{formatarMoeda(f.pagamento?.valorPago ?? f.valor)}</td>
                      <td>
                        <Badge tone={TOM_STATUS[f.status]}>{ROTULOS_STATUS_FATURA[f.status]}</Badge>
                        {situacao && <span className={styles.secundario}>{situacao}</span>}
                      </td>
                      <td className={styles.acao}>
                        <div className={styles.botoes}>{acoes(f)}</div>
                      </td>
                    </tr>
                  )
                })}
              </tbody>
            </table>
          </div>
        )}
      </section>
      <Paginacao paginacao={paginacao} rotulo="Páginas das faturas" carregando={atualizando} />
    </>
  )
}
