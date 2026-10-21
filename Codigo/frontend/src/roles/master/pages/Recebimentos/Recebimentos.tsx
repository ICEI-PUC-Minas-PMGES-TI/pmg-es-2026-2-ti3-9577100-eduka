import { useCallback, useState } from 'react'
import { Button } from '../../../../components/Button/Button'
import { ModalComprovante } from '../../../../components/Comprovante/ModalComprovante'
import { EstadoDashboard } from '../../../../components/Dashboard/Dashboard'
import { TabelaFaturas } from '../../../../components/TabelaFaturas/TabelaFaturas'
import { useCarregamento } from '../../../../hooks/useCarregamento'
import { buscarComprovanteDaMinhaFatura, listarMinhasFaturas } from '../../../../services/faturaPlataformaService'
import type { FaturaPlataforma } from '../../../../types/faturaPlataforma'
import type { FormaPagamento } from '../../../../types/pagamentoAluno'
import { ModalPagamentoFatura } from '../../components/ModalPagamentoFatura/ModalPagamentoFatura'
import { MasterLayout } from '../../layouts/MasterLayout'

const SEM_FATURAS: FaturaPlataforma[] = []

type Pagando = { fatura: FaturaPlataforma; forma: FormaPagamento }

/**
 * Recebimentos do MASTER. Por enquanto, as faturas que a escola paga ao Eduka
 * (Fluxo A): pagar com PIX ou boleto e ver o comprovante. A ativação da conta
 * de recebimento entra aqui na PO.5.
 */
export function Recebimentos() {
  const { dados: faturas, carregando, erro, recarregar } = useCarregamento(
    listarMinhasFaturas,
    SEM_FATURAS,
    'Não foi possível encontrar as faturas da escola.',
  )
  const [pagando, setPagando] = useState<Pagando | null>(null)
  const [comprovanteDe, setComprovanteDe] = useState<string | null>(null)

  const buscarComprovante = useCallback(
    () => buscarComprovanteDaMinhaFatura(comprovanteDe ?? ''),
    [comprovanteDe],
  )

  /** Ao fechar, a lista mostra o pagamento gerado ou confirmado. */
  function fecharPagamento() {
    setPagando(null)
    recarregar()
  }

  return (
    <MasterLayout titulo="Recebimentos" subtitulo="Faturas que a escola paga ao Edúka pelo uso da plataforma.">
      <EstadoDashboard carregando={carregando} erro={erro} onTentarDeNovo={recarregar} />
      {!carregando && !erro && (
        <TabelaFaturas
          faturas={faturas}
          vazio="Nenhuma fatura do Edúka por enquanto."
          acoes={(f) => (
            <>
              {f.acoes.includes('PAGAR') && (
                <>
                  <Button size="sm" variant="primary" icon="card" onClick={() => setPagando({ fatura: f, forma: 'PIX' })}>
                    Pagar com PIX
                  </Button>
                  <Button
                    size="sm"
                    variant="outline"
                    icon="fileText"
                    onClick={() => setPagando({ fatura: f, forma: 'BOLETO' })}
                    aria-label={`Boleto da fatura ${f.competencia}`}
                  >
                    Boleto
                  </Button>
                </>
              )}
              {f.acoes.includes('VER_COMPROVANTE') && (
                <Button size="sm" variant="outline" icon="eye" onClick={() => setComprovanteDe(f.id)}>
                  Comprovante
                </Button>
              )}
            </>
          )}
        />
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
