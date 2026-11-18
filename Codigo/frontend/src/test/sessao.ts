import type { PerfilUsuario } from '../services/acesso/authService'
import { ACCESS_TOKEN_KEY } from '../services/api'

/**
 * Entra como o perfil, para as telas que mostram ou escondem ações por perfil.
 * JWT só com o payload que o front lê (sub e perfil); a assinatura não é
 * conferida no cliente. Limpe com localStorage.clear() no afterEach.
 */
export function logarComo(perfil: PerfilUsuario) {
  const payload = btoa(JSON.stringify({ sub: 'usuario-1', perfil })).replace(/=+$/, '')
  localStorage.setItem(ACCESS_TOKEN_KEY, `cabecalho.${payload}.assinatura`)
}
