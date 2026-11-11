import type { AvisoPrivacidade } from '../types/privacidade'
import { apiRequest } from './api'

/** O aviso em vigor e se o usuário do token já o aceitou. Qualquer perfil. */
export function buscarAvisoPrivacidade(signal?: AbortSignal): Promise<AvisoPrivacidade> {
  return apiRequest<AvisoPrivacidade>('/privacidade/aviso', { signal })
}

/** Grava o aceite da versão lida. 400 se o aviso mudou nesse meio-tempo. */
export function aceitarAvisoPrivacidade(versao: string): Promise<AvisoPrivacidade> {
  return apiRequest<AvisoPrivacidade>('/privacidade/aceite', { method: 'POST', body: JSON.stringify({ versao }) })
}
