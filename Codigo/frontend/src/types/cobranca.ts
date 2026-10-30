// Espelha o StatusCobranca do backend. PENDENTE vencida já chega como EM_ATRASO.
export type StatusCobranca = 'PENDENTE' | 'EM_ATRASO' | 'PAGA' | 'CANCELADA'

/** Pagamento de uma cobrança paga: online (provedor) ou baixa manual da escola. */
export type PagamentoDaCobranca = {
  origem: 'ONLINE' | 'MANUAL'
  valorPago: number
  dataPagamento: string
  forma: string | null
  comprovanteUrl: string | null
}

// Espelha o CobrancaResponse do backend (GET /cobrancas).
export type Cobranca = {
  id: string
  alunoId: string
  alunoNome: string | null
  alunoNumeroMatricula: string | null
  matriculaId: string | null
  turmaId: number | null
  turmaNome: string | null
  responsavelId: string | null
  responsavelNome: string | null
  descricao: string
  valor: number
  dataVencimento: string
  status: StatusCobranca
  canceladaEm: string | null
  pagaEm: string | null
  pagamento: PagamentoDaCobranca | null
  criadoEm: string
  atualizadoEm: string
}

export type FiltroCobranca = 'TODAS' | 'PENDENTE' | 'EM_ATRASO' | 'PAGA'

// Espelha o PaginaCobrancasResponse do backend (GET /cobrancas/pagina).
export type PaginaCobrancas = {
  conteudo: Cobranca[]
  /** Começa em 0. */
  pagina: number
  tamanho: number
  totalElementos: number
  totalPaginas: number
  /** Todas as situações, na ordem do backend, sem os filtros de situação e de nome. */
  totaisPorStatus: { status: StatusCobranca; quantidade: number; valor: number }[]
}

/** Corpo do POST /cobrancas. A escola vem do token. */
export type NovaCobrancaRequest = {
  alunoId: string
  matriculaId?: string
  responsavelId?: string
  descricao: string
  valor: number
  dataVencimento: string
}

export const ROTULOS_STATUS_COBRANCA: Record<StatusCobranca, string> = {
  PENDENTE: 'Pendente',
  EM_ATRASO: 'Em atraso',
  PAGA: 'Paga',
  CANCELADA: 'Cancelada',
}
