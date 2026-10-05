import type { TurmaDoProfessor } from '../types/turmaProfessor'
import { apiRequest } from './api'

// Professor e escola saem do token: não há como pedir as turmas de outro professor.
export function listarMinhasTurmas(signal?: AbortSignal): Promise<TurmaDoProfessor[]> {
  return apiRequest<TurmaDoProfessor[]>('/portal/professor/turmas', { signal })
}
