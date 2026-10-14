export type StatusCobranca = 'A_PAGAR' | 'EM_ATRASO' | 'PAGA' | 'CANCELADA'

export type Cobranca = {
  id: string
  alunoNome: string
  descricao: string
  dataVencimento: string
  dataPagamento?: string | null
  valor: number
  status: StatusCobranca
  responsavelNome: string
  codigoBarras?: string
  linhaDigitavel?: string
}

export type FiltroCobranca = 'TODAS' | 'A_PAGAR' | 'EM_ATRASO' | 'PAGA'

export type NovaCobrancaRequest = {
  alunoNome: string
  descricao: string
  valor: number
  dataVencimento: string
  responsavelNome: string
}

export const ROTULOS_STATUS_COBRANCA: Record<StatusCobranca, string> = {
  A_PAGAR: 'A pagar',
  EM_ATRASO: 'Em atraso',
  PAGA: 'Paga',
  CANCELADA: 'Cancelada',
}

export const COBRANCAS_MOCK_INICIAIS: Cobranca[] = [
  {
    id: 'cob-1',
    alunoNome: 'Lucas Andrade',
    descricao: 'Mensalidade de outubro · vence 10/10/2026',
    dataVencimento: '2026-10-10',
    valor: 890.0,
    status: 'A_PAGAR',
    responsavelNome: 'Cláudia Andrade',
    linhaDigitavel: '34191.79001 01043.510047 91020.150008 8 10250000089000',
    codigoBarras: '34198102500000890001790001043510049102015000',
  },
  {
    id: 'cob-2',
    alunoNome: 'Caio Ferreira',
    descricao: 'Mensalidade de setembro · vence 10/09/2026',
    dataVencimento: '2026-09-10',
    valor: 420.0,
    status: 'EM_ATRASO',
    responsavelNome: 'Marcos Ferreira',
    linhaDigitavel: '34191.79001 01043.510047 91020.150008 8 10250000042000',
    codigoBarras: '34198102500000420001790001043510049102015000',
  },
  {
    id: 'cob-3',
    alunoNome: 'Sofia Lima',
    descricao: 'Mensalidade de setembro · vence 10/09/2026',
    dataVencimento: '2026-09-10',
    valor: 760.0,
    status: 'EM_ATRASO',
    responsavelNome: 'Renata Lima',
    linhaDigitavel: '34191.79001 01043.510047 91020.150008 8 10250000076000',
    codigoBarras: '34198102500000760001790001043510049102015000',
  },
  {
    id: 'cob-4',
    alunoNome: 'Helena Castro',
    descricao: 'Taxa de matrícula · vence 25/09/2026',
    dataVencimento: '2026-09-25',
    valor: 350.0,
    status: 'A_PAGAR',
    responsavelNome: 'Eduardo Castro',
    linhaDigitavel: '34191.79001 01043.510047 91020.150008 8 10250000035000',
    codigoBarras: '34198102500000350001790001043510049102015000',
  },
  {
    id: 'cob-5',
    alunoNome: 'Beatriz Nunes',
    descricao: 'Mensalidade de setembro · vence 10/09/2026',
    dataVencimento: '2026-09-10',
    dataPagamento: '2026-09-09',
    valor: 760.0,
    status: 'PAGA',
    responsavelNome: 'Patrícia Nunes',
    linhaDigitavel: '34191.79001 01043.510047 91020.150008 8 10250000076000',
    codigoBarras: '34198102500000760001790001043510049102015000',
  },
  {
    id: 'cob-6',
    alunoNome: 'Marina Rocha',
    descricao: 'Material didático · vence 05/09/2026',
    dataVencimento: '2026-09-05',
    valor: 1150.0,
    status: 'CANCELADA',
    responsavelNome: 'Fernando Rocha',
    linhaDigitavel: '34191.79001 01043.510047 91020.150008 8 10250000115000',
    codigoBarras: '34198102500001150001790001043510049102015000',
  },
]

