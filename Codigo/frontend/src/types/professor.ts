// Valores do enum VinculoProfessor do backend, na ordem em que aparecem na tela.
export const VINCULOS_PROFESSOR = [
  'CLT_40H',
  'CLT_30H',
  'CLT_20H',
  'HORISTA',
  'TEMPORARIO',
  'PRESTADOR_SERVICO',
] as const

export type VinculoProfessor = (typeof VINCULOS_PROFESSOR)[number]

export const ROTULOS_VINCULO: Record<VinculoProfessor, string> = {
  CLT_40H: 'CLT - 40h',
  CLT_30H: 'CLT - 30h',
  CLT_20H: 'CLT - 20h',
  HORISTA: 'Horista',
  TEMPORARIO: 'Temporário',
  PRESTADOR_SERVICO: 'Prestador de serviço',
}

export const OPCOES_VINCULO = VINCULOS_PROFESSOR.map((vinculo) => ({
  valor: vinculo,
  rotulo: ROTULOS_VINCULO[vinculo],
}))

// Espelha o ProfessorResponse do backend. Na listagem o CPF vem null: ele só
// aparece no detalhe de um professor específico (GET /professores/{id}).
export type Professor = {
  id: string
  escolaId: string
  nome: string
  cpf: string | null
  email: string | null
  telefone: string | null
  formacao: string | null
  /** ISO (yyyy-MM-dd). */
  dataAdmissao: string | null
  /** Nulo nos professores cadastrados antes do campo existir. */
  vinculo: VinculoProfessor | null
  ativo: boolean
}

// Espelha o ProfessorRequest do backend. A escola vem do token, não do corpo.
export type ProfessorRequest = {
  nome: string
  /** Só os 11 dígitos, sem máscara. */
  cpf: string
  /** Vira o login do professor; o acesso inicial é enviado para ele. */
  email: string
  telefone: string | null
  formacao: string | null
  /** ISO (yyyy-MM-dd). */
  dataAdmissao: string | null
  vinculo: VinculoProfessor
}
