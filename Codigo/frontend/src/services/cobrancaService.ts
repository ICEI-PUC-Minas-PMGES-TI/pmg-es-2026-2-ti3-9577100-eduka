import type { Cobranca, NovaCobrancaRequest, PaginaCobrancas, StatusCobranca } from '../types/cobranca'
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
