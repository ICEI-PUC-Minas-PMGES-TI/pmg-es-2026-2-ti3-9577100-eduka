import type { Assinatura, Comprovante, FaturaPlataforma, LancarFaturaRequest } from '../types/faturaPlataforma'
import type { DadosPagamento, FormaPagamento } from '../types/pagamentoAluno'
import { apiRequest } from './api'

// Faturas da plataforma (spec §10.1 F1 a F7). Super Admin: faturas de uma
// escola em /escolas/{id}/faturas. MASTER: as da própria escola (a do token)
// em /config-pagamento/faturas.

// ---- Super Admin ----

/** Faturas de todas as escolas (IU24), do vencimento mais recente para o mais antigo. */
export function listarFaturasDaPlataforma(signal?: AbortSignal): Promise<FaturaPlataforma[]> {
  return apiRequest<FaturaPlataforma[]>('/escolas/faturas', { signal })
}

export function listarFaturasDaEscola(escolaId: string, signal?: AbortSignal): Promise<FaturaPlataforma[]> {
  return apiRequest<FaturaPlataforma[]>(`/escolas/${escolaId}/faturas`, { signal })
}

/** 409 FATURA_DUPLICADA quando a escola já tem fatura (não cancelada) na competência. */
export function lancarFatura(escolaId: string, dados: LancarFaturaRequest): Promise<FaturaPlataforma> {
  return apiRequest<FaturaPlataforma>(`/escolas/${escolaId}/faturas`, {
    method: 'POST',
    body: JSON.stringify(dados),
  })
}

/** 409 COBRANCA_JA_PAGA quando o pagamento já entrou (aqui ou no provedor) ou PAGAMENTO_EM_PROCESSAMENTO. */
export function cancelarFatura(escolaId: string, faturaId: string): Promise<FaturaPlataforma> {
  return apiRequest<FaturaPlataforma>(`/escolas/${escolaId}/faturas/${faturaId}/cancelar`, { method: 'PATCH' })
}

export function buscarComprovanteDaFatura(escolaId: string, faturaId: string): Promise<Comprovante> {
  return apiRequest<Comprovante>(`/escolas/${escolaId}/faturas/${faturaId}/comprovante`)
}

// ---- MASTER ----

/** Plano, situação, vigência e limite de usuários da escola do token (IU24-A). */
export function buscarMinhaAssinatura(signal?: AbortSignal): Promise<Assinatura> {
  return apiRequest<Assinatura>('/config-pagamento/assinatura', { signal })
}

export function listarMinhasFaturas(signal?: AbortSignal): Promise<FaturaPlataforma[]> {
  return apiRequest<FaturaPlataforma[]>('/config-pagamento/faturas', { signal })
}

/**
 * Gera (ou reaproveita) o PIX ou o boleto da fatura na conta do Eduka. Trocar
 * a forma altera a mesma cobrança. `situacao` PROCESSANDO quando o provedor
 * ainda não respondeu.
 */
export function pagarFatura(faturaId: string, forma: FormaPagamento): Promise<DadosPagamento> {
  return apiRequest<DadosPagamento>(`/config-pagamento/faturas/${faturaId}/pagar`, {
    method: 'POST',
    body: JSON.stringify({ forma }),
  })
}

export function buscarComprovanteDaMinhaFatura(faturaId: string): Promise<Comprovante> {
  return apiRequest<Comprovante>(`/config-pagamento/faturas/${faturaId}/comprovante`)
}
