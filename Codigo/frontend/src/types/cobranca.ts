// Espelha o StatusCobranca do backend. PENDENTE vencida já chega como EM_ATRASO.
export type StatusCobranca = 'PENDENTE' | 'EM_ATRASO' | 'PAGA' | 'CANCELADA'

/** Pagamento de uma cobrança paga: online (provedor) ou baixa manual da escola. */
export type PagamentoDaCobranca = {
  origem: 'ONLINE' | 'MANUAL'
  valorPago: number
  /** Quanto do pago foi devolvido; a cobrança continua PAGA. */
  valorEstornado: number
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
  /** Paga no provedor depois de cancelada no Eduka: a escola precisa resolver com o pagador. */
  pagaAposCancelamento: boolean
  /** Forma escolhida no Pagar; nula antes de o aluno clicar em Pagar. */
  formaPagamento: 'PIX' | 'BOLETO' | null
  /** Quando o aluno clicou em Pagar pela primeira vez. */
  pagamentoIniciadoEm: string | null
  /** Em aberto e antes do Pagar: a escola ainda pode editar. */
  editavel: boolean
  pagamento: PagamentoDaCobranca | null
  criadoEm: string
  atualizadoEm: string
}

export type FiltroCobranca = 'TODAS' | 'PENDENTE' | 'EM_ATRASO' | 'PAGA' | 'CANCELADA'

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

// Espelha o TipoCobranca do backend.
export type TipoCobranca = 'MENSALIDADE' | 'MATRICULA' | 'MATERIAL' | 'OUTRO'

/** Corpo do POST /cobrancas. A escola vem do token. */
export type NovaCobrancaRequest = {
  alunoId: string
  matriculaId?: string
  responsavelId?: string
  descricao: string
  valor: number
  dataVencimento: string
  tipo: TipoCobranca
}

export const ROTULOS_TIPO_COBRANCA: Record<TipoCobranca, string> = {
  MENSALIDADE: 'Mensalidade',
  MATRICULA: 'Matrícula',
  MATERIAL: 'Material',
  OUTRO: 'Outro',
}

export const ROTULOS_STATUS_COBRANCA: Record<StatusCobranca, string> = {
  PENDENTE: 'Pendente',
  EM_ATRASO: 'Em atraso',
  PAGA: 'Paga',
  CANCELADA: 'Cancelada',
}

/** Corpo do PUT /cobrancas/{id}: substitui tudo, então matrícula e responsável voltam como estavam. */
export type AtualizarCobrancaRequest = {
  matriculaId: string | null
  responsavelId: string | null
  descricao: string
  valor: number
  dataVencimento: string
}
