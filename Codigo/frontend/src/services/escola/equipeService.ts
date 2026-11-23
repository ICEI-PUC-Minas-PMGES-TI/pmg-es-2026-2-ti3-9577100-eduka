import type { MembroEquipe, MembroEquipeRequest, MembroEquipeUpdateRequest } from '../../types/escola/equipe'
import { apiRequest } from '../api'

// Equipe da escola (secretaria e financeiro). Perfil: MASTER. A escola sai do token.

/** Secretaria e financeiro, em ordem de nome, sem o CPF. */
export function listarEquipe(signal?: AbortSignal): Promise<MembroEquipe[]> {
  return apiRequest<MembroEquipe[]>('/usuarios/equipe', { signal })
}

/** Detalhe com o CPF, para a edição. */
export function buscarMembroEquipe(id: string, signal?: AbortSignal): Promise<MembroEquipe> {
  return apiRequest<MembroEquipe>(`/usuarios/equipe/${id}`, { signal })
}

/**
 * Cadastra e envia o primeiro acesso por e-mail. 409 se o CPF já está na
 * equipe ou o e-mail já é login de alguém.
 */
export function cadastrarMembroEquipe(dados: MembroEquipeRequest): Promise<MembroEquipe> {
  return apiRequest<MembroEquipe>('/usuarios/equipe', { method: 'POST', body: JSON.stringify(dados) })
}

/** Substitui os dados; trocar o e-mail troca o login. */
export function atualizarMembroEquipe(id: string, dados: MembroEquipeUpdateRequest): Promise<MembroEquipe> {
  return apiRequest<MembroEquipe>(`/usuarios/equipe/${id}`, { method: 'PUT', body: JSON.stringify(dados) })
}

/** O login para de funcionar; o registro continua. */
export function inativarMembroEquipe(id: string): Promise<MembroEquipe> {
  return apiRequest<MembroEquipe>(`/usuarios/equipe/${id}/inativar`, { method: 'PATCH' })
}

export function ativarMembroEquipe(id: string): Promise<MembroEquipe> {
  return apiRequest<MembroEquipe>(`/usuarios/equipe/${id}/ativar`, { method: 'PATCH' })
}

/** Nova senha provisória e novo e-mail. 409 se a pessoa já definiu a senha. */
export function reenviarAcessoEquipe(id: string): Promise<void> {
  return apiRequest<void>(`/usuarios/equipe/${id}/primeiro-acesso`, { method: 'POST' })
}
