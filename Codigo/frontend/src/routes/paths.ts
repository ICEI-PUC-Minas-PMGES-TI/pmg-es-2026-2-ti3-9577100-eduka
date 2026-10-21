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
  superAdmin: {
    raiz: '/super-admin',
    /** Atalho antigo: redireciona para loginAdmin. */
    login: '/super-admin/login',
    escolas: '/super-admin/escolas',
    /** Use com generatePath(paths.superAdmin.editarEscola, { id }). */
    editarEscola: '/super-admin/escolas/:id/editar',
    /** Faturas que a escola paga ao Eduka. Use com generatePath(paths.superAdmin.faturasEscola, { id }). */
    faturasEscola: '/super-admin/escolas/:id/faturas',
  },
  master: {
    raiz: '/master',
    /** Dashboard da escola. */
    inicio: '/master/inicio',
    /** Faturas do Eduka (pagar e comprovante); a ativação da conta entra na PO.5. */
    recebimentos: '/master/recebimentos',
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
    cobrancas: '/financeiro/cobrancas',
    novaCobranca: '/financeiro/cobrancas/nova',
    despesas: '/financeiro/despesas',
  },
  professor: {
    raiz: '/professor',
    /** Dashboard: turmas, alunos, próximas avaliações e notas a lançar. */
    inicio: '/professor/inicio',
    turmas: '/professor/turmas',
    /** Endereço antigo: redireciona para `notas`, que juntou as duas telas. */
    avaliacoes: '/professor/avaliacoes',
    /** Avaliações e notas de uma turma/disciplina (?turma=&disciplina=&avaliacao=). */
    notas: '/professor/notas',
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

// Para onde cada perfil vai depois do login, ou quando tenta abrir a área de
// outro perfil.
export const rotaInicialPorPerfil: Record<PerfilUsuario, string> = {
  SUPER_ADMIN: paths.superAdmin.escolas,
  MASTER: paths.master.raiz,
  SECRETARIA: paths.secretaria.raiz,
  FINANCEIRO: paths.financeiro.cobrancas,
  PROFESSOR: paths.professor.raiz,
  ALUNO: paths.aluno.raiz,
}
