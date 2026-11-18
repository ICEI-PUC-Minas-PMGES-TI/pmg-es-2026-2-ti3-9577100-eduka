import { apiRequest } from '../api'
import { salvarSessao, type LoginResponse, type PerfilUsuario } from './authService'

// Acesso de demonstração (DemoController no backend, só no perfil "demo").
// Login e senha são os do acesso, não os da conta: o backend confere e emite
// o token da conta escolhida.

export type ContaDemo = { perfil: PerfilUsuario; rotulo: string; login: string }

export type GrupoDemo = { titulo: string; descricao: string; contas: ContaDemo[] }

export function listarContasDemo(login: string, senha: string): Promise<GrupoDemo[]> {
  return apiRequest<GrupoDemo[]>('/demo/contas', {
    method: 'POST',
    body: JSON.stringify({ login: login.trim(), senha }),
  })
}

export async function entrarComoDemo(login: string, senha: string, conta: string): Promise<LoginResponse> {
  const resposta = await apiRequest<LoginResponse>('/demo/entrar', {
    method: 'POST',
    body: JSON.stringify({ login: login.trim(), senha, conta }),
  })

  salvarSessao(resposta, conta)
  return resposta
}
