import type { DashboardEscola, DashboardSecretaria } from '../types/dashboard'
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
