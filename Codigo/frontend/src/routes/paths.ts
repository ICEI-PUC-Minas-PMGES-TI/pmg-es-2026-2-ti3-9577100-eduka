// Caminhos da aplicação, agrupados por papel. Use sempre estas constantes em
// vez de strings soltas, para que renomear uma rota seja uma mudança só.
export const paths = {
  login: '/login',
  superAdmin: {
    raiz: '/super-admin',
    login: '/super-admin/login',
    escolas: '/super-admin/escolas',
  },
} as const
