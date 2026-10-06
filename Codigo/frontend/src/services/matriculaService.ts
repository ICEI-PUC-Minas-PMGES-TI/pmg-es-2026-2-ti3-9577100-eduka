import type { AlunoResumo } from '../types/aluno'
import type { AtualizarMatriculaRequest, Matricula, MatriculaRequest } from '../types/matricula'
import { ApiError, apiRequest } from './api'

// O backend já limita alunos e matrículas à escola de quem está logado.

export function listarAlunos(signal?: AbortSignal): Promise<AlunoResumo[]> {
  return apiRequest<AlunoResumo[]>('/alunos', { signal })
}

export function listarMatriculas(signal?: AbortSignal): Promise<Matricula[]> {
  return apiRequest<Matricula[]>('/matriculas', { signal })
}

/** 409 se o aluno já tem matrícula na turma ou se a turma está cheia. */
export function matricularAluno(dados: MatriculaRequest): Promise<Matricula> {
  return apiRequest<Matricula>('/matriculas', { method: 'POST', body: JSON.stringify(dados) })
}

/** 400 para transição de situação inválida; 409 para turma de destino cheia ou com o aluno. */
export function atualizarMatricula(id: string, dados: AtualizarMatriculaRequest): Promise<Matricula> {
  return apiRequest<Matricula>(`/matriculas/${id}`, { method: 'PATCH', body: JSON.stringify(dados) })
}

/**
 * Mensagem para a secretaria a partir do erro de matrícula. O backend manda
 * o motivo no `detail` (sem acento); aqui vira texto de tela.
 */
export function mensagemErroMatricula(erro: unknown): string {
  if (!(erro instanceof ApiError)) return 'Erro inesperado ao salvar a matrícula.'
  const detalhe = erro.detalhe?.toLowerCase() ?? ''
  if (detalhe.includes('sem vagas')) return 'A turma não tem mais vagas.'
  if (detalhe.includes('ja possui matricula')) return 'O aluno já tem matrícula nesta turma.'
  if (detalhe.includes('turma inativa')) return 'A turma está inativa e não recebe matrícula.'
  if (detalhe.includes('transferida')) return 'Só matrícula ativa pode ser transferida de turma.'
  if (detalhe.includes('nao pode passar')) return 'Essa mudança de situação não é permitida.'
  if (erro.status === 404) return 'O aluno, a turma ou a matrícula não existe mais. Recarregue a página.'
  return erro.message
}
