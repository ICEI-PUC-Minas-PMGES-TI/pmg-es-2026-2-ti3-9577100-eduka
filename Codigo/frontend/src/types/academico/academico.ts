import { ROTULOS_TURNO, type Turno } from './alocacao'

// Estrutura acadêmica da escola (GET/POST /academico/secretaria/...). Os tipos
// espelham os *Response e *Request do backend; a escola nunca vai no corpo,
// o backend usa a do token.

export type PeriodoLetivo = {
  id: number
  nome: string
  /** ISO (AAAA-MM-DD). */
  dataInicio: string
  dataFim: string
  ativo: boolean
  /** Período planejado só recebe matrícula com isto verdadeiro (IU09). */
  matriculasAbertas: boolean
  /** ISO (AAAA-MM-DD); preenchido quando a secretaria encerrou o período. */
  encerradoEm: string | null
}

export type PeriodoLetivoRequest = {
  nome: string
  dataInicio: string
  dataFim: string
}

export type Curso = {
  id: number
  nome: string
  codigo: string | null
  descricao: string | null
  ativo: boolean
}

export type CursoRequest = {
  nome: string
  codigo: string | null
  descricao: string | null
}

export type Disciplina = {
  id: number
  /** Nulo quando a disciplina não é ligada a um curso (ex.: comum a todos). */
  cursoId: number | null
  cursoNome: string | null
  nome: string
  codigo: string | null
  cargaHoraria: number
  descricao: string | null
  ativa: boolean
}

export type DisciplinaRequest = {
  cursoId: number | null
  nome: string
  codigo: string | null
  cargaHoraria: number
  descricao: string | null
}

export type TurmaAcademica = {
  id: number
  periodoLetivoId: number
  periodoLetivoNome: string
  cursoId: number
  cursoNome: string
  nome: string
  codigo: string | null
  vagas: number
  turno: Turno
  ativa: boolean
  /** Grade da turma. */
  disciplinas: Disciplina[]
  /** Vagas ocupadas: só matrícula ativa conta. */
  matriculasAtivas: number
}

export type TurmaRequest = {
  periodoLetivoId: number
  cursoId: number
  nome: string
  codigo: string | null
  vagas: number
  turno: Turno
  disciplinaIds: number[]
}

export const OPCOES_TURNO = (Object.keys(ROTULOS_TURNO) as Turno[]).map((turno) => ({
  valor: turno,
  rotulo: ROTULOS_TURNO[turno],
}))
