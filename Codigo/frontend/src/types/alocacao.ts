// Valores do enum Turno do backend.
export type Turno = 'MATUTINO' | 'VESPERTINO' | 'NOTURNO' | 'INTEGRAL'

export const ROTULOS_TURNO: Record<Turno, string> = {
  MATUTINO: 'Matutino',
  VESPERTINO: 'Vespertino',
  NOTURNO: 'Noturno',
  INTEGRAL: 'Integral',
}

// Espelha o DisciplinaResponse do backend, só com o que a tela de alocação usa.
export type DisciplinaDaTurma = {
  id: number
  nome: string
  codigo: string | null
  cargaHoraria: number | null
}

// Espelha o TurmaResponse do backend (GET /academico/secretaria/turmas).
export type Turma = {
  id: number
  nome: string
  codigo: string | null
  turno: Turno
  periodoLetivoId: number
  periodoLetivoNome: string
  cursoNome: string | null
  ativa: boolean
  /** Grade da turma: só estas disciplinas recebem professor. */
  disciplinas: DisciplinaDaTurma[]
}

// Espelha o AlocacaoProfessorResponse do backend: um professor em uma
// disciplina de uma turma. Cada disciplina tem um único professor por turma.
export type Alocacao = {
  id: string
  professorId: string
  professorNome: string
  turmaId: number
  turmaNome: string
  turno: Turno
  periodoLetivoId: number
  periodoLetivoNome: string
  disciplinaId: number
  disciplinaNome: string
}

// Espelha o AlocacaoProfessorRequest do backend. O professor vai na URL.
export type AlocacaoRequest = {
  turmaId: number
  disciplinaId: number
}
