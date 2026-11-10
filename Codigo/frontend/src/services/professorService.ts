import type { PaginaComResumo } from '../hooks/usePaginacao'
import type { Professor, ProfessorRequest, ResumoProfessores } from '../types/professor'
import { apiRequest } from './api'

// O backend já limita a lista à escola de quem está logado.
export function listarProfessores(signal?: AbortSignal): Promise<Professor[]> {
  return apiRequest<Professor[]>('/professores', { signal })
}

export type ConsultaProfessores = {
  pagina: number
  tamanho: number
  /** Parte do nome. */
  nome?: string
}

/** Uma página dos professores, em ordem de nome, com o resumo da escola inteira. */
export function listarPaginaDeProfessores(
  consulta: ConsultaProfessores,
  signal?: AbortSignal,
): Promise<PaginaComResumo<Professor, ResumoProfessores>> {
  const params = new URLSearchParams({ pagina: String(consulta.pagina), tamanho: String(consulta.tamanho) })
  if (consulta.nome) params.set('nome', consulta.nome)
  return apiRequest(`/professores/pagina?${params}`, { signal })
}

/** Detalhe com todos os dados (a listagem não traz o CPF). */
export function buscarProfessor(id: string, signal?: AbortSignal): Promise<Professor> {
  return apiRequest<Professor>(`/professores/${id}`, { signal })
}

// A escola do novo professor também sai do token.
export function cadastrarProfessor(dados: ProfessorRequest): Promise<Professor> {
  return apiRequest<Professor>('/professores', { method: 'POST', body: JSON.stringify(dados) })
}

/** Substitui os dados do professor. 409 se o CPF ou o e-mail já estão em uso. */
export function atualizarProfessor(id: string, dados: ProfessorRequest): Promise<Professor> {
  return apiRequest<Professor>(`/professores/${id}`, { method: 'PUT', body: JSON.stringify(dados) })
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
