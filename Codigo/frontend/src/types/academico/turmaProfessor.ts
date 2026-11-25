import type { HorarioDaDisciplina } from './horario'
import type { StatusMatricula } from './matricula'

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
  /** Aulas da disciplina na grade da turma (IU13); vazia sem horário montado pela secretaria. */
  horarios: HorarioDaDisciplina[]
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
  /** Alunos com matrícula ativa na turma. */
  alunosAtivos: number
  disciplinas: DisciplinaDoProfessor[]
}

/** Aluno da turma no Ver alunos do professor (IU13). Espelha o AlunoDaTurmaResponse. */
export type AlunoDaTurma = {
  alunoId: string
  nome: string | null
  numeroMatricula: string
  situacao: StatusMatricula
}
