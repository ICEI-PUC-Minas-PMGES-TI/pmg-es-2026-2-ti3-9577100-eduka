import type { StatusMatricula } from './matricula'
import type { TipoAvaliacao } from '../portal/portalAluno'

// Espelha o AvaliacaoResponse do backend (/portal/professor/avaliacoes).
export type AvaliacaoDoProfessor = {
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
  /** Quantos alunos já têm nota lançada. */
  notasLancadas: number
  /** Falso enquanto as notas estão em rascunho: o aluno ainda não as vê. */
  notasPublicadas: boolean
  criadoEm: string
  atualizadoEm: string | null
}

// Espelha o AtualizarAvaliacaoRequest: turma e disciplina não mudam na edição.
export type DadosAvaliacao = {
  tipo: TipoAvaliacao
  titulo: string
  descricao: string | null
  /** ISO (yyyy-MM-dd). */
  data: string
  valor: number
}

// Espelha o AvaliacaoRequest. Sem professor nem escola: os dois vêm do token.
export type AvaliacaoRequest = DadosAvaliacao & {
  turmaId: number
  disciplinaId: number
}

// Espelha o NotaAlunoResponse: uma linha do diário de notas da avaliação.
export type NotaDoAluno = {
  alunoId: string
  alunoNome: string
  alunoNumeroMatricula: string
  /** Nula se o aluno tem nota mas já não está na turma (transferido). */
  situacaoMatricula: StatusMatricula | null
  /** Nula enquanto não foi lançada. */
  valor: number | null
  observacao: string | null
}

// Espelha o LancarNotasRequest.NotaLancamento. Valor nulo apaga a nota.
export type LancamentoNota = {
  alunoId: string
  valor: number | null
  observacao: string | null
}
