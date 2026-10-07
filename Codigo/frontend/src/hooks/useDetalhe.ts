import { useEffect, useState } from 'react'
import { ApiError } from '../services/api'

/**
 * Busca o detalhe de uma entidade ao abrir um modal (a listagem nem sempre
 * traz todos os campos, ex.: CPF). Montar o modal de novo busca de novo.
 */
export function useDetalhe<T>(buscar: (id: string, signal: AbortSignal) => Promise<T>, id: string) {
  const [dados, setDados] = useState<T | null>(null)
  const [carregando, setCarregando] = useState(true)
  const [erro, setErro] = useState<string | null>(null)

  useEffect(() => {
    const controller = new AbortController()
    buscar(id, controller.signal)
      .then(setDados)
      .catch((e: unknown) => {
        if (controller.signal.aborted) return
        if (e instanceof ApiError && e.status === 404) {
          setErro('Este cadastro não existe mais. Recarregue a página.')
        } else {
          setErro(e instanceof ApiError ? e.message : 'Erro inesperado ao carregar os dados.')
        }
      })
      .finally(() => {
        if (!controller.signal.aborted) setCarregando(false)
      })
    return () => controller.abort()
  }, [buscar, id])

  return { dados, carregando, erro }
}
