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
  },
  master: {
    raiz: '/master',
  },
  secretaria: {
    raiz: '/secretaria',
    professores: '/secretaria/professores',
    /** Alocação de professores nas turmas; por enquanto a única tela do Acadêmico. */
    alocacoes: '/secretaria/academico/alocacoes',
  },
  financeiro: {
    raiz: '/financeiro',
  },
  professor: {
    raiz: '/professor',
  },
  aluno: {
    raiz: '/aluno',
  },
} as const

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
