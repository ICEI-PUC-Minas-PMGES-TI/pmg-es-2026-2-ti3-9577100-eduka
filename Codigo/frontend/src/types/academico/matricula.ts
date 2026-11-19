import type { Turno } from './alocacao'

// Valores do enum StatusMatricula do backend. Só ATIVA ocupa vaga da turma.
export type StatusMatricula = 'ATIVA' | 'TRANCADA' | 'CANCELADA' | 'CONCLUIDA'

export const ROTULOS_STATUS_MATRICULA: Record<StatusMatricula, string> = {
  ATIVA: 'Ativa',
  TRANCADA: 'Trancada',
  CANCELADA: 'Cancelada',
  CONCLUIDA: 'Concluída',
}

/** Cor do badge de cada situação. */
export const TOM_DO_STATUS: Record<StatusMatricula, 'success' | 'danger' | 'neutral'> = {
  ATIVA: 'success',
  TRANCADA: 'neutral',
  CANCELADA: 'danger',
  CONCLUIDA: 'neutral',
}

/** CANCELADA e CONCLUIDA são finais: o backend recusa qualquer mudança de situação. */
export function matriculaEncerrada(status: StatusMatricula): boolean {
  return status === 'CANCELADA' || status === 'CONCLUIDA'
}

// Espelha o MatriculaTurmaResponse do backend (GET/POST/PATCH /matriculas).
export type Matricula = {
  id: string
  alunoId: string
  alunoNome: string | null
  /** Número de matrícula do aluno na escola (não o id desta matrícula). */
  alunoNumeroMatricula: string
  turmaId: number
  turmaNome: string
  turno: Turno
  periodoLetivoId: number
  periodoLetivoNome: string
  cursoId: number
  cursoNome: string
  status: StatusMatricula
  /** ISO (AAAA-MM-DD). */
  dataMatricula: string
  observacao: string | null
  criadoEm: string
  atualizadoEm: string | null
}

/**
 * Resumo da página de GET /matriculas/pagina: a turma (ou o aluno) inteira,
 * sem o filtro de situação.
 */
export type ResumoMatriculas = {
  total: number
  porSituacao: Record<StatusMatricula, number>
  /** Só na consulta por turma: alunos ainda sem registro nela, sem os anonimizados e os inativos. */
  paraMatricular: number | null
}

// Espelha o MatriculaTurmaRequest. Sem escola: o backend usa a do token.
export type MatriculaRequest = {
  alunoId: string
  turmaId: number
  /** Sem data, o backend usa a de hoje. */
  dataMatricula: string | null
  observacao: string | null
}

// Espelha o AtualizarMatriculaRequest: só o que vier preenchido muda.
export type AtualizarMatriculaRequest = {
  status?: StatusMatricula
  /** Transferência para outra turma; só para matrícula que fica ATIVA. */
  turmaId?: number
  /** String vazia apaga a observação. */
  observacao?: string
}
