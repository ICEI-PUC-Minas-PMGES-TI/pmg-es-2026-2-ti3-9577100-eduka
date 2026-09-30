import type { Professor, ProfessorRequest } from '../types/professor'
import { apiRequest } from './api'

// O backend já limita a lista à escola de quem está logado.
export function listarProfessores(signal?: AbortSignal): Promise<Professor[]> {
  return apiRequest<Professor[]>('/professores', { signal })
}

// A escola do novo professor também sai do token.
export function cadastrarProfessor(dados: ProfessorRequest): Promise<Professor> {
  return apiRequest<Professor>('/professores', { method: 'POST', body: JSON.stringify(dados) })
}

/** Afastamento (licença etc.): o professor e o login ficam inativos. */
export function inativarProfessor(id: string): Promise<Professor> {
  return apiRequest<Professor>(`/professores/${id}/inativar`, { method: 'PATCH' })
}

export function ativarProfessor(id: string): Promise<Professor> {
  return apiRequest<Professor>(`/professores/${id}/ativar`, { method: 'PATCH' })
}

/** Saída da escola. O backend recusa com 409 se o professor já tem histórico. */
export function excluirProfessor(id: string): Promise<void> {
  return apiRequest<void>(`/professores/${id}`, { method: 'DELETE' })
}
