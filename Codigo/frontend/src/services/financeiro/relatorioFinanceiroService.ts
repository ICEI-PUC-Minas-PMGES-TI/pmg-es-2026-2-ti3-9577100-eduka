import type { ConjuntoCsv, RelatorioFinanceiro, SeparadorCsv } from '../../types/financeiro/relatorioFinanceiro'
import { apiBaixarArquivo, apiRequest } from '../api'

type Periodo = { de: string; ate: string }

/**
 * Relatório de `de` a `ate` (AAAA-MM-DD, inclusivos), no máximo 24 meses. A
 * escola sai do token. Perfis: MASTER e FINANCEIRO.
 */
export function obterRelatorioFinanceiro(periodo: Periodo, signal?: AbortSignal): Promise<RelatorioFinanceiro> {
  return apiRequest<RelatorioFinanceiro>(`/relatorios/financeiro?${new URLSearchParams(periodo)}`, { signal })
}

/** O mesmo nome que o backend dá ao anexo: traz as datas completas do período. */
export function nomeDoCsv({ de, ate }: Periodo): string {
  return `relatorio-financeiro-${de}-a-${ate}.csv`
}

/** O CSV do relatório, com uma seção por conjunto. Perfis: MASTER e FINANCEIRO. */
export function exportarRelatorioFinanceiro(
  periodo: Periodo,
  conjuntos: ConjuntoCsv[],
  separador: SeparadorCsv,
): Promise<Blob> {
  const parametros = new URLSearchParams({ ...periodo, conjuntos: conjuntos.join(','), separador })
  return apiBaixarArquivo(`/relatorios/financeiro/exportar?${parametros}`, 'text/csv')
}
