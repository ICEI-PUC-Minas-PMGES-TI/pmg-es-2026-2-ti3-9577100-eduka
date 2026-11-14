import type { DashboardEscola, DashboardFinanceiro, DashboardSecretaria, MesFinanceiro } from '../../types/dashboard/dashboard'
import { apiRequest } from '../api'

// A escola sai do token. O resumo do professor fica no portal dele
// (ver portalProfessorService).

/** Resumo da escola. Perfil: MASTER. */
export function obterDashboardEscola(signal?: AbortSignal): Promise<DashboardEscola> {
  return apiRequest<DashboardEscola>('/dashboard/escola', { signal })
}

/** Resumo de matrículas. Perfis: MASTER e SECRETARIA. */
export function obterDashboardSecretaria(signal?: AbortSignal): Promise<DashboardSecretaria> {
  return apiRequest<DashboardSecretaria>('/dashboard/secretaria', { signal })
}

/**
 * Entrou e saiu de cada mês de `de` a `ate` (AAAA-MM-DD, inclusivos): o filtro
 * com calendário do gráfico. No máximo 24 meses. Perfis: MASTER e FINANCEIRO.
 */
export function obterMovimentoFinanceiro(
  periodo: { de: string; ate: string },
  signal?: AbortSignal,
): Promise<MesFinanceiro[]> {
  return apiRequest<MesFinanceiro[]>(`/dashboard/financeiro/movimento?${new URLSearchParams(periodo)}`, { signal })
}

/** Resumo financeiro: recebido, em aberto, atraso, despesas e últimas movimentações. Perfis: MASTER e FINANCEIRO. */
export function obterDashboardFinanceiro(signal?: AbortSignal): Promise<DashboardFinanceiro> {
  return apiRequest<DashboardFinanceiro>('/dashboard/financeiro', { signal })
}
