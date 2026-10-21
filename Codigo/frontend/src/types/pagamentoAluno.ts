// Tipos do pagamento no portal do aluno e do histórico financeiro do aluno.
// Espelham os DTOs do backend:
// HistoricoFinanceiroResponse (GET /portal/aluno/financeiro e GET /alunos/{id}/financeiro),
// CobrancaAlunoResponse (GET /portal/aluno/cobrancas/{id}) e
// DadosPagamentoResponse (POST /portal/aluno/cobrancas/{id}/pagar).

/** Situação gravada da cobrança. PENDENTE vencida já chega como EM_ATRASO. */
export type StatusCobrancaAluno = 'PENDENTE' | 'EM_ATRASO' | 'PAGA' | 'CANCELADA'

export const ROTULOS_STATUS_COBRANCA_ALUNO: Record<StatusCobrancaAluno, string> = {
  PENDENTE: 'A vencer',
  EM_ATRASO: 'Em atraso',
  PAGA: 'Paga',
  CANCELADA: 'Cancelada',
}

/** Só a cobrança em aberto pode ser paga. */
export function podePagar(status: StatusCobrancaAluno): boolean {
  return status === 'PENDENTE' || status === 'EM_ATRASO'
}

export type FormaPagamento = 'PIX' | 'BOLETO'

export const ROTULOS_FORMA_PAGAMENTO: Record<FormaPagamento, string> = {
  PIX: 'PIX',
  BOLETO: 'Boleto',
}

/** Pagamento registrado (online). */
export type PagamentoResumo = {
  valorPago: number
  /** ISO (yyyy-MM-dd). */
  dataPagamento: string
  forma: FormaPagamento
}

export type CobrancaDoHistorico = {
  id: string
  descricao: string
  valor: number
  /** ISO (yyyy-MM-dd). */
  dataVencimento: string
  status: StatusCobrancaAluno
  turmaNome: string | null
  /** Instante (ISO) da baixa; nulo enquanto não paga. */
  pagaEm: string | null
  /** Pagamento registrado (online); nulo na baixa manual e enquanto não paga. */
  pagamento: PagamentoResumo | null
}

export type HistoricoFinanceiro = {
  alunoId: string
  alunoNome: string | null
  alunoNumeroMatricula: string
  resumo: {
    /** Pendentes + em atraso. */
    valorEmAberto: number
    valorEmAtraso: number
    valorPago: number
    quantidadePendente: number
    quantidadeEmAtraso: number
    quantidadePaga: number
    quantidadeCancelada: number
  }
  /** Da mais recente para a mais antiga, pelo vencimento. */
  cobrancas: CobrancaDoHistorico[]
}

/**
 * Situação do pagamento online: AGUARDANDO_PAGAMENTO (pode pagar ou já tem
 * PIX gerado), PROCESSANDO (gerando no provedor), PAGA ou CANCELADA.
 */
export type SituacaoPagamento = 'AGUARDANDO_PAGAMENTO' | 'PROCESSANDO' | 'PAGA' | 'CANCELADA'

export type CobrancaAluno = {
  id: string
  descricao: string
  valor: number
  dataVencimento: string
  status: StatusCobrancaAluno
  situacaoPagamento: SituacaoPagamento
  formaPagamento: FormaPagamento | null
  pagamento: PagamentoResumo | null
  acoes: string[]
}

export type DadosPix = {
  /** PNG em base64, sem o prefixo data:. */
  qrCodeBase64: string
  copiaECola: string
  /** Instante (ISO) até quando o QR Code vale; nulo se o provedor não informou. */
  validade: string | null
}

export type DadosBoleto = {
  /** 47 dígitos, sem pontuação. */
  linhaDigitavel: string
  codigoBarras: string
}

export type DadosPagamento = {
  /** PROCESSANDO: o provedor ainda não respondeu e o PIX/boleto ainda não existe. */
  situacao: 'PRONTO' | 'PROCESSANDO'
  forma: FormaPagamento
  valor: number
  /** Vencimento enviado ao provedor: o de hoje, se a cobrança já venceu. */
  vencimento: string
  pix: DadosPix | null
  boleto: DadosBoleto | null
}
