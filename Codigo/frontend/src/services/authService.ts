import { ACCESS_TOKEN_KEY, apiRequest } from './api'

export const USUARIO_KEY = 'eduka.usuario'

export type PerfilUsuario =
  | 'SUPER_ADMIN'
  | 'MASTER'
  | 'SECRETARIA'
  | 'FINANCEIRO'
  | 'PROFESSOR'
  | 'ALUNO'

export type LoginResponse = {
  accessToken: string
  refreshToken: string
  usuarioId: string
  perfil: PerfilUsuario
}

export type UsuarioSessao = {
  id: string
  perfil: PerfilUsuario
  login: string
}

export async function autenticar(login: string, senha: string): Promise<LoginResponse> {
  const resposta = await apiRequest<LoginResponse>('/login', {
    method: 'POST',
    body: JSON.stringify({ login: login.trim(), senha }),
  })

  salvarSessao(resposta, login.trim())
  return resposta
}

export function salvarSessao(dados: LoginResponse, login: string): void {
  try {
    localStorage.setItem(ACCESS_TOKEN_KEY, dados.accessToken)
    const usuario: UsuarioSessao = {
      id: dados.usuarioId,
      perfil: dados.perfil,
      login,
    }
    localStorage.setItem(USUARIO_KEY, JSON.stringify(usuario))
  } catch {
    // Silencia erros de storage (ex: navegação anônima com quota cheia)
  }
}

export function obterToken(): string | null {
  try {
    return localStorage.getItem(ACCESS_TOKEN_KEY)
  } catch {
    return null
  }
}

export function obterUsuarioLogado(): UsuarioSessao | null {
  try {
    const raw = localStorage.getItem(USUARIO_KEY)
    return raw ? (JSON.parse(raw) as UsuarioSessao) : null
  } catch {
    return null
  }
}

export function limparSessao(): void {
  try {
    localStorage.removeItem(ACCESS_TOKEN_KEY)
    localStorage.removeItem(USUARIO_KEY)
  } catch {
    // Ignora erros ao limpar
  }
}

export function estaAutenticado(): boolean {
  return Boolean(obterToken())
}

