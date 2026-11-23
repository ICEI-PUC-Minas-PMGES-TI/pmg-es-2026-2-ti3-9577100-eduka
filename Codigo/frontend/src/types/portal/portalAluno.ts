import type { Turno } from '../academico/alocacao'
import type { StatusMatricula } from '../academico/matricula'

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
  /** Bimestre (1 a 4) pela data, no período letivo da turma; nulo se o período não tem datas. */
  bimestre: number | null
}

// Espelha o BoletimTurmaResponse.Frequencia. Fica nula ("sem aulas") enquanto
// não há aula com chamada: nunca é 0% nem 100%.
export type FrequenciaBoletim = {
  /** Aulas com chamada em que o aluno consta. */
  totalAulas: number
  faltas: number
  /** Presença em %, com uma casa decimal. */
  percentualFrequencia: number
  /** Faltas acima do limite (`limiteFaltasPercentual` da turma) das aulas dadas. */
  alertaFaltas: boolean
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
  /** Nula quando a disciplina ainda não teve aula com chamada. */
  frequencia: FrequenciaBoletim | null
  /** Os mesmos totais e a frequência em cada bimestre da turma, na ordem. */
  bimestres: BimestreDaDisciplina[]
}

// Espelha o BoletimTurmaResponse.BimestreDaDisciplina.
export type BimestreDaDisciplina = {
  numero: number
  pontosObtidos: number
  pontosAvaliados: number
  pontosDistribuidos: number
  /** Nula sem aula com chamada no bimestre. */
  frequencia: FrequenciaBoletim | null
}

// Espelha o BoletimTurmaResponse.BimestreDaTurma.
export type BimestreDaTurma = {
  /** 1 a 4 no ano letivo: o 1º semestre tem o 1º e o 2º; o 2º semestre, o 3º e o 4º. */
  numero: number
  /** ISO (yyyy-MM-dd), inclusivo. */
  inicio: string
  /** ISO (yyyy-MM-dd), inclusivo. */
  fim: string
  /** Frequência da turma toda no bimestre; nula sem aula com chamada. */
  frequencia: FrequenciaBoletim | null
}

// Espelha o BoletimTurmaResponse (GET /portal/aluno/boletim).
export type BoletimTurma = {
  turmaId: number
  turmaNome: string
  periodoLetivoNome: string
  cursoNome: string
  situacaoMatricula: StatusMatricula
  disciplinas: BoletimDisciplina[]
  /** Frequência da turma toda (soma das disciplinas); nula se nenhuma teve aula. */
  frequencia: FrequenciaBoletim | null
  /** Limite de faltas (% das aulas dadas) que dispara o alerta; vem do backend para a tela não repetir o número. */
  limiteFaltasPercentual: number
  /** Ano letivo (do início do período); nulo se o período não tem datas. */
  ano: number | null
  /** ISO (yyyy-MM-dd). */
  periodoLetivoInicio: string | null
  /** ISO (yyyy-MM-dd). */
  periodoLetivoFim: string | null
  /** Bimestres do período; vazio se o período não tem datas. */
  bimestres: BimestreDaTurma[]
}
