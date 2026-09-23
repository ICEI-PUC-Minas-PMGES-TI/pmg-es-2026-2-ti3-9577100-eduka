import type { PerfilUsuario } from '../services/authService'

// Caminhos da aplicação, agrupados por papel. Use sempre estas constantes em
// vez de strings soltas, para que renomear uma rota seja uma mudança só.
// Cada perfil tem uma rota-mãe (raiz); as telas entram como subrotas dela.
export const paths = {
  login: '/login',
  superAdmin: {
    raiz: '/super-admin',
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
