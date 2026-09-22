import type { Escola } from '../types/escola'
import { apiRequest } from './api'

export function listarEscolas(signal?: AbortSignal): Promise<Escola[]> {
  return apiRequest<Escola[]>('/escolas', { signal })
}

export function ativarEscola(id: string): Promise<Escola> {
  return apiRequest<Escola>(`/escolas/${id}/ativar`, { method: 'PATCH' })
}

export function suspenderEscola(id: string): Promise<Escola> {
  return apiRequest<Escola>(`/escolas/${id}/suspender`, { method: 'PATCH' })
}
