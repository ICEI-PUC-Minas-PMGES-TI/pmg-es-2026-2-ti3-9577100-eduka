import { useCallback, useState } from 'react'
import { Button } from '../../../../components/Button/Button'
import { ModalComprovante } from '../../../../components/Comprovante/ModalComprovante'
import { EstadoDashboard } from '../../../../components/Dashboard/Dashboard'
import { TabelaFaturas } from '../../../../components/TabelaFaturas/TabelaFaturas'
import { useCarregamento } from '../../../../hooks/useCarregamento'
import { ApiError } from '../../../../services/api'
import {
  ativarRecebimentos,
  buscarContaRecebimento,
  sincronizarContaRecebimento,
} from '../../../../services/contaRecebimentoService'
import { buscarComprovanteDaMinhaFatura, listarMinhasFaturas } from '../../../../services/faturaPlataformaService'
import type { AtivarRecebimentosRequest, ContaRecebimento } from '../../../../types/contaRecebimento'
import type { FaturaPlataforma } from '../../../../types/faturaPlataforma'
import type { FormaPagamento } from '../../../../types/pagamentoAluno'
import { ModalAtivarRecebimentos } from '../../components/ModalAtivarRecebimentos/ModalAtivarRecebimentos'
import { ModalPagamentoFatura } from '../../components/ModalPagamentoFatura/ModalPagamentoFatura'
import { PainelContaRecebimento } from '../../components/PainelContaRecebimento/PainelContaRecebimento'
import { MasterLayout } from '../../layouts/MasterLayout'

const SEM_FATURAS: FaturaPlataforma[] = []

type Pagando = { fatura: FaturaPlataforma; forma: FormaPagamento }

/**
 * Recebimentos do MASTER: a conta de recebimento da escola (ativar e
 * acompanhar a aprovação) e as faturas que a escola paga ao Eduka (Fluxo A):
 * pagar com PIX ou boleto e ver o comprovante.
 */
export function Recebimentos() {
  const { dados: faturas, carregando, erro, recarregar } = useCarregamento(
    listarMinhasFaturas,
    SEM_FATURAS,
    'Não foi possível encontrar as faturas da escola.',
  )
  const conta = useCarregamento<ContaRecebimento | null>(
    buscarContaRecebimento,
    null,
    'Não foi possível encontrar a conta de recebimento da escola.',
  )
  const [contaAtualizada, setContaAtualizada] = useState<ContaRecebimento | null>(null)
  const [ativando, setAtivando] = useState(false)
  const [sincronizando, setSincronizando] = useState(false)
  const [erroSincronizar, setErroSincronizar] = useState<string | null>(null)
  const [pagando, setPagando] = useState<Pagando | null>(null)
  const [comprovanteDe, setComprovanteDe] = useState<string | null>(null)

  const contaAtual = contaAtualizada ?? conta.dados

  async function ativar(dados: AtivarRecebimentosRequest) {
    setContaAtualizada(await ativarRecebimentos(dados))
    setErroSincronizar(null)
    setAtivando(false)
  }

  async function sincronizar() {
    setSincronizando(true)
    setErroSincronizar(null)
    try {
      setContaAtualizada(await sincronizarContaRecebimento())
    } catch (e) {
      setErroSincronizar(
        e instanceof ApiError && e.codigo === 'PROVEDOR_INDISPONIVEL'
          ? 'O provedor de pagamento não respondeu. Tente de novo em instantes.'
          : e instanceof ApiError
            ? (e.detalhe ?? e.message)
            : 'Erro inesperado ao sincronizar a conta.',
      )
    } finally {
      setSincronizando(false)
    }
  }

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
    <MasterLayout
      titulo="Recebimentos"
      subtitulo="Conta para receber as mensalidades online e faturas que a escola paga ao Edúka."
    >
      <EstadoDashboard carregando={conta.carregando} erro={conta.erro} onTentarDeNovo={conta.recarregar} />
      {contaAtual && (
        <PainelContaRecebimento
          conta={contaAtual}
          sincronizando={sincronizando}
          erroSincronizar={erroSincronizar}
          onAtivar={() => setAtivando(true)}
          onSincronizar={sincronizar}
        />
      )}

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
      {ativando && contaAtual && (
        <ModalAtivarRecebimentos conta={contaAtual} onAtivar={ativar} onFechar={() => setAtivando(false)} />
      )}
    </MasterLayout>
  )
}
