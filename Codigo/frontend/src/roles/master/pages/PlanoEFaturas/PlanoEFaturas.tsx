import { useCallback, useState } from 'react'
import { Badge } from '../../../../components/ui/Badge/Badge'
import { Button } from '../../../../components/ui/Button/Button'
import { ModalComprovante } from '../../../../components/financeiro/Comprovante/ModalComprovante'
import { EstadoDashboard, Linhas, Painel } from '../../../../components/layout/Dashboard/Dashboard'
import { Icon } from '../../../../components/ui/Icon/Icon'
import { Paginacao } from '../../../../components/ui/Paginacao/Paginacao'
import tabela from '../../../../components/financeiro/TabelaFaturas/TabelaFaturas.module.css'
import { useCarregamento } from '../../../../hooks/useCarregamento'
import { usePaginacao } from '../../../../hooks/usePaginacao'
import {
  buscarComprovanteDaMinhaFatura,
  buscarMinhaAssinatura,
  listarMinhasFaturas,
} from '../../../../services/escola/faturaPlataformaService'
import { ROTULOS_PLANO } from '../../../../types/escola/escola'
import type { Assinatura, FaturaPlataforma } from '../../../../types/escola/faturaPlataforma'
import { formatarMoeda } from '../../../../utils/formatters'
import { formatarData } from '../../../../utils/formatacao'
import { ModalPagamentoFatura } from '../../components/ModalPagamentoFatura/ModalPagamentoFatura'
import { MasterLayout } from '../../layouts/MasterLayout'
import styles from './PlanoEFaturas.module.css'
import { faturasAnteriores, proximaFatura, referenciaDaFatura } from './proximaFatura'

const SEM_FATURAS: FaturaPlataforma[] = []

function vigencia(assinatura: Assinatura): string {
  if (!assinatura.inicioVigencia && !assinatura.fimVigencia) return '—'
  const inicio = assinatura.inicioVigencia ? formatarData(assinatura.inicioVigencia) : '—'
  const fim = assinatura.fimVigencia ? formatarData(assinatura.fimVigencia) : 'sem término'
  return `${inicio} a ${fim}`
}

/**
 * Plano e faturas do MASTER (IU24-A): o que a escola paga ao Edúka. A próxima
 * fatura em destaque com o Pagar fatura (as mesmas modais do aluno, IU17-A a
 * IU17-D), a assinatura da escola e as faturas anteriores com o comprovante.
 */
export function PlanoEFaturas() {
  const { dados: faturas, carregando, erro, recarregar } = useCarregamento(
    listarMinhasFaturas,
    SEM_FATURAS,
    'Não foi possível encontrar as faturas da escola.',
  )
  const assinatura = useCarregamento<Assinatura | null>(
    buscarMinhaAssinatura,
    null,
    'Não foi possível encontrar a assinatura da escola.',
  )
  const [pagando, setPagando] = useState<FaturaPlataforma | null>(null)
  const [comprovanteDe, setComprovanteDe] = useState<string | null>(null)

  const buscarComprovante = useCallback(
    () => buscarComprovanteDaMinhaFatura(comprovanteDe ?? ''),
    [comprovanteDe],
  )

  // Uma fatura por mês: a lista vem inteira e pagina no front.
  const proxima = proximaFatura(faturas)
  const anteriores = faturasAnteriores(faturas)
  const { paginacao, topo } = usePaginacao<FaturaPlataforma, HTMLElement>(anteriores)

  /** Ao fechar, a tela mostra o pagamento gerado ou confirmado. */
  function fecharPagamento() {
    setPagando(null)
    recarregar()
  }

  return (
    <MasterLayout titulo="Plano e faturas" subtitulo="O que a escola paga ao Edúka pelo uso da plataforma.">
      <EstadoDashboard carregando={carregando} erro={erro} onTentarDeNovo={recarregar} />
      {!carregando && !erro && (
        <>
          <div className={styles.topo}>
            <section className={styles.destaque} aria-labelledby="titulo-proxima-fatura">
              <h2 id="titulo-proxima-fatura" className={styles.destaqueRotulo}>
                Próxima fatura
              </h2>
              {proxima ? (
                <>
                  <strong className={styles.valor}>{formatarMoeda(proxima.valor)}</strong>
                  <span className={styles.referencia}>
                    {referenciaDaFatura(proxima)} · vence em <strong>{formatarData(proxima.dataVencimento)}</strong>
                  </span>
                  <span className={`${styles.situacao} ${proxima.status === 'EM_ATRASO' ? styles.atrasada : ''}`}>
                    {proxima.status === 'EM_ATRASO' ? 'Em atraso' : 'A pagar'}
                  </span>
                  {proxima.acoes.includes('PAGAR') && (
                    <div className={styles.pagarLinha}>
                      <Button
                        className={styles.pagar}
                        icon="card"
                        onClick={() => setPagando(proxima)}
                        aria-label={`Pagar fatura ${referenciaDaFatura(proxima)} com PIX ou boleto`}
                      >
                        Pagar fatura
                      </Button>
                      <span className={styles.dica}>PIX ou boleto, com confirmação automática.</span>
                    </div>
                  )}
                </>
              ) : (
                <p className={styles.emDia}>
                  <Icon name="check" size={18} strokeWidth={2.5} />
                  Nenhuma fatura em aberto. Tudo em dia.
                </p>
              )}
            </section>

            <Painel titulo="Sua assinatura">
              <EstadoDashboard
                carregando={assinatura.carregando}
                erro={assinatura.erro}
                onTentarDeNovo={assinatura.recarregar}
              />
              {assinatura.dados && (
                <>
                  <Linhas
                    itens={[
                      { rotulo: 'Plano', valor: assinatura.dados.plano ? ROTULOS_PLANO[assinatura.dados.plano] : '—' },
                      {
                        rotulo: 'Situação',
                        valor: (
                          <Badge tone={assinatura.dados.ativa ? 'success' : 'danger'} icon={assinatura.dados.ativa ? 'check' : undefined}>
                            {assinatura.dados.ativa ? 'Ativa' : 'Suspensa'}
                          </Badge>
                        ),
                      },
                      { rotulo: 'Vigência', valor: vigencia(assinatura.dados) },
                      {
                        rotulo: 'Limite de usuários',
                        valor: assinatura.dados.limiteUsuarios?.toLocaleString('pt-BR') ?? '—',
                      },
                    ]}
                  />
                  <p className={styles.aviso}>
                    <Icon name="info" size={15} />
                    Fatura em atraso pode levar à suspensão do acesso da escola.
                  </p>
                </>
              )}
            </Painel>
          </div>

          <section ref={topo} className={tabela.cartao} aria-labelledby="titulo-faturas-anteriores">
            <h2 id="titulo-faturas-anteriores" className={styles.titulo}>
              Faturas anteriores
            </h2>
            {paginacao.total === 0 ? (
              <p className={tabela.vazio}>Nenhuma fatura anterior.</p>
            ) : (
              <div className={tabela.rolagem}>
                <table className={tabela.tabela}>
                  <thead>
                    <tr>
                      <th scope="col">Referência</th>
                      <th scope="col">Vencimento</th>
                      <th scope="col">Valor</th>
                      <th scope="col">Situação</th>
                      <th scope="col" className={tabela.acao}>
                        Comprovante
                      </th>
                    </tr>
                  </thead>
                  <tbody>
                    {paginacao.itensDaPagina.map((f) => (
                      <tr key={f.id}>
                        <td className={tabela.colPrincipal}>
                          <span className={tabela.descricao}>{referenciaDaFatura(f)}</span>
                        </td>
                        <td className={tabela.nowrap} data-rotulo="Vencimento">
                          {formatarData(f.dataVencimento)}
                        </td>
                        <td className={`${tabela.nowrap} ${styles.valorLinha}`} data-rotulo="Valor">
                          {formatarMoeda(f.pagamento?.valorPago ?? f.valor)}
                        </td>
                        <td data-rotulo="Situação">
                          {f.status === 'PAGA' ? (
                            <Badge tone="success" icon="check">
                              Paga
                            </Badge>
                          ) : (
                            <Badge tone="neutral">Cancelada</Badge>
                          )}
                        </td>
                        <td className={tabela.acao}>
                          {f.acoes.includes('VER_COMPROVANTE') ? (
                            <button
                              type="button"
                              className={styles.link}
                              onClick={() => setComprovanteDe(f.id)}
                              aria-label={`Ver comprovante de ${referenciaDaFatura(f)}`}
                            >
                              Ver comprovante
                            </button>
                          ) : (
                            <span className={tabela.secundario}>—</span>
                          )}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </section>
          <Paginacao paginacao={paginacao} rotulo="Páginas das faturas anteriores" />
        </>
      )}

      {pagando && <ModalPagamentoFatura fatura={pagando} onPago={recarregar} onFechar={fecharPagamento} />}
      {comprovanteDe && <ModalComprovante buscar={buscarComprovante} onFechar={() => setComprovanteDe(null)} />}
    </MasterLayout>
  )
}
