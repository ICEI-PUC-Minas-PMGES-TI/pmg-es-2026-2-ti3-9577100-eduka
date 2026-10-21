import type { CobrancaAluno, DadosPagamento, FormaPagamento, HistoricoFinanceiro } from '../types/pagamentoAluno'
import { apiBaixarArquivo, apiRequest } from './api'

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
 * Gera (ou reaproveita) o PIX ou o boleto da cobrança. Trocar a forma altera a
 * mesma cobrança no provedor. `situacao` PROCESSANDO quando o provedor ainda
 * não respondeu: é só consultar a cobrança e pedir de novo. Recusas chegam
 * como ApiError com `codigo` (PAGADOR_SEM_CPF, COBRANCA_NAO_PAGAVEL,
 * RECEBIMENTOS_INDISPONIVEIS, PAGAMENTO_EM_PROCESSAMENTO...).
 */
export function pagarCobranca(id: string, forma: FormaPagamento): Promise<DadosPagamento> {
  return apiRequest<DadosPagamento>(`/portal/aluno/cobrancas/${id}/pagar`, {
    method: 'POST',
    body: JSON.stringify({ forma }),
  })
}

/** PDF oficial do boleto, depois de gerado com a forma BOLETO. 409 BOLETO_NAO_GERADO antes disso. */
export function baixarBoletoPdf(id: string): Promise<Blob> {
  return apiBaixarArquivo(`/portal/aluno/cobrancas/${id}/boleto`, 'application/pdf')
}
