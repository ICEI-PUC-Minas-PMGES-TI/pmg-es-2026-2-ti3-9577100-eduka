import { useState } from 'react'
import { EstadoDashboard } from '../../../../components/layout/Dashboard/Dashboard'
import { useCarregamento } from '../../../../hooks/useCarregamento'
import { ApiError } from '../../../../services/api'
import {
  ativarRecebimentos,
  buscarContaRecebimento,
  sincronizarContaRecebimento,
} from '../../../../services/financeiro/contaRecebimentoService'
import {
  aprovarContaNoSandbox,
  atalhosSandboxHabilitados,
  mensagemErroSandbox,
} from '../../../../services/financeiro/sandboxService'
import type { AtivarRecebimentosRequest, ContaRecebimento } from '../../../../types/financeiro/contaRecebimento'
import { ModalAtivarRecebimentos } from '../../components/ModalAtivarRecebimentos/ModalAtivarRecebimentos'
import { PainelContaRecebimento } from '../../components/PainelContaRecebimento/PainelContaRecebimento'
import { MasterLayout } from '../../layouts/MasterLayout'

/**
 * Recebimentos do MASTER (IU23): a conta de recebimento da escola no Asaas,
 * para receber as mensalidades dos alunos. As faturas que a escola paga ao
 * Eduka ficam em Plano e faturas (IU24-A).
 */
export function Recebimentos() {
  const conta = useCarregamento<ContaRecebimento | null>(
    buscarContaRecebimento,
    null,
    'Não foi possível encontrar a conta de recebimento da escola.',
  )
  const [contaAtualizada, setContaAtualizada] = useState<ContaRecebimento | null>(null)
  const [ativando, setAtivando] = useState(false)
  const [sincronizando, setSincronizando] = useState(false)
  const [erroSincronizar, setErroSincronizar] = useState<string | null>(null)

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

  /** Atalho de teste: aprova a conta no Sandbox e já lê a situação nova. */
  async function aprovarNoSandbox() {
    setSincronizando(true)
    setErroSincronizar(null)
    try {
      await aprovarContaNoSandbox()
      setContaAtualizada(await sincronizarContaRecebimento())
    } catch (e) {
      setErroSincronizar(
        mensagemErroSandbox(e, 'A conta ainda não foi criada no provedor. Envie o cadastro antes de aprovar.'),
      )
    } finally {
      setSincronizando(false)
    }
  }

  return (
    <MasterLayout
      titulo="Recebimentos"
      subtitulo="Ative a conta da escola para receber as mensalidades por PIX e boleto."
    >
      <EstadoDashboard carregando={conta.carregando} erro={conta.erro} onTentarDeNovo={conta.recarregar} />
      {contaAtual && (
        <PainelContaRecebimento
          conta={contaAtual}
          sincronizando={sincronizando}
          erroSincronizar={erroSincronizar}
          onAtivar={() => setAtivando(true)}
          onSincronizar={sincronizar}
          onAprovarNoSandbox={atalhosSandboxHabilitados ? aprovarNoSandbox : undefined}
        />
      )}

      {ativando && contaAtual && (
        <ModalAtivarRecebimentos conta={contaAtual} onAtivar={ativar} onFechar={() => setAtivando(false)} />
      )}
    </MasterLayout>
  )
}
