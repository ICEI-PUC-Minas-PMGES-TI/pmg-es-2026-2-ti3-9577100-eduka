import type { CobrancaAluno, DadosPagamento, HistoricoFinanceiro } from '../types/pagamentoAluno'
import { apiRequest } from './api'

// Pagamentos no portal do aluno: o backend usa o aluno e a escola do token,
// e cobrança de outro aluno responde 404.

/** Resumo e todas as cobranças do aluno logado. */
export function buscarHistoricoFinanceiro(signal?: AbortSignal): Promise<HistoricoFinanceiro> {
  return apiRequest<HistoricoFinanceiro>('/portal/aluno/financeiro', { signal })
}

/** Situação atual de uma cobrança: a tela consulta enquanto espera a confirmação do PIX. */
export function buscarMinhaCobranca(id: string, signal?: AbortSignal): Promise<CobrancaAluno> {
  return apiRequest<CobrancaAluno>(`/portal/aluno/cobrancas/${id}`, { signal })
}

/**
 * Gera (ou reaproveita) o PIX da cobrança. `situacao` PROCESSANDO quando o
 * provedor ainda não respondeu: é só consultar a cobrança e pedir de novo.
 * Recusas chegam como ApiError com `codigo` (PAGADOR_SEM_CPF,
 * COBRANCA_NAO_PAGAVEL, RECEBIMENTOS_INDISPONIVEIS, PAGAMENTO_EM_PROCESSAMENTO...).
 */
export function pagarComPix(id: string): Promise<DadosPagamento> {
  return apiRequest<DadosPagamento>(`/portal/aluno/cobrancas/${id}/pagar`, {
    method: 'POST',
    body: JSON.stringify({ forma: 'PIX' }),
  })
}
