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
  /** Nulo quando trocaSenhaObrigatoria é verdadeiro. */
  accessToken: string | null
  refreshToken: string | null
  usuarioId: string
  perfil: PerfilUsuario
  /**
   * O usuário entrou com a senha provisória: ainda não há sessão, e ele
   * precisa definir a definitiva com definirSenhaDefinitiva.
   */
  trocaSenhaObrigatoria: boolean
  /**
   * A senha conferiu, mas ainda não há sessão: falta confirmar com
   * confirmarCodigo o código enviado por e-mail. Nulo quando não há essa etapa.
   */
  verificacao: VerificacaoLogin | null
}

export type VerificacaoLogin = {
  id: string
  /** Segundos, a partir da resposta, até o código expirar. */
  validadeSegundos: number
  /** Segundos, a partir da resposta, até poder pedir outro código. */
  reenvioSegundos: number
}

export type UsuarioSessao = {
  id: string
  perfil: PerfilUsuario
  login: string
}

const PERFIS_VALIDOS: readonly PerfilUsuario[] = [
  'SUPER_ADMIN',
  'MASTER',
  'SECRETARIA',
  'FINANCEIRO',
  'PROFESSOR',
  'ALUNO',
]

export async function autenticar(login: string, senha: string): Promise<LoginResponse> {
  const resposta = await apiRequest<LoginResponse>('/login', {
    method: 'POST',
    body: JSON.stringify({ login: login.trim(), senha }),
  })

  salvarSessao(resposta, login.trim())
  return resposta
}

// Primeiro acesso: troca a senha provisória pela definitiva e já abre a
// sessão, sem pedir um segundo login.
export async function definirSenhaDefinitiva(
  login: string,
  senhaProvisoria: string,
  novaSenha: string,
): Promise<LoginResponse> {
  const resposta = await apiRequest<LoginResponse>('/primeiro-acesso', {
    method: 'POST',
    body: JSON.stringify({ login: login.trim(), senhaProvisoria, novaSenha }),
  })

  salvarSessao(resposta, login.trim())
  return resposta
}

// Segunda etapa do login: troca o código recebido por e-mail pela sessão.
export async function confirmarCodigo(
  verificacaoId: string,
  codigo: string,
  login: string,
): Promise<LoginResponse> {
  const resposta = await apiRequest<LoginResponse>('/login/codigo', {
    method: 'POST',
    body: JSON.stringify({ verificacaoId, codigo }),
  })

  salvarSessao(resposta, login.trim())
  return resposta
}

// Pede um código novo para a mesma verificação; o anterior deixa de valer.
export function reenviarCodigo(verificacaoId: string): Promise<VerificacaoLogin> {
  return apiRequest<VerificacaoLogin>('/login/codigo/reenvio', {
    method: 'POST',
    body: JSON.stringify({ verificacaoId }),
  })
}

export type RecuperacaoSenha = {
  /** Segundos, a partir da resposta, que o código enviado vale. */
  validadeSegundos: number
  /** Segundos, a partir da resposta, até poder pedir outro código. */
  reenvioSegundos: number
}

// "Esqueceu sua senha?": o backend manda um código para o e-mail do cadastro.
// A resposta é a mesma para qualquer login, exista ou não.
export function solicitarRecuperacaoSenha(login: string): Promise<RecuperacaoSenha> {
  return apiRequest<RecuperacaoSenha>('/senha/recuperacao', {
    method: 'POST',
    body: JSON.stringify({ login: login.trim() }),
  })
}

// Define a senha nova com o código recebido. Não abre sessão: depois disso o
// usuário entra pelo login normal, com a senha nova.
export async function redefinirSenha(login: string, codigo: string, novaSenha: string): Promise<void> {
  await apiRequest<void>('/senha/redefinicao', {
    method: 'POST',
    body: JSON.stringify({ login: login.trim(), codigo, novaSenha }),
  })
}

export function salvarSessao(dados: LoginResponse, login: string): void {
  // Troca de senha ou código pendente: o backend não emitiu token, não há o
  // que guardar.
  if (!dados.accessToken) return

  try {
    localStorage.setItem(ACCESS_TOKEN_KEY, dados.accessToken)
    // Só o login (e-mail) é guardado à parte: id e perfil não vêm daqui, são
    // sempre lidos das claims do token em obterUsuarioLogado.
    localStorage.setItem(USUARIO_KEY, JSON.stringify({ login }))
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

// Decodifica o payload de um JWT sem verificar assinatura: só é usado para
// decidir navegação no cliente. Quem garante o acesso de fato é o backend,
// que responde 403/401 para qualquer rota fora do perfil do token.
function decodificarPayloadToken(token: string): Record<string, unknown> | null {
  const partes = token.split('.')
  if (partes.length !== 3) return null

  try {
    const base64 = partes[1].replace(/-/g, '+').replace(/_/g, '/')
    const preenchido = base64.padEnd(base64.length + ((4 - (base64.length % 4)) % 4), '=')
    const json = decodeURIComponent(
      atob(preenchido)
        .split('')
        .map((char) => '%' + char.charCodeAt(0).toString(16).padStart(2, '0'))
        .join(''),
    )
    return JSON.parse(json) as Record<string, unknown>
  } catch {
    return null
  }
}

function extrairClaimsToken(token: string): { id: string; perfil: PerfilUsuario } | null {
  const payload = decodificarPayloadToken(token)
  const id = payload?.sub
  const perfil = payload?.perfil
  if (typeof id !== 'string' || typeof perfil !== 'string') return null
  if (!PERFIS_VALIDOS.includes(perfil as PerfilUsuario)) return null

  return { id, perfil: perfil as PerfilUsuario }
}

export function obterUsuarioLogado(): UsuarioSessao | null {
  try {
    const token = obterToken()
    if (!token) return null

    const claims = extrairClaimsToken(token)
    if (!claims) return null

    const raw = localStorage.getItem(USUARIO_KEY)
    const { login } = raw ? (JSON.parse(raw) as { login: string }) : { login: '' }

    return { id: claims.id, perfil: claims.perfil, login }
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

