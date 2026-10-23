// Conta de recebimento da escola (conta filha no provedor). Espelha
// ContaRecebimentoResponse e AtivarRecebimentosRequest do backend.

export type StatusContaRecebimento =
  | 'NAO_INICIADA'
  | 'CRIANDO'
  | 'VERIFICACAO_PENDENTE'
  | 'ERRO_CRIACAO'
  | 'AGUARDANDO_APROVACAO'
  | 'APROVADA'
  | 'REJEITADA'
  | 'CREDENCIAL_INDISPONIVEL'

export const ROTULOS_STATUS_CONTA: Record<StatusContaRecebimento, string> = {
  NAO_INICIADA: 'Não ativada',
  CRIANDO: 'Criando conta',
  VERIFICACAO_PENDENTE: 'Verificação pendente',
  ERRO_CRIACAO: 'Erro na criação',
  AGUARDANDO_APROVACAO: 'Aguardando aprovação',
  APROVADA: 'Aprovada',
  REJEITADA: 'Rejeitada',
  CREDENCIAL_INDISPONIVEL: 'Requer suporte',
}

export type TipoEmpresa = 'MEI' | 'LIMITED' | 'INDIVIDUAL' | 'ASSOCIATION'

export const ROTULOS_TIPO_EMPRESA: Record<TipoEmpresa, string> = {
  MEI: 'MEI',
  LIMITED: 'Sociedade limitada (Ltda)',
  INDIVIDUAL: 'Empresa individual',
  ASSOCIATION: 'Associação',
}

export type TipoContaBancaria = 'CORRENTE' | 'POUPANCA'

export type SituacaoCadastral = 'PENDENTE' | 'EM_ANALISE' | 'APROVADO' | 'REJEITADO'

export const ROTULOS_SITUACAO: Record<SituacaoCadastral, string> = {
  PENDENTE: 'Pendente',
  EM_ANALISE: 'Em análise',
  APROVADO: 'Aprovado',
  REJEITADO: 'Rejeitado',
}

export type SituacaoConta = {
  geral: SituacaoCadastral | null
  documentacao: SituacaoCadastral | null
  dadosComerciais: SituacaoCadastral | null
  contaBancaria: SituacaoCadastral | null
}

/** Nunca traz a chave da conta nem os dados bancários: só o banco e os 4 últimos dígitos. */
export type ContaRecebimento = {
  status: StatusContaRecebimento
  recebimentosHabilitados: boolean
  motivoNaoHabilitado: string | null
  razaoSocial: string | null
  emailFinanceiro: string | null
  telefoneCelular: string | null
  tipoEmpresa: TipoEmpresa | null
  faturamentoMensal: number | null
  logradouro: string | null
  numero: string | null
  complemento: string | null
  bairro: string | null
  cep: string | null
  /** CNPJ do cadastro da escola, só leitura; nulo se a escola não tiver um. */
  cnpj: string | null
  bancoCodigo: string | null
  contaFinal: string | null
  situacao: SituacaoConta | null
  ultimoErro: string | null
  /** ISO com hora. */
  solicitadaEm: string | null
  aprovadaEm: string | null
  sincronizadaEm: string | null
  podeAtivar: boolean
  podeSincronizar: boolean
}

export type AtivarRecebimentosRequest = {
  razaoSocial: string
  emailFinanceiro: string
  /** Só dígitos (10 ou 11). */
  telefoneCelular: string
  tipoEmpresa: TipoEmpresa
  faturamentoMensal: number
  logradouro: string
  numero: string
  complemento: string | null
  bairro: string
  /** Só dígitos (8). */
  cep: string
  dadosBancarios: {
    codigoBanco: string
    agencia: string
    conta: string
    digitoConta: string
    tipo: TipoContaBancaria
  }
}
