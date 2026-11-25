import type { StatusMatricula } from './matricula'

// Espelha o AulaResumoResponse do backend: a aula só com os totais da chamada.
export type AulaResumo = {
  id: string
  turmaId: number
  turmaNome: string
  disciplinaId: number
  disciplinaNome: string
  /** AAAA-MM-DD. */
  data: string
  conteudo: string
  presentes: number
  faltas: number
  criadoEm: string
  atualizadoEm: string | null
}

// Espelha o FrequenciaAlunoResponse do backend.
export type PresencaDoAluno = {
  alunoId: string
  alunoNome: string
  alunoNumeroMatricula: string
  /** Nula se o aluno esteve na aula e já saiu da turma. */
  situacaoMatricula: StatusMatricula | null
  presente: boolean
}

// Espelha o AulaResponse do backend: a aula com a chamada completa.
export type Aula = AulaResumo & { chamada: PresencaDoAluno[] }

// Espelha o AlunoDaChamadaResponse do backend.
export type AlunoDaChamada = {
  alunoId: string
  alunoNome: string
  alunoNumeroMatricula: string
}

// Espelha o ChamadaDoDiaResponse do backend.
export type ChamadaDoDia = {
  data: string
  /** A disciplina tem aula na grade da turma neste dia da semana: só então dá para registrar aula nova. */
  diaDeAula: boolean
  /** Quem entra na chamada deste dia: matrícula ativa, feita até a data. */
  alunos: AlunoDaChamada[]
  /** Aulas já registradas neste dia, da primeira para a última. */
  aulas: AulaResumo[]
}

/** Um mês com aulas no histórico (MesDeAulasResponse): quantas aulas e os totais das chamadas. */
export type MesDeAulas = {
  /** "AAAA-MM". */
  mes: string
  aulas: number
  presentes: number
  faltas: number
}

/** Filtro do histórico de aulas: datas (inclusive) e trecho do conteúdo; o que faltar não limita. */
export type FiltroDeAulas = { de?: string; ate?: string; busca?: string }

// Espelha o ChamadaItem do backend.
export type ItemChamada = {
  alunoId: string
  presente: boolean
}

// Espelha o AtualizarAulaRequest do backend: turma, disciplina e data não mudam.
export type DadosAula = {
  conteudo: string
  chamada: ItemChamada[]
}

// Espelha o AulaRequest do backend.
export type AulaRequest = DadosAula & {
  turmaId: number
  disciplinaId: number
  data: string
}
