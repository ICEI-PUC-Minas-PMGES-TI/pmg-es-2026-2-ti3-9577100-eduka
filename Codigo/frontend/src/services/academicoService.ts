import type {
  Curso,
  CursoRequest,
  Disciplina,
  DisciplinaRequest,
  PeriodoLetivo,
  PeriodoLetivoRequest,
  TurmaAcademica,
  TurmaRequest,
} from '../types/academico'
import { apiRequest } from './api'

// Estrutura acadêmica da escola de quem está logado: o backend filtra e grava
// sempre na escola do token.
const BASE = '/academico/secretaria'

function cadastrar<T>(recurso: string, dados: unknown): Promise<T> {
  return apiRequest<T>(`${BASE}/${recurso}`, { method: 'POST', body: JSON.stringify(dados) })
}

export function listarPeriodosLetivos(signal?: AbortSignal): Promise<PeriodoLetivo[]> {
  return apiRequest<PeriodoLetivo[]>(`${BASE}/periodos-letivos`, { signal })
}

/** O backend recusa com 400 se a data de fim for anterior à de início. */
export function cadastrarPeriodoLetivo(dados: PeriodoLetivoRequest): Promise<PeriodoLetivo> {
  return cadastrar('periodos-letivos', dados)
}

export function listarCursos(signal?: AbortSignal): Promise<Curso[]> {
  return apiRequest<Curso[]>(`${BASE}/cursos`, { signal })
}

export function cadastrarCurso(dados: CursoRequest): Promise<Curso> {
  return cadastrar('cursos', dados)
}

export function listarDisciplinas(signal?: AbortSignal): Promise<Disciplina[]> {
  return apiRequest<Disciplina[]>(`${BASE}/disciplinas`, { signal })
}

export function cadastrarDisciplina(dados: DisciplinaRequest): Promise<Disciplina> {
  return cadastrar('disciplinas', dados)
}

export function listarTurmasAcademicas(signal?: AbortSignal): Promise<TurmaAcademica[]> {
  return apiRequest<TurmaAcademica[]>(`${BASE}/turmas`, { signal })
}

/** 404 se o período, o curso ou alguma disciplina não for da escola. */
export function cadastrarTurma(dados: TurmaRequest): Promise<TurmaAcademica> {
  return cadastrar('turmas', dados)
}

// Edição substitui o registro inteiro. Exclusão responde 409 quando outro
// cadastro depende do registro (ver mensagemErroAcademico).

function atualizar<T>(recurso: string, id: number, dados: unknown): Promise<T> {
  return apiRequest<T>(`${BASE}/${recurso}/${id}`, { method: 'PUT', body: JSON.stringify(dados) })
}

function excluir(recurso: string, id: number): Promise<void> {
  return apiRequest<void>(`${BASE}/${recurso}/${id}`, { method: 'DELETE' })
}

export function atualizarPeriodoLetivo(id: number, dados: PeriodoLetivoRequest): Promise<PeriodoLetivo> {
  return atualizar('periodos-letivos', id, dados)
}

/** Encerra o período em andamento: deixa de receber matrícula nova (IU09). */
export function encerrarPeriodoLetivo(id: number): Promise<PeriodoLetivo> {
  return apiRequest<PeriodoLetivo>(`${BASE}/periodos-letivos/${id}/encerrar`, { method: 'PATCH' })
}

/** Libera as matrículas de um período planejado (IU09). */
export function abrirMatriculasDoPeriodo(id: number): Promise<PeriodoLetivo> {
  return apiRequest<PeriodoLetivo>(`${BASE}/periodos-letivos/${id}/abrir-matriculas`, { method: 'PATCH' })
}

export function excluirPeriodoLetivo(id: number): Promise<void> {
  return excluir('periodos-letivos', id)
}

export function atualizarCurso(id: number, dados: CursoRequest): Promise<Curso> {
  return atualizar('cursos', id, dados)
}

export function excluirCurso(id: number): Promise<void> {
  return excluir('cursos', id)
}

export function atualizarDisciplina(id: number, dados: DisciplinaRequest): Promise<Disciplina> {
  return atualizar('disciplinas', id, dados)
}

export function excluirDisciplina(id: number): Promise<void> {
  return excluir('disciplinas', id)
}

/** 400 se as vagas ficam abaixo das matrículas ativas; 409 se sai da grade disciplina com avaliação. */
export function atualizarTurma(id: number, dados: TurmaRequest): Promise<TurmaAcademica> {
  return atualizar('turmas', id, dados)
}

export function excluirTurma(id: number): Promise<void> {
  return excluir('turmas', id)
}
