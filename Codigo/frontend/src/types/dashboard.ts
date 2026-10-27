import type { PerfilUsuario } from '../services/authService'
import type { Turno } from './alocacao'
import type { StatusMatricula } from './matricula'
import type { FormaPagamento, StatusCobrancaAluno } from './pagamentoAluno'
import type { TipoAvaliacao } from './portalAluno'

// Espelha o DashboardEscolaResponse do backend (GET /dashboard/escola).
export type DashboardEscola = {
  pessoas: {
    alunos: number
    /** Alunos com pelo menos uma matrícula ATIVA. */
    alunosAtivos: number
    /** Alunos sem nenhuma matrícula ATIVA. */
    alunosSemTurma: number
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
  /** Já realizadas (antes de hoje) e com aluno ativo sem nota, da mais antiga para a mais recente. */
  avaliacoesPendentes: AvaliacaoResumo[]
}

// Espelha o DashboardFinanceiroResponse.Total.
export type TotalFinanceiro = {
  valor: number
  quantidade: number
}

// Espelha o DashboardFinanceiroResponse.Mes.
export type MesFinanceiro = {
  /** "AAAA-MM". */
  mes: string
  /** Cobranças pagas no mês. */
  entrou: number
  /** Despesas pagas no mês. */
  saiu: number
}

// Espelha o DashboardFinanceiroResponse.CobrancaDaSemana.
export type CobrancaDaSemana = {
  id: string
  alunoId: string
  alunoNome: string | null
  descricao: string
  valor: number
  /** ISO (yyyy-MM-dd). */
  dataVencimento: string
  /** PENDENTE vencida já chega como EM_ATRASO. */
  status: StatusCobrancaAluno
}

export type OrigemPagamento = 'ONLINE' | 'MANUAL'

// Espelha o DashboardFinanceiroResponse.PagamentoRecente.
export type PagamentoRecente = {
  cobrancaId: string
  alunoNome: string | null
  descricao: string
  /** O valor pago no Asaas; na baixa manual, o da cobrança. */
  valor: number
  /** Nula na baixa manual. */
  forma: FormaPagamento | null
  origem: OrigemPagamento
  /** Instante (ISO). */
  pagoEm: string
}

export type OperacaoAuditoria =
  | 'CRIAR'
  | 'EDITAR'
  | 'CANCELAR'
  | 'PAGAR'
  | 'CONFIRMAR_PAGAMENTO'
  | 'PAGO_APOS_CANCELAR'
  | 'ESTORNAR'
  | 'CONTESTAR'
  | 'ATIVAR'
  | 'ALTERAR_SITUACAO'

export type EntidadeAuditada = 'COBRANCA' | 'PAGAMENTO' | 'DESPESA' | 'CONTA_RECEBIMENTO' | 'FATURA_PLATAFORMA'

// Espelha o DashboardFinanceiroResponse.AcaoRecente.
export type AcaoRecente = {
  /** Nulo nas ações que vêm do Asaas (webhook), sem usuário da escola. */
  usuarioId: string | null
  usuarioNome: string | null
  perfil: PerfilUsuario | null
  operacao: OperacaoAuditoria
  entidade: EntidadeAuditada
  registroId: string
  /** Instante (ISO). */
  dataHora: string
}

// Espelha o DashboardFinanceiroResponse do backend (GET /dashboard/financeiro).
export type DashboardFinanceiro = {
  /** Cobranças pagas neste mês, online ou com baixa manual. */
  recebidoNoMes: TotalFinanceiro
  /** Cobranças em aberto (pendentes e em atraso). */
  faltaReceber: TotalFinanceiro
  emAtraso: {
    /** Alunos com pelo menos uma cobrança em atraso. */
    alunos: number
    cobrancas: number
    valor: number
    /** 0 a 100: o em atraso sobre tudo o que já venceu; nulo enquanto nada venceu. */
    inadimplencia: number | null
  }
  /** Despesas a pagar com vencimento até o fim do mês, inclusive as atrasadas. */
  despesasAPagar: {
    valor: number
    quantidade: number
    /** ISO (yyyy-MM-dd), de hoje em diante; nulo se não houver. */
    proximoVencimento: string | null
  }
  /** Os 6 últimos meses, do mais antigo ao atual. */
  ultimosMeses: MesFinanceiro[]
  /** Em atraso e as que vencem até daqui a 6 dias, pelo vencimento; no máximo 10. */
  vencemNaSemana: CobrancaDaSemana[]
  /** Mais recentes primeiro; no máximo 10. */
  ultimosPagamentos: PagamentoRecente[]
  /** Do log de auditoria, mais recentes primeiro; no máximo 10. */
  ultimasAcoes: AcaoRecente[]
}
