// Espelha o SituacaoDespesa do backend.
export type SituacaoDespesa = 'A_PAGAR' | 'PAGA' | 'CANCELADA'

export type FiltroSituacaoDespesa = 'TODAS' | 'A_PAGAR' | 'PAGAS'

// Espelha o CategoriaDespesaResponse do backend (GET /categorias-despesa).
export type CategoriaDespesa = {
  id: string
  nome: string
}

// Espelha o DespesaResponse do backend (GET /despesas).
export type Despesa = {
  id: string
  categoriaId: string
  categoriaNome: string
  descricao: string
  valor: number
  dataVencimento: string // YYYY-MM-DD
  situacao: SituacaoDespesa
  pagaEm: string | null
  canceladaEm: string | null
  criadoEm: string
  atualizadoEm: string
}

/** Corpo do POST /despesas. `paga: true` já lança como PAGA. */
export type NovaDespesaRequest = {
  descricao: string
  categoriaId: string
  valor: number
  dataVencimento: string
  paga: boolean
}
