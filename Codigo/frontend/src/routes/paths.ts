import type { PerfilUsuario } from '../services/authService'

// Caminhos da aplicação, agrupados por papel. Use sempre estas constantes em
// vez de strings soltas, para que renomear uma rota seja uma mudança só.
// Cada perfil tem uma rota-mãe (raiz); as telas entram como subrotas dela.
export const paths = {
  /** Login da escola: master, secretaria, financeiro, professor e aluno. */
  login: '/login',
  /** Login do console da plataforma (SUPER_ADMIN). */
  loginAdmin: '/login/admin',
  /** Só existe em dev (import.meta.env.DEV). */
  loginDev: '/login/dev',
  /** Só existe no build de apresentação (VITE_DEMO=true) e em dev. */
  loginDemo: '/login/demo',
  /** Aviso para quem é de uma escola suspensa (IU04). */
  acessoSuspenso: '/acesso-suspenso',
  superAdmin: {
    raiz: '/super-admin',
    /** Atalho antigo: redireciona para loginAdmin. */
    login: '/super-admin/login',
    escolas: '/super-admin/escolas',
    /** Use com generatePath(paths.superAdmin.editarEscola, { id }). */
    editarEscola: '/super-admin/escolas/:id/editar',
    /** Faturas que a escola paga ao Eduka. Use com generatePath(paths.superAdmin.faturasEscola, { id }). */
    faturasEscola: '/super-admin/escolas/:id/faturas',
    /** Faturas de todas as escolas (IU24, item Assinaturas do menu). */
    assinaturas: '/super-admin/assinaturas',
  },
  master: {
    raiz: '/master',
    /** Dashboard da escola. */
    inicio: '/master/inicio',
    /** Conta de recebimento da escola e faturas do Edúka (pagar e comprovante). */
    recebimentos: '/master/recebimentos',
    /** Plano da escola e faturas do Edúka: pagar e ver o comprovante (IU24-A). */
    planoEFaturas: '/master/plano-e-faturas',
    // Telas da secretaria e do financeiro que o MASTER também usa (IU05,
    // IU20, IU21): mesmas páginas, com o menu da gestão (ver routes/areas.ts).
    alunos: '/master/alunos',
    professores: '/master/professores',
    academico: '/master/academico',
    alocacoes: '/master/academico/alocacoes',
    matriculas: '/master/matriculas',
    cobrancas: '/master/cobrancas',
    novaCobranca: '/master/cobrancas/nova',
    despesas: '/master/despesas',
    relatorios: '/master/relatorios',
  },
  secretaria: {
    raiz: '/secretaria',
    /** Dashboard: resumo de matrículas e ocupação das turmas. */
    inicio: '/secretaria/inicio',
    alunos: '/secretaria/alunos',
    professores: '/secretaria/professores',
    /** Painel acadêmico: períodos letivos, cursos, disciplinas e turmas (aba em ?aba=). */
    academico: '/secretaria/academico',
    /** Alocação de professores nas turmas, dentro do Acadêmico. */
    alocacoes: '/secretaria/academico/alocacoes',
    /** Matrícula de alunos nas turmas. */
    matriculas: '/secretaria/matriculas',
  },
  financeiro: {
    raiz: '/financeiro',
    /** Dashboard: recebido, em aberto, atraso, despesas e movimentações recentes. */
    inicio: '/financeiro/inicio',
    /** Aceita ?status=EM_ATRASO (ou A_PAGAR, PAGA) para abrir já filtrada. */
    cobrancas: '/financeiro/cobrancas',
    novaCobranca: '/financeiro/cobrancas/nova',
    despesas: '/financeiro/despesas',
    /** Relatórios por período e exportação em CSV (IU19). */
    relatorios: '/financeiro/relatorios',
  },
  professor: {
    raiz: '/professor',
    /** Dashboard: turmas, alunos, próximas avaliações e notas a lançar. */
    inicio: '/professor/inicio',
    turmas: '/professor/turmas',
    /** Avaliações e notas de uma turma/disciplina (?turma=&disciplina=&avaliacao=). */
    avaliacoes: '/professor/avaliacoes',
    /** Endereço antigo: redireciona para `avaliacoes`, com os mesmos parâmetros. */
    notas: '/professor/notas',
    /** Aula do dia e histórico de uma turma/disciplina (?turma=&disciplina=&data=&aula=). */
    chamada: '/professor/chamada',
  },
  aluno: {
    raiz: '/aluno',
    boletim: '/aluno/boletim',
    /** Endereço antigo: redireciona para a aba "Tarefas e provas" do boletim. */
    avaliacoes: '/aluno/avaliacoes',
    meusDados: '/aluno/meus-dados',
    /** Cobranças do aluno, com o Pagar por PIX. */
    pagamentos: '/aluno/pagamentos',
  },
} as const

/**
 * Estado de navegação dos atalhos dos dashboards: a tela de destino (Alunos,
 * Professores) já abre com o modal de cadastro aberto.
 */
export type EstadoAbrirCadastro = { abrirCadastro: true }

/** Abre o cadastro de avaliação; com `turmaId`, o modal já vem com essa turma escolhida. */
export type EstadoNovaAvaliacao = EstadoAbrirCadastro & { turmaId?: number }

/** Volta de Nova cobrança para Cobranças com a confirmação do que foi criado. */
export type EstadoCobrancaCriada = { cobrancaCriada: string }

// Para onde cada perfil vai depois do login, ou quando tenta abrir a área de
// outro perfil.
export const rotaInicialPorPerfil: Record<PerfilUsuario, string> = {
  SUPER_ADMIN: paths.superAdmin.escolas,
  MASTER: paths.master.raiz,
  SECRETARIA: paths.secretaria.raiz,
  FINANCEIRO: paths.financeiro.raiz,
  PROFESSOR: paths.professor.raiz,
  ALUNO: paths.aluno.raiz,
}
