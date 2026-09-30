import type { Professor } from '../types/professor'
import { apiRequest } from './api'

// O backend já limita a lista à escola de quem está logado.
export function listarProfessores(signal?: AbortSignal): Promise<Professor[]> {
  return apiRequest<Professor[]>('/professores', { signal })
}
