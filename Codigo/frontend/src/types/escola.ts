// Valores do enum PlanoEscola do backend.
export const PLANOS_ESCOLA = ['BASICO', 'AVANCADO', 'ENTERPRISE'] as const

export type PlanoEscola = (typeof PLANOS_ESCOLA)[number]

export const ROTULOS_PLANO: Record<PlanoEscola, string> = {
  BASICO: 'Básico',
  AVANCADO: 'Avançado',
  ENTERPRISE: 'Enterprise',
}

// Espelha o EscolaResponse do backend.
export type Escola = {
  id: string
  nome: string
  cnpjCpf: string
  endereco: string
  email: string
  telefone: string
  ativo: boolean
  criadoEm: string
  // Nulo em escolas cadastradas antes do plano existir.
  plano: PlanoEscola | null
}

// Espelha o EscolaRequest do backend (POST /escolas).
export type EscolaRequest = {
  nome: string
  /** Só dígitos, como no DER (CHAR(14)). */
  cnpjCpf: string
  endereco?: string
  email: string
  telefone?: string
  ativo: boolean
  plano: PlanoEscola
  // Previsto no DER (ESCOLA.cep), mas a API ainda não persiste: é ignorado
  // pelo backend até a coluna existir.
  cep?: string
}
