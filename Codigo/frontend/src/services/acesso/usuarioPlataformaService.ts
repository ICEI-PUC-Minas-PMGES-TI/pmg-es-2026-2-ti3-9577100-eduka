import { apiRequest } from '../api'
import type { PaginaDoBackend } from '../../hooks/usePaginacao'
import type { FiltrosUsuariosPlataforma, UsuarioPlataforma } from '../../types/acesso/usuarioPlataforma'

const BASE = '/usuarios/plataforma'

/** Logins de todas as escolas, em ordem de login, uma página por vez. */
export function listarUsuariosDaPlataforma(
  filtros: FiltrosUsuariosPlataforma,
  pagina: number,
  tamanho: number,
  signal?: AbortSignal,
): Promise<PaginaDoBackend<UsuarioPlataforma>> {
  const params = new URLSearchParams({ pagina: String(pagina), tamanho: String(tamanho) })
  for (const [chave, valor] of Object.entries(filtros)) {
    if (valor) params.set(chave, valor)
  }
  return apiRequest<PaginaDoBackend<UsuarioPlataforma>>(`${BASE}?${params}`, { signal })
}

/** Só o login de MASTER: os demais são geridos pela escola. */
export function inativarUsuarioDaPlataforma(id: string): Promise<UsuarioPlataforma> {
  return apiRequest<UsuarioPlataforma>(`${BASE}/${id}/inativar`, { method: 'PATCH' })
}

/** Conta no limite de usuários da escola (409 LIMITE_USUARIOS_ATINGIDO). */
export function ativarUsuarioDaPlataforma(id: string): Promise<UsuarioPlataforma> {
  return apiRequest<UsuarioPlataforma>(`${BASE}/${id}/ativar`, { method: 'PATCH' })
}

/** Nova senha provisória e o e-mail de primeiro acesso de novo (409 se ele já definiu a senha). */
export function reenviarAcessoDaPlataforma(id: string): Promise<UsuarioPlataforma> {
  return apiRequest<UsuarioPlataforma>(`${BASE}/${id}/primeiro-acesso`, { method: 'POST' })
}
