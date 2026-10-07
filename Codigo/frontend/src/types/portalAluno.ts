import type { Turno } from './alocacao'
import type { StatusMatricula } from './matricula'

export type TipoAvaliacao = 'PROVA' | 'TRABALHO' | 'TAREFA'

export const ROTULOS_TIPO_AVALIACAO: Record<TipoAvaliacao, string> = {
  PROVA: 'Prova',
  TRABALHO: 'Trabalho',
  TAREFA: 'Tarefa',
}

// Espelha o TurmaDoAlunoResponse do backend (GET /portal/aluno/turmas).
export type TurmaDoAluno = {
  matriculaId: string
  situacaoMatricula: StatusMatricula
  /** ISO (yyyy-MM-dd). */
  dataMatricula: string
  turmaId: number
  turmaNome: string
  turmaCodigo: string | null
  turno: Turno
  periodoLetivoNome: string
  cursoNome: string
  disciplinas: {
    id: number
    nome: string
    codigo: string | null
    cargaHoraria: number
    /** Nulo enquanto a secretaria não alocou professor. */
    professorNome: string | null
  }[]
}

// Espelha o AvaliacaoDoAlunoResponse (agenda e itens do boletim).
export type AvaliacaoDoAluno = {
  id: string
  turmaId: number
  turmaNome: string
  disciplinaId: number
  disciplinaNome: string
  tipo: TipoAvaliacao
  titulo: string
  descricao: string | null
  /** ISO (yyyy-MM-dd). */
  data: string
  /** Pontuação máxima. */
  valor: number
  /** Nula enquanto o professor não lançou. */
  nota: number | null
  observacao: string | null
}

export type BoletimDisciplina = {
  disciplinaId: number
  disciplinaNome: string
  professorNome: string | null
  avaliacoes: AvaliacaoDoAluno[]
  /** Soma das notas lançadas. */
  pontosObtidos: number
  /** Soma do valor das avaliações que já têm nota. */
  pontosAvaliados: number
  /** Soma do valor de todas as avaliações. */
  pontosDistribuidos: number
}

// Espelha o BoletimTurmaResponse (GET /portal/aluno/boletim).
export type BoletimTurma = {
  turmaId: number
  turmaNome: string
  periodoLetivoNome: string
  cursoNome: string
  situacaoMatricula: StatusMatricula
  disciplinas: BoletimDisciplina[]
}
