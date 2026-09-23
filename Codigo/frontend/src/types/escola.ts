export const PLANOS_ESCOLA = ['Básico', 'Avançado', 'Enterprise'] as const

export type PlanoEscola = (typeof PLANOS_ESCOLA)[number]

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
  // Ainda não existe na API; entra quando o módulo de assinaturas chegar.
  plano?: PlanoEscola
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
  // Previstos no DER (ESCOLA.cep e ESCOLA.plano_id), mas a API ainda não
  // persiste: são ignorados pelo backend até as colunas existirem.
  cep?: string
  plano?: PlanoEscola
}
