import type { PaginaDoBackend } from '../../hooks/usePaginacao'
import type { Aula, AulaRequest, AulaResumo, ChamadaDoDia, DadosAula } from '../../types/academico/aula'
import type {
  AvaliacaoDoProfessor,
  AvaliacaoRequest,
  DadosAvaliacao,
  LancamentoNota,
  NotaDoAluno,
} from '../../types/academico/avaliacaoProfessor'
import type { DashboardProfessor } from '../../types/dashboard/dashboard'
import type { AlunoDaTurma, TurmaDoProfessor } from '../../types/academico/turmaProfessor'
import { apiRequest } from '../api'

const AVALIACOES = '/portal/professor/avaliacoes'
const AULAS = '/portal/professor/aulas'

// Professor e escola saem do token: não há como pedir as turmas de outro professor.
export function listarMinhasTurmas(signal?: AbortSignal): Promise<TurmaDoProfessor[]> {
  return apiRequest<TurmaDoProfessor[]>('/portal/professor/turmas', { signal })
}

/** Turmas, alunos, próximas avaliações e notas pendentes. 404 se o login não é de um professor. */
/** Alunos da turma, ativos e trancados (Ver alunos, IU13). 404 se o professor não leciona nela. */
export function listarAlunosDaTurma(turmaId: number, signal?: AbortSignal): Promise<AlunoDaTurma[]> {
  return apiRequest<AlunoDaTurma[]>(`/portal/professor/turmas/${turmaId}/alunos`, { signal })
}

export function obterDashboardProfessor(signal?: AbortSignal): Promise<DashboardProfessor> {
  return apiRequest<DashboardProfessor>('/portal/professor/dashboard', { signal })
}

/** Avaliações de uma disciplina na turma, por data. 404 se o professor não está alocado nela. */
export function listarAvaliacoes(
  turmaId: number,
  disciplinaId: number,
  signal?: AbortSignal,
): Promise<AvaliacaoDoProfessor[]> {
  const params = new URLSearchParams({ turmaId: String(turmaId), disciplinaId: String(disciplinaId) })
  return apiRequest<AvaliacaoDoProfessor[]>(`${AVALIACOES}?${params}`, { signal })
}

export function cadastrarAvaliacao(dados: AvaliacaoRequest): Promise<AvaliacaoDoProfessor> {
  return apiRequest<AvaliacaoDoProfessor>(AVALIACOES, { method: 'POST', body: JSON.stringify(dados) })
}

/** 400 se o novo valor fica abaixo de uma nota já lançada. */
export function atualizarAvaliacao(id: string, dados: DadosAvaliacao): Promise<AvaliacaoDoProfessor> {
  return apiRequest<AvaliacaoDoProfessor>(`${AVALIACOES}/${id}`, { method: 'PUT', body: JSON.stringify(dados) })
}

/** 409 se a avaliação já tem nota lançada. */
export function excluirAvaliacao(id: string): Promise<void> {
  return apiRequest<void>(`${AVALIACOES}/${id}`, { method: 'DELETE' })
}

/** Alunos ativos da turma (e os que já saíram mas têm nota), com a nota de cada um. */
export function listarNotas(avaliacaoId: string, signal?: AbortSignal): Promise<NotaDoAluno[]> {
  return apiRequest<NotaDoAluno[]>(`${AVALIACOES}/${avaliacaoId}/notas`, { signal })
}

/**
 * Lança em lote, tudo ou nada; só os alunos enviados mudam. Em rascunho as
 * notas ficam gravadas sem aparecer para o aluno; sem ele, publica (IU14).
 */
export function lancarNotas(avaliacaoId: string, notas: LancamentoNota[], rascunho = false): Promise<NotaDoAluno[]> {
  return apiRequest<NotaDoAluno[]>(`${AVALIACOES}/${avaliacaoId}/notas`, {
    method: 'PUT',
    body: JSON.stringify({ notas, rascunho }),
  })
}

/** Quem entra na chamada do dia e as aulas já registradas nele. 400 se a data é futura; 404 se o professor não está alocado. */
export function obterChamadaDoDia(
  turmaId: number,
  disciplinaId: number,
  data: string,
  signal?: AbortSignal,
): Promise<ChamadaDoDia> {
  const params = new URLSearchParams({ turmaId: String(turmaId), disciplinaId: String(disciplinaId), data })
  return apiRequest<ChamadaDoDia>(`${AULAS}/chamada?${params}`, { signal })
}

/** Histórico de aulas da disciplina na turma, da mais recente para a mais antiga. */
export function listarAulas(
  turmaId: number,
  disciplinaId: number,
  pagina: number,
  tamanho: number,
  signal?: AbortSignal,
): Promise<PaginaDoBackend<AulaResumo>> {
  const params = new URLSearchParams({
    turmaId: String(turmaId),
    disciplinaId: String(disciplinaId),
    pagina: String(pagina),
    tamanho: String(tamanho),
  })
  return apiRequest<PaginaDoBackend<AulaResumo>>(`${AULAS}?${params}`, { signal })
}

export function buscarAula(id: string, signal?: AbortSignal): Promise<Aula> {
  return apiRequest<Aula>(`${AULAS}/${id}`, { signal })
}

/** Tudo ou nada; 400 se a data é futura ou a chamada não traz todos os alunos do dia. */
export function registrarAula(dados: AulaRequest): Promise<Aula> {
  return apiRequest<Aula>(AULAS, { method: 'POST', body: JSON.stringify(dados) })
}

/** Corrige o conteúdo e a chamada; turma, disciplina e data não mudam. */
export function atualizarAula(id: string, dados: DadosAula): Promise<Aula> {
  return apiRequest<Aula>(`${AULAS}/${id}`, { method: 'PUT', body: JSON.stringify(dados) })
}

/** Apaga a aula e a chamada dela: é como se desfaz uma data errada. */
export function excluirAula(id: string): Promise<void> {
  return apiRequest<void>(`${AULAS}/${id}`, { method: 'DELETE' })
}
