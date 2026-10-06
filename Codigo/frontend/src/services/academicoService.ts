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
