/** Perfis que o MASTER cadastra na equipe da escola. */
export type PerfilEquipe = 'SECRETARIA' | 'FINANCEIRO'

export const ROTULOS_PERFIL_EQUIPE: Record<PerfilEquipe, string> = {
  SECRETARIA: 'Secretaria',
  FINANCEIRO: 'Financeiro',
}

/** O que cada perfil faz, para o MASTER escolher no cadastro. */
export const DESCRICOES_PERFIL_EQUIPE: Record<PerfilEquipe, string> = {
  SECRETARIA: 'Cadastra alunos, professores, turmas e matrículas.',
  FINANCEIRO: 'Lança cobranças e despesas e acompanha os pagamentos.',
}

/** Alguém da equipe (GET /usuarios/equipe). A listagem não traz o CPF. */
export type MembroEquipe = {
  id: string
  perfil: PerfilEquipe
  nome: string
  cpf: string | null
  email: string | null
  telefone: string | null
  login: string
  ativo: boolean
  /** Ainda não trocou a senha provisória: dá para reenviar o e-mail de acesso. */
  aguardandoPrimeiroAcesso: boolean
}

/** Cadastro: o e-mail vira o login e recebe o primeiro acesso. */
export type MembroEquipeRequest = {
  perfil: PerfilEquipe
  nome: string
  cpf: string
  email: string
  telefone: string | null
}

/** Edição: o perfil não muda. */
export type MembroEquipeUpdateRequest = Omit<MembroEquipeRequest, 'perfil'>
