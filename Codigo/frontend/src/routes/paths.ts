// Caminhos da aplicação, agrupados por papel. Use sempre estas constantes em
// vez de strings soltas, para que renomear uma rota seja uma mudança só.
export const paths = {
  superAdmin: {
    raiz: '/super-admin',
    escolas: '/super-admin/escolas',
  },
} as const
