import type {
  CadastroResponsavel,
  CadastroResponsavelRequest,
  Responsavel,
  ResponsavelRequest,
  VinculoResponsavelRequest,
} from '../../types/pessoas/aluno'
import { apiRequest } from '../api'

// O backend já limita alunos e responsáveis à escola de quem está logado.

export function listarResponsaveisDoAluno(alunoId: string, signal?: AbortSignal): Promise<Responsavel[]> {
  return apiRequest<Responsavel[]>(`/alunos/${alunoId}/responsaveis`, { signal })
}

/**
 * Cadastra e vincula de uma vez. CPF já cadastrado na escola liga o aluno ao
 * responsável existente (irmãos). 409 se ele já está vinculado a este aluno;
 * 400 se o financeiro vier sem CPF válido.
 */
export function incluirResponsavel(alunoId: string, dados: ResponsavelRequest): Promise<Responsavel> {
  return apiRequest<Responsavel>(`/alunos/${alunoId}/responsaveis`, { method: 'POST', body: JSON.stringify(dados) })
}

/** Parentesco e financeiro. Marcar um financeiro tira a marca do anterior; 400 sem CPF válido. */
export function atualizarVinculo(
  alunoId: string,
  responsavelId: string,
  dados: VinculoResponsavelRequest,
): Promise<Responsavel> {
  return apiRequest<Responsavel>(`/alunos/${alunoId}/responsaveis/${responsavelId}`, {
    method: 'PUT',
    body: JSON.stringify(dados),
  })
}

/** O cadastro do responsável continua. 409 se ele tem cobrança em aberto deste aluno. */
export function desvincularResponsavel(alunoId: string, responsavelId: string): Promise<void> {
  return apiRequest<void>(`/alunos/${alunoId}/responsaveis/${responsavelId}`, { method: 'DELETE' })
}

export function buscarResponsavel(id: string, signal?: AbortSignal): Promise<CadastroResponsavel> {
  return apiRequest<CadastroResponsavel>(`/responsaveis/${id}`, { signal })
}

/** Substitui o cadastro inteiro. 409 se o CPF já é de outro responsável da escola. */
export function atualizarResponsavel(id: string, dados: CadastroResponsavelRequest): Promise<CadastroResponsavel> {
  return apiRequest<CadastroResponsavel>(`/responsaveis/${id}`, { method: 'PUT', body: JSON.stringify(dados) })
}
