import type { DashboardEscola, DashboardFinanceiro, DashboardSecretaria } from '../types/dashboard'
import { apiRequest } from './api'

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

/** Resumo financeiro: recebido, em aberto, atraso, despesas e últimas movimentações. Perfis: MASTER e FINANCEIRO. */
export function obterDashboardFinanceiro(signal?: AbortSignal): Promise<DashboardFinanceiro> {
  return apiRequest<DashboardFinanceiro>('/dashboard/financeiro', { signal })
}
