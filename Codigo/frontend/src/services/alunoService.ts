import type { Aluno, AlunoRequest } from '../types/aluno'
import { apiRequest } from './api'

// O backend já limita os alunos à escola de quem está logado. A listagem
// (resumo) fica em matriculaService.listarAlunos.

export function buscarAluno(id: string, signal?: AbortSignal): Promise<Aluno> {
  return apiRequest<Aluno>(`/alunos/${id}`, { signal })
}

/** Cria também o login do aluno. 409 se a matrícula ou o e-mail já estão em uso. */
export function cadastrarAluno(dados: AlunoRequest): Promise<Aluno> {
  return apiRequest<Aluno>('/alunos', { method: 'POST', body: JSON.stringify(dados) })
}

/** Substitui os dados do aluno; os responsáveis não mudam. 409 como no cadastro. */
export function atualizarAluno(id: string, dados: AlunoRequest): Promise<Aluno> {
  return apiRequest<Aluno>(`/alunos/${id}`, { method: 'PUT', body: JSON.stringify(dados) })
}

/** Nova senha provisória por e-mail. 409 se o aluno já definiu a senha. */
export function reenviarAcessoAluno(id: string): Promise<void> {
  return apiRequest<void>(`/alunos/${id}/primeiro-acesso`, { method: 'POST' })
}

/** Portal do aluno: os dados do próprio aluno logado. */
export function buscarMeusDados(signal?: AbortSignal): Promise<Aluno> {
  return apiRequest<Aluno>('/portal/aluno/me', { signal })
}
