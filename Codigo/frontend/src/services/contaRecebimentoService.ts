import type { AtivarRecebimentosRequest, ContaRecebimento } from '../types/contaRecebimento'
import { apiRequest } from './api'

// Conta de recebimento da escola do token (spec §8.1), só MASTER.

export function buscarContaRecebimento(signal?: AbortSignal): Promise<ContaRecebimento> {
  return apiRequest<ContaRecebimento>('/config-pagamento/conta', { signal })
}

/**
 * Cria a conta filha no provedor. Volta VERIFICACAO_PENDENTE quando o provedor
 * não respondeu a tempo; 400 PROVEDOR_RECUSOU com o motivo; 409
 * ATIVACAO_EM_ANDAMENTO ou CONTA_JA_CRIADA.
 */
export function ativarRecebimentos(dados: AtivarRecebimentosRequest): Promise<ContaRecebimento> {
  return apiRequest<ContaRecebimento>('/config-pagamento/conta/ativar', {
    method: 'POST',
    body: JSON.stringify(dados),
  })
}

/** Lê a situação da conta no provedor. 503 PROVEDOR_INDISPONIVEL se ele não responder. */
export function sincronizarContaRecebimento(): Promise<ContaRecebimento> {
  return apiRequest<ContaRecebimento>('/config-pagamento/conta/sincronizar', { method: 'POST' })
}
