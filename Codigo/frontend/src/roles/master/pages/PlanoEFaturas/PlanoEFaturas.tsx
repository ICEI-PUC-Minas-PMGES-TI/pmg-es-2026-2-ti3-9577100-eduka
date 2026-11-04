import { useCallback, useState } from 'react'
import { Badge } from '../../../../components/Badge/Badge'
import { Button } from '../../../../components/Button/Button'
import { ModalComprovante } from '../../../../components/Comprovante/ModalComprovante'
import { Colunas, EstadoDashboard, Linhas, Painel, PainelVazio } from '../../../../components/Dashboard/Dashboard'
import { Icon } from '../../../../components/Icon/Icon'
import { TabelaFaturas } from '../../../../components/TabelaFaturas/TabelaFaturas'
import { useCarregamento } from '../../../../hooks/useCarregamento'
import {
  buscarComprovanteDaMinhaFatura,
  buscarMinhaAssinatura,
  listarMinhasFaturas,
} from '../../../../services/faturaPlataformaService'
import { ROTULOS_PLANO } from '../../../../types/escola'
import { ROTULOS_STATUS_FATURA, type Assinatura, type FaturaPlataforma } from '../../../../types/faturaPlataforma'
import type { FormaPagamento } from '../../../../types/pagamentoAluno'
import { formatarMoeda } from '../../../../utils/formatters'
import { formatarData } from '../../../secretaria/components/PainelSecretaria/formatacao'
import { ModalPagamentoFatura } from '../../components/ModalPagamentoFatura/ModalPagamentoFatura'
import { MasterLayout } from '../../layouts/MasterLayout'
import styles from './PlanoEFaturas.module.css'
import { proximaFatura } from './proximaFatura'

const SEM_FATURAS: FaturaPlataforma[] = []

type Pagando = { fatura: FaturaPlataforma; forma: FormaPagamento }


function vigencia(assinatura: Assinatura): string {
  if (!assinatura.inicioVigencia && !assinatura.fimVigencia) return '—'
  const inicio = assinatura.inicioVigencia ? formatarData(assinatura.inicioVigencia) : '—'
  const fim = assinatura.fimVigencia ? formatarData(assinatura.fimVigencia) : 'sem término'
  return `${inicio} a ${fim}`
}

/**
 * Plano e faturas do MASTER (IU24-A): a próxima fatura do Edúka com o Pagar
 * (mesmo caminho do aluno, PIX ou boleto), a assinatura da escola e as
 * faturas anteriores com o comprovante.
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
  const [pagando, setPagando] = useState<Pagando | null>(null)
  const [comprovanteDe, setComprovanteDe] = useState<string | null>(null)

  const buscarComprovante = useCallback(
    () => buscarComprovanteDaMinhaFatura(comprovanteDe ?? ''),
    [comprovanteDe],
  )

  const proxima = proximaFatura(faturas)
  const anteriores = faturas.filter((f) => f.id !== proxima?.id)

  /** Ao fechar, a lista mostra o pagamento gerado ou confirmado. */
  function fecharPagamento() {
    setPagando(null)
    recarregar()
  }

  return (
    <MasterLayout titulo="Plano e faturas" subtitulo="O que a escola paga ao Edúka pelo uso da plataforma.">
      <EstadoDashboard carregando={carregando} erro={erro} onTentarDeNovo={recarregar} />
      {!carregando && !erro && (
        <>
          <Colunas>
            <Painel titulo="Próxima fatura">
              {proxima ? (
                <div className={styles.proxima}>
                  <strong className={styles.valor}>{formatarMoeda(proxima.valor)}</strong>
                  <span className={styles.referencia}>
                    {proxima.descricao} · vence em {formatarData(proxima.dataVencimento)}
                  </span>
                  <Badge tone={proxima.status === 'EM_ATRASO' ? 'danger' : 'warning'}>
                    {ROTULOS_STATUS_FATURA[proxima.status]}
                  </Badge>
                  {proxima.acoes.includes('PAGAR') && (
                    <div className={styles.botoes}>
                      <Button variant="primary" icon="card" onClick={() => setPagando({ fatura: proxima, forma: 'PIX' })}>
                        Pagar fatura com PIX
                      </Button>
                      <Button
                        variant="outline"
                        icon="fileText"
                        onClick={() => setPagando({ fatura: proxima, forma: 'BOLETO' })}
                      >
                        Pagar com boleto
                      </Button>
                    </div>
                  )}
                </div>
              ) : (
                <PainelVazio>Nenhuma fatura em aberto. Tudo em dia.</PainelVazio>
              )}
            </Painel>

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
                          <Badge tone={assinatura.dados.ativa ? 'success' : 'danger'}>
                            {assinatura.dados.ativa ? 'Ativa' : 'Suspensa'}
                          </Badge>
                        ),
                      },
                      { rotulo: 'Vigência', valor: vigencia(assinatura.dados) },
                      { rotulo: 'Limite de usuários', valor: assinatura.dados.limiteUsuarios ?? '—' },
                    ]}
                  />
                  <p className={styles.aviso}>
                    <Icon name="info" size={14} />
                    Fatura em atraso pode levar à suspensão do acesso da escola.
                  </p>
                </>
              )}
            </Painel>
          </Colunas>

          <h2 className={styles.titulo}>Faturas anteriores</h2>
          <TabelaFaturas
            faturas={anteriores}
            vazio="Nenhuma fatura anterior."
            acoes={(f) =>
              f.acoes.includes('VER_COMPROVANTE') && (
                <Button size="sm" variant="outline" icon="eye" onClick={() => setComprovanteDe(f.id)}>
                  Ver comprovante
                </Button>
              )
            }
          />
        </>
      )}

      {pagando && (
        <ModalPagamentoFatura
          fatura={pagando.fatura}
          forma={pagando.forma}
          onPago={recarregar}
          onFechar={fecharPagamento}
        />
      )}
      {comprovanteDe && <ModalComprovante buscar={buscarComprovante} onFechar={() => setComprovanteDe(null)} />}
    </MasterLayout>
  )
}
