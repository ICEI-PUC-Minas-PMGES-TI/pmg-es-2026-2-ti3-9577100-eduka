import type { Alocacao, AlocacaoRequest, Turma } from '../types/alocacao'
import { apiRequest } from './api'

// O backend já limita a lista à escola de quem está logado.
export function listarTurmas(signal?: AbortSignal): Promise<Turma[]> {
  return apiRequest<Turma[]>('/academico/secretaria/turmas', { signal })
}

export function listarAlocacoesDaTurma(turmaId: number, signal?: AbortSignal): Promise<Alocacao[]> {
  return apiRequest<Alocacao[]>(`/turmas/${turmaId}/alocacoes`, { signal })
}

export function listarAlocacoesDoProfessor(professorId: string, signal?: AbortSignal): Promise<Alocacao[]> {
  return apiRequest<Alocacao[]>(`/professores/${professorId}/alocacoes`, { signal })
}

/** O backend recusa com 409 se a disciplina já tem professor na turma. */
export function alocarProfessor(professorId: string, dados: AlocacaoRequest): Promise<Alocacao> {
  return apiRequest<Alocacao>(`/professores/${professorId}/alocacoes`, {
    method: 'POST',
    body: JSON.stringify(dados),
  })
}

export function desalocarProfessor(professorId: string, alocacaoId: string): Promise<void> {
  return apiRequest<void>(`/professores/${professorId}/alocacoes/${alocacaoId}`, { method: 'DELETE' })
}
