import type { Cobranca, NovaCobrancaRequest } from '../types/cobranca'
import { apiRequest } from './api'

// O backend já limita as cobranças à escola de quem está logado.

export function listarCobrancas(signal?: AbortSignal): Promise<Cobranca[]> {
  return apiRequest<Cobranca[]>('/cobrancas', { signal })
}

/** Cria a cobrança como PENDENTE. 404 se o aluno não é da escola. */
export function criarCobranca(dados: NovaCobrancaRequest): Promise<Cobranca> {
  return apiRequest<Cobranca>('/cobrancas', { method: 'POST', body: JSON.stringify(dados) })
}
