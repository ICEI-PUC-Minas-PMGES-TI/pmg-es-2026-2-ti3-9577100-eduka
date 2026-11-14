import type { PaginaDoBackend } from '../../hooks/usePaginacao'
import type { Atividade, AutorAtividade, FiltrosAtividades } from '../../types/auditoria/auditoria'
import { apiRequest } from '../api'

/**
 * Página do Registro de atividades (IU28), da mais recente para a mais antiga.
 * Filtro vazio não vai na URL. 400 se o período está invertido.
 */
export function listarAtividades(
  filtros: FiltrosAtividades,
  pagina: number,
  tamanho: number,
  signal?: AbortSignal,
): Promise<PaginaDoBackend<Atividade>> {
  const params = new URLSearchParams({ pagina: String(pagina), tamanho: String(tamanho) })
  for (const [chave, valor] of Object.entries(filtros)) {
    if (valor) params.set(chave, valor)
  }
  return apiRequest<PaginaDoBackend<Atividade>>(`/auditoria/pagina?${params}`, { signal })
}

/** Quem já fez alguma atividade na escola, por nome: as opções do filtro por pessoa. */
export function listarAutoresDeAtividades(signal?: AbortSignal): Promise<AutorAtividade[]> {
  return apiRequest<AutorAtividade[]>('/auditoria/autores', { signal })
}
