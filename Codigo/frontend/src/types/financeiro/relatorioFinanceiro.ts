import type { FormaPagamento } from './pagamentoAluno'

// Espelha o RelatorioFinanceiroResponse do backend (GET /relatorios/financeiro).

export type TotalRelatorio = {
  valor: number
  quantidade: number
}

export type MesRelatorio = {
  /** AAAA-MM. */
  mes: string
  /** Pode ser negativa, se no mês só houve estornos. */
  receitas: number
  /** Despesas pagas no mês. */
  despesas: number
  saldo: number
}

export type FormaRelatorio = {
  /** Nula na baixa manual. */
  forma: FormaPagamento | null
  /** Já sem os estornos; pode ser negativo. */
  valor: number
  quantidade: number
  /** Sobre o recebido; nulo se o recebido não for positivo. */
  percentual: number | null
}

export type LinhaResumo = {
  /** Curso, tipo de cobrança ou mês (AAAA-MM), conforme o agrupamento; nulo na linha de total. */
  grupo: string | null
  cobrado: number
  recebido: number
  emAberto: number
  /** 0 a 100; nulo enquanto nada venceu. */
  inadimplencia: number | null
}

export type RelatorioFinanceiro = {
  de: string
  ate: string
  /** Pagamentos do período menos os estornos; a quantidade é a de pagamentos. */
  recebido: TotalRelatorio
  /** Já descontado de `recebido`. */
  estornado: TotalRelatorio
  /** Despesas lançadas que vencem no período. */
  despesas: TotalRelatorio
  /** Despesas pagas no período: entram no saldo e no gráfico. */
  despesasPagas: TotalRelatorio
  saldo: number
  inadimplencia: {
    valor: number
    cobrancas: number
    /** 0 a 100; nulo enquanto nada venceu. */
    percentual: number | null
  }
  receitasEDespesas: MesRelatorio[]
  formasPagamento: FormaRelatorio[]
  resumo: {
    porCurso: LinhaResumo[]
    porTipo: LinhaResumo[]
    porMes: LinhaResumo[]
    total: LinhaResumo
  }
}

/** O que pode entrar no CSV (IU19-A), na ordem do arquivo. */
export type ConjuntoCsv = 'COBRANCAS' | 'PAGAMENTOS' | 'DESPESAS'

export type SeparadorCsv = 'PONTO_E_VIRGULA' | 'VIRGULA'
