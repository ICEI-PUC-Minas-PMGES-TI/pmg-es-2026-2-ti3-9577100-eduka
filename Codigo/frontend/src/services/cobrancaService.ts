import type { Comprovante } from '../types/faturaPlataforma'
import type {
  AtualizarCobrancaRequest,
  Cobranca,
  NovaCobrancaRequest,
  PaginaCobrancas,
  StatusCobranca,
} from '../types/cobranca'
import type { NivelRiscoAtraso, RiscoAtraso } from '../types/riscoAtraso'
import { apiRequest } from './api'

// O backend já limita as cobranças à escola de quem está logado.

export type ConsultaCobrancas = {
  /** Começa em 0. */
  pagina: number
  tamanho: number
  status?: StatusCobranca
  /** Parte do nome do aluno. */
  nome?: string
}

/** Uma página das cobranças, filtrada e ordenada pelo vencimento no backend. */
export function listarCobrancas(consulta: ConsultaCobrancas, signal?: AbortSignal): Promise<PaginaCobrancas> {
  const params = new URLSearchParams({ pagina: String(consulta.pagina), tamanho: String(consulta.tamanho) })
  if (consulta.status) params.set('status', consulta.status)
  if (consulta.nome) params.set('nome', consulta.nome)
  return apiRequest<PaginaCobrancas>(`/cobrancas/pagina?${params}`, { signal })
}

/** Cria a cobrança como PENDENTE. 404 se o aluno não é da escola. */
export function criarCobranca(dados: NovaCobrancaRequest): Promise<Cobranca> {
  return apiRequest<Cobranca>('/cobrancas', { method: 'POST', body: JSON.stringify(dados) })
}

/** Edita descrição, valor e vencimento. 409 COBRANCA_NAO_EDITAVEL depois que o aluno clicou em Pagar. */
export function atualizarCobranca(id: string, dados: AtualizarCobrancaRequest): Promise<Cobranca> {
  return apiRequest<Cobranca>(`/cobrancas/${id}`, { method: 'PUT', body: JSON.stringify(dados) })
}

/** Cancela (e remove no Asaas, se já emitida). 409 COBRANCA_JA_PAGA se já foi paga. */
export function cancelarCobranca(id: string): Promise<Cobranca> {
  return apiRequest<Cobranca>(`/cobrancas/${id}/cancelar`, { method: 'PATCH' })
}

/** Comprovante do pagamento online, montado pelo Edúka (IU17-D). */
export function buscarComprovanteDaCobranca(id: string): Promise<Comprovante> {
  return apiRequest<Comprovante>(`/cobrancas/${id}/comprovante`)
}

/**
 * Risco de atraso de cada aluno, do maior para o menor, com o motivo.
 * `nivelMinimo` traz só os desse nível para cima. Perfis: MASTER e FINANCEIRO.
 */
export function listarRiscoAtraso(nivelMinimo?: NivelRiscoAtraso, signal?: AbortSignal): Promise<RiscoAtraso[]> {
  const params = nivelMinimo ? `?${new URLSearchParams({ nivelMinimo })}` : ''
  return apiRequest<RiscoAtraso[]>(`/cobrancas/risco-atraso${params}`, { signal })
}
