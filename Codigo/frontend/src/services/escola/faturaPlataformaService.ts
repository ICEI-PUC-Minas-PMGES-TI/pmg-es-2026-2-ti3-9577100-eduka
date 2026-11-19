import type { PaginaDoBackend } from '../../hooks/usePaginacao'
import type {
  Assinatura,
  Comprovante,
  FaturaPlataforma,
  LancarFaturaRequest,
  StatusFatura,
  SuspensaoPrevista,
} from '../../types/escola/faturaPlataforma'
import type { DadosPagamento, FormaPagamento } from '../../types/financeiro/pagamentoAluno'
import { apiRequest } from '../api'

// Faturas da plataforma (spec §10.1 F1 a F7). Super Admin: faturas de uma
// escola em /escolas/{id}/faturas. MASTER: as da própria escola (a do token)
// em /config-pagamento/faturas.

// ---- Super Admin ----

/** Faturas de todas as escolas (IU24), do vencimento mais recente para o mais antigo. */
export function listarFaturasDaPlataforma(signal?: AbortSignal): Promise<FaturaPlataforma[]> {
  return apiRequest<FaturaPlataforma[]>('/escolas/faturas', { signal })
}

/** Uma página das faturas da escola, da competência mais recente para a mais antiga. */
export function listarPaginaDeFaturasDaEscola(
  escolaId: string,
  pagina: number,
  tamanho: number,
  signal?: AbortSignal,
): Promise<PaginaDoBackend<FaturaPlataforma>> {
  const params = new URLSearchParams({ pagina: String(pagina), tamanho: String(tamanho) })
  return apiRequest(`/escolas/${escolaId}/faturas/pagina?${params}`, { signal })
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
/**
 * Suspensão prevista da escola do token por fatura em atraso; null quando não há
 * (204). Perfil: MASTER.
 */
export async function buscarSuspensaoPrevista(signal?: AbortSignal): Promise<SuspensaoPrevista | null> {
  return (await apiRequest<SuspensaoPrevista | undefined>('/config-pagamento/assinatura/suspensao', { signal })) ?? null
}

export function buscarMinhaAssinatura(signal?: AbortSignal): Promise<Assinatura> {
  return apiRequest<Assinatura>('/config-pagamento/assinatura', { signal })
}

/** Uma fatura da escola do token, com a situação do pagamento. 404 se não é da escola. */
export function buscarMinhaFatura(faturaId: string, signal?: AbortSignal): Promise<FaturaPlataforma> {
  return apiRequest<FaturaPlataforma>(`/config-pagamento/faturas/${faturaId}`, { signal })
}

export type ConsultaMinhasFaturas = {
  pagina: number
  tamanho: number
  /** Só as faturas numa destas situações; sem ela, todas. */
  status?: StatusFatura[]
  /** COMPETENCIA (padrão) vem da mais recente; VENCIMENTO, da que vence primeiro. */
  ordenarPor?: 'COMPETENCIA' | 'VENCIMENTO'
}

/** Uma página das faturas da escola do token. */
export function listarPaginaDeMinhasFaturas(
  consulta: ConsultaMinhasFaturas,
  signal?: AbortSignal,
): Promise<PaginaDoBackend<FaturaPlataforma>> {
  const params = new URLSearchParams({ pagina: String(consulta.pagina), tamanho: String(consulta.tamanho) })
  if (consulta.status?.length) params.set('status', consulta.status.join(','))
  if (consulta.ordenarPor) params.set('ordenarPor', consulta.ordenarPor)
  return apiRequest(`/config-pagamento/faturas/pagina?${params}`, { signal })
}

/**
 * A fatura em aberto que vence (ou venceu) primeiro; null se está tudo pago.
 * Pede PENDENTE junto de EM_ATRASO: a vencida pode seguir gravada como
 * PENDENTE até a rotina diária do backend marcá-la.
 */
export async function buscarProximaMinhaFatura(signal?: AbortSignal): Promise<FaturaPlataforma | null> {
  const pagina = await listarPaginaDeMinhasFaturas(
    { pagina: 0, tamanho: 1, status: ['PENDENTE', 'EM_ATRASO'], ordenarPor: 'VENCIMENTO' },
    signal,
  )
  return pagina.conteudo[0] ?? null
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
