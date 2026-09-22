export type PlanoEscola = 'Básico' | 'Avançado' | 'Enterprise'

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
