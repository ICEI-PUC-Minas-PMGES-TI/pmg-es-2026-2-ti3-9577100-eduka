// Valores do enum Turno do backend.
export type Turno = 'MATUTINO' | 'VESPERTINO' | 'NOTURNO' | 'INTEGRAL'

export const ROTULOS_TURNO: Record<Turno, string> = {
  MATUTINO: 'Manhã',
  VESPERTINO: 'Tarde',
  NOTURNO: 'Noite',
  INTEGRAL: 'Integral',
}

// Espelha o TurmaDoProfessorResponse.DisciplinaDoProfessor do backend.
export type DisciplinaDoProfessor = {
  id: number
  nome: string
  codigo: string | null
  /** Em horas. */
  cargaHoraria: number
}

// Espelha o TurmaDoProfessorResponse do backend: a turma vem só com as
// disciplinas que o professor logado leciona nela, não a grade inteira.
export type TurmaDoProfessor = {
  id: number
  nome: string
  codigo: string | null
  turno: Turno
  ativa: boolean
  periodoLetivoId: number
  periodoLetivoNome: string
  cursoId: number
  cursoNome: string
  disciplinas: DisciplinaDoProfessor[]
}
