import type { Escola, EscolaRequest, EscolaUpdateRequest, UsoEscola } from '../types/escola'
import { apiRequest } from './api'

export function listarEscolas(signal?: AbortSignal): Promise<Escola[]> {
  return apiRequest<Escola[]>('/escolas', { signal })
}

/** Alunos, usuários ativos, cobranças do mês e última atividade de cada escola. Perfil: SUPER_ADMIN. */
export function listarUsoDasEscolas(signal?: AbortSignal): Promise<UsoEscola[]> {
  return apiRequest<UsoEscola[]>('/escolas/uso', { signal })
}

export function buscarEscola(id: string, signal?: AbortSignal): Promise<Escola> {
  return apiRequest<Escola>(`/escolas/${id}`, { signal })
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

export function atualizarEscola(id: string, dados: EscolaUpdateRequest): Promise<Escola> {
  return apiRequest<Escola>(`/escolas/${id}`, { method: 'PUT', body: JSON.stringify(dados) })
}
