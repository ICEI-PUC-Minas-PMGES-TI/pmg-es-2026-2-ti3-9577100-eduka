import type { FormaPagamento, PagamentoResumo, SituacaoPagamento } from './pagamentoAluno'

// Fatura da plataforma (Fluxo A): o que a escola paga ao Eduka. Espelha
// FaturaPlataformaResponse, LancarFaturaRequest e ComprovanteResponse do backend.

export type StatusFatura = 'PENDENTE' | 'EM_ATRASO' | 'PAGA' | 'CANCELADA'

export const ROTULOS_STATUS_FATURA: Record<StatusFatura, string> = {
  PENDENTE: 'Pendente',
  EM_ATRASO: 'Em atraso',
  PAGA: 'Paga',
  CANCELADA: 'Cancelada',
}

/** Ações que o backend libera para quem está vendo; a tela não recalcula regra. */
export type AcaoFatura = 'PAGAR' | 'CANCELAR' | 'VER_COMPROVANTE'

export type FaturaPlataforma = {
  id: string
  escolaId: string
  escolaNome: string
  /** yyyy-MM */
  competencia: string
  descricao: string
  valor: number
  /** ISO (yyyy-MM-dd). */
  dataVencimento: string
  /** Já com EM_ATRASO no lugar de PENDENTE vencida. */
  status: StatusFatura
  situacaoPagamento: SituacaoPagamento
  formaPagamento: FormaPagamento | null
  pagamento: PagamentoResumo | null
  acoes: AcaoFatura[]
}

export type LancarFaturaRequest = {
  /** yyyy-MM */
  competencia: string
  descricao: string
  valor: number
  /** ISO (yyyy-MM-dd), a partir de hoje. */
  dataVencimento: string
}

/** Comprovante montado pelo Eduka (nunca a página do provedor). */
export type Comprovante = {
  numero: string
  beneficiarioNome: string
  beneficiarioDocumento: string | null
  pagadorNome: string
  pagadorDocumentoMascarado: string | null
  descricao: string
  vencimento: string
  valorPago: number
  dataPagamento: string
  forma: FormaPagamento
  codigoTransacao: string | null
}
