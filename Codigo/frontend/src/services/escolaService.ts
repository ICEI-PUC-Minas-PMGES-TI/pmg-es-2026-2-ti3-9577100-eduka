import type { Escola, EscolaRequest } from '../types/escola'
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

export function cadastrarEscola(dados: EscolaRequest): Promise<Escola> {
  return apiRequest<Escola>('/escolas', { method: 'POST', body: JSON.stringify(dados) })
}
