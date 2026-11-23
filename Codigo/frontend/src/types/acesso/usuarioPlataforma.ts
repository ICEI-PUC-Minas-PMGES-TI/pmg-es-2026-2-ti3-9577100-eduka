import type { PerfilUsuario } from '../../services/acesso/authService'

/**
 * Um login da plataforma na tela Usuários do Super Admin (S5-14). Espelha o
 * UsuarioPlataformaResponse do backend: sem nome nem CPF, que ficam com a
 * escola; para gerir o acesso basta o login (o e-mail).
 */
export type UsuarioPlataforma = {
  id: string
  login: string
  perfil: PerfilUsuario
  /** Nulos para o Super Admin, que não é de escola. */
  escolaId: string | null
  escolaNome: string | null
  ativo: boolean
  /** Ainda com a senha provisória: não entrou nenhuma vez. */
  aguardandoPrimeiroAcesso: boolean
  /** ISO. Nulo se nunca entrou. */
  ultimoAcessoEm: string | null
  criadoEm: string
  /**
   * O Super Admin pode inativar, reativar e reenviar o acesso: só o do MASTER,
   * que é quem ele cria. Os demais são geridos pela própria escola.
   */
  gerenciavel: boolean
}

export type FiltrosUsuariosPlataforma = {
  escolaId: string
  perfil: PerfilUsuario | ''
  /** 'true' (ativos), 'false' (inativos) ou '' (todos). */
  ativo: '' | 'true' | 'false'
  busca: string
}

/** Perfis na ordem em que aparecem no filtro, com o nome que a escola usa. */
export const ROTULOS_PERFIL_PLATAFORMA: Record<PerfilUsuario, string> = {
  SUPER_ADMIN: 'Super Admin',
  MASTER: 'Gestão (MASTER)',
  SECRETARIA: 'Secretaria',
  FINANCEIRO: 'Financeiro',
  PROFESSOR: 'Professor',
  ALUNO: 'Aluno',
}
