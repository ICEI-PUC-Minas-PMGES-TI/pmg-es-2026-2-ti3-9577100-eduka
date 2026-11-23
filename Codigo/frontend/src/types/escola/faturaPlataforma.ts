import type { ModuloEscola, PlanoEscola } from './escola'
import type { FormaPagamento, PagamentoResumo, SituacaoPagamento } from '../financeiro/pagamentoAluno'

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
  /** Quanto do pago foi devolvido. */
  valorEstornado: number
  dataPagamento: string
  forma: FormaPagamento
  codigoTransacao: string | null
}

/** Assinatura da escola do token (GET /config-pagamento/assinatura, IU24-A). */
/**
 * Aviso de suspensão por fatura em atraso (GET /config-pagamento/assinatura/suspensao).
 * Espelha o SuspensaoPrevistaResponse do backend.
 */
export type SuspensaoPrevista = {
  /** ISO (yyyy-MM-dd): o dia em que a escola será suspensa se a fatura continuar em aberto. */
  suspensaoEm: string
  /** De hoje até `suspensaoEm`; 0 quando é hoje. */
  diasRestantes: number
  faturaId: string
  faturaDescricao: string
  faturaValor: number
  /** ISO (yyyy-MM-dd). */
  faturaVencimento: string
}

export type Assinatura = {
  escolaNome: string
  plano: PlanoEscola | null
  ativa: boolean
  /** ISO (yyyy-MM-dd). */
  inicioVigencia: string | null
  fimVigencia: string | null
  /** Nulo quando o plano não tem limite. */
  limiteUsuarios: number | null
  /** Logins ativos da escola hoje, de todos os perfis: o que conta no limite. */
  usuariosAtivos: number
  /** Módulos contratados; sem um deles, a tela dele some. */
  modulos: ModuloEscola[]
}
