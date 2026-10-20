export type CategoriaDespesa =
  | 'Salários'
  | 'Contas'
  | 'Materiais'
  | 'Consertos'
  | 'Limpeza'
  | 'Outros'

export type SituacaoDespesa = 'A_PAGAR' | 'PAGA'

export type FiltroSituacaoDespesa = 'TODAS' | 'A_PAGAR' | 'PAGAS'

export type Despesa = {
  id: string
  descricao: string
  categoria: CategoriaDespesa
  data: string // YYYY-MM-DD
  valor: number
  situacao: SituacaoDespesa
}

export type NovaDespesaRequest = {
  descricao: string
  categoria: CategoriaDespesa
  data: string
  valor: number
  situacao: SituacaoDespesa
}

export const CATEGORIAS_DESPESA: CategoriaDespesa[] = [
  'Salários',
  'Contas',
  'Materiais',
  'Consertos',
  'Limpeza',
  'Outros',
]

export const DESPESAS_MOCK_INICIAIS: Despesa[] = [
  {
    id: 'desp-1',
    descricao: 'Salários dos professores',
    categoria: 'Salários',
    data: '2026-09-05',
    valor: 86000.0,
    situacao: 'PAGA',
  },
  {
    id: 'desp-2',
    descricao: 'Conta de energia',
    categoria: 'Contas',
    data: '2026-09-15',
    valor: 4320.0,
    situacao: 'PAGA',
  },
  {
    id: 'desp-3',
    descricao: 'Conserto do ar-condicionado',
    categoria: 'Consertos',
    data: '2026-09-18',
    valor: 1280.0,
    situacao: 'PAGA',
  },
  {
    id: 'desp-4',
    descricao: 'Conta de água',
    categoria: 'Contas',
    data: '2026-09-28',
    valor: 1950.0,
    situacao: 'A_PAGAR',
  },
  {
    id: 'desp-5',
    descricao: 'Livros da biblioteca',
    categoria: 'Materiais',
    data: '2026-09-30',
    valor: 3400.0,
    situacao: 'A_PAGAR',
  },
  {
    id: 'desp-6',
    descricao: 'Internet',
    categoria: 'Contas',
    data: '2026-09-30',
    valor: 890.0,
    situacao: 'A_PAGAR',
  },
]

