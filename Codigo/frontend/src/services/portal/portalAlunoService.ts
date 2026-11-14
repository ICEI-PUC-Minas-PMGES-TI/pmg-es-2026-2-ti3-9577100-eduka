import type { AvaliacaoDoAluno, BoletimTurma, TurmaDoAluno } from '../../types/portal/portalAluno'
import { apiRequest } from '../api'

// Portal do aluno: o backend usa o aluno e a escola do token. Os dados
// cadastrais (GET /portal/aluno/me) ficam em alunoService.buscarMeusDados.

export function listarMinhasTurmas(signal?: AbortSignal): Promise<TurmaDoAluno[]> {
  return apiRequest<TurmaDoAluno[]>('/portal/aluno/turmas', { signal })
}

export function buscarBoletim(signal?: AbortSignal): Promise<BoletimTurma[]> {
  return apiRequest<BoletimTurma[]>('/portal/aluno/boletim', { signal })
}

/** Provas, trabalhos e tarefas das turmas ativas, por data. */
export function listarMinhasAvaliacoes(signal?: AbortSignal): Promise<AvaliacaoDoAluno[]> {
  return apiRequest<AvaliacaoDoAluno[]>('/portal/aluno/avaliacoes', { signal })
}
