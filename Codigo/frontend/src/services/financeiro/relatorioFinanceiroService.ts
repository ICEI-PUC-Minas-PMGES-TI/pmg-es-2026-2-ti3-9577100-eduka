import type { ConjuntoCsv, RelatorioFinanceiro, SeparadorCsv } from '../../types/financeiro/relatorioFinanceiro'
import { apiBaixarArquivo, apiBaixarArquivoComNome, apiRequest } from '../api'

const XLSX = 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet'

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

/** Nome da planilha se o backend não mandar o dele (que traz também o nome da escola). */
export function nomeDaPlanilha({ de, ate }: Periodo): string {
  return `relatorio-financeiro-${de}-a-${ate}.xlsx`
}

/**
 * A planilha .xlsx do relatório, com a identidade da escola: aba de resumo e
 * uma aba por conjunto, editáveis. Perfis: MASTER e FINANCEIRO.
 */
export async function exportarPlanilhaRelatorioFinanceiro(periodo: Periodo): Promise<{ conteudo: Blob; nome: string }> {
  const { conteudo, nome } = await apiBaixarArquivoComNome(
    `/relatorios/financeiro/planilha?${new URLSearchParams(periodo)}`,
    XLSX,
  )
  return { conteudo, nome: nome ?? nomeDaPlanilha(periodo) }
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
