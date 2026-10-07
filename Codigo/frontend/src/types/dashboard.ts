import type { Turno } from './alocacao'
import type { StatusMatricula } from './matricula'
import type { TipoAvaliacao } from './portalAluno'

// Espelha o DashboardEscolaResponse do backend (GET /dashboard/escola).
export type DashboardEscola = {
  pessoas: {
    alunos: number
    /** Alunos com pelo menos uma matrícula ATIVA. */
    alunosAtivos: number
    professoresAtivos: number
    professoresInativos: number
    secretarias: number
    financeiro: number
  }
  estrutura: {
    cursos: number
    disciplinas: number
    turmasAtivas: number
    turmas: number
    /** Nulo se nenhum período está em andamento hoje. */
    periodoLetivoAtual: string | null
  }
  /** Vagas das turmas ativas. */
  ocupacao: {
    vagasOfertadas: number
    matriculasAtivas: number
    /** 0 a 100; nulo sem vagas ofertadas. */
    percentual: number | null
  }
  desempenho: {
    avaliacoes: number
    notasLancadas: number
    /** 0 a 100; nulo sem nenhuma nota lançada. */
    aproveitamento: number | null
  }
}

// Espelha o DashboardSecretariaResponse.OcupacaoTurma.
export type OcupacaoTurma = {
  turmaId: number
  turmaNome: string
  periodoLetivoNome: string
  cursoNome: string
  turno: Turno
  vagas: number
  matriculasAtivas: number
}

// Espelha o DashboardSecretariaResponse do backend (GET /dashboard/secretaria).
export type DashboardSecretaria = {
  /** As quatro situações vêm sempre, com zero quando não há nenhuma. */
  matriculasPorSituacao: Record<StatusMatricula, number>
  matriculasUltimos30Dias: number
  /** Alunos cadastrados sem nenhuma matrícula ATIVA. */
  alunosSemTurma: number
  /** Vagas das turmas ativas. */
  vagas: {
    ofertadas: number
    ocupadas: number
    livres: number
    turmasLotadas: number
  }
  /** Turmas ativas, da mais cheia para a mais vazia. */
  turmas: OcupacaoTurma[]
}

// Espelha o DashboardProfessorResponse.AvaliacaoResumo.
export type AvaliacaoResumo = {
  id: string
  tipo: TipoAvaliacao
  titulo: string
  /** ISO (yyyy-MM-dd). */
  data: string
  /** Pontuação máxima. */
  valor: number
  turmaId: number
  turmaNome: string
  disciplinaId: number
  disciplinaNome: string
  /** Alunos ativos da turma ainda sem nota nesta avaliação. */
  notasFaltando: number
}

// Espelha o DashboardProfessorResponse do backend (GET /portal/professor/dashboard).
export type DashboardProfessor = {
  turmas: number
  disciplinas: number
  /** Alunos distintos com matrícula ATIVA nas turmas do professor. */
  alunos: number
  avaliacoes: number
  /** De hoje até 30 dias, por data. */
  proximasAvaliacoes: AvaliacaoResumo[]
  /** Já realizadas e com aluno ativo sem nota, da mais antiga para a mais recente. */
  avaliacoesPendentes: AvaliacaoResumo[]
}
