import type { ContatoEscola } from '../../types/escola/contatoEscola'
import { apiRequest } from '../api'

/** Contato da escola do token, para a Central de ajuda; o SUPER_ADMIN não tem escola. */
export function buscarContatoDaEscola(signal?: AbortSignal): Promise<ContatoEscola> {
  return apiRequest<ContatoEscola>('/minha-escola/contato', { signal })
}
