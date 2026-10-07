import { useCallback, useEffect, useState } from 'react'
import { ApiError } from '../services/api'

type Buscar<T> = (signal: AbortSignal) => Promise<T>

type Resultado<T> = {
  /** Busca e versão que produziram este resultado. */
  origem: Buscar<T>
  versao: number
  dados: T
  erro: string | null
}

/**
 * Carrega um recurso da tela (um dashboard, ou o que o usuário escolheu: turma,
 * disciplina, avaliação). Trocar `buscar` (memorizado com useCallback) refaz a
 * busca; `null` deixa a tela sem carregar nada. `inicial` deve ser estável.
 *
 * @param mensagem404 texto mostrado no lugar do erro quando a API responde 404
 */
export function useCarregamento<T>(buscar: Buscar<T> | null, inicial: T, mensagem404: string) {
  const [resultado, setResultado] = useState<Resultado<T> | null>(null)
  const [versao, setVersao] = useState(0)

  useEffect(() => {
    if (!buscar) return
    const controller = new AbortController()

    buscar(controller.signal)
      .then((dados) => setResultado({ origem: buscar, versao, dados, erro: null }))
      .catch((e: unknown) => {
        if (controller.signal.aborted) return
        const erro =
          e instanceof ApiError
            ? e.status === 404
              ? mensagem404
              : e.message
            : 'Erro inesperado ao carregar os dados.'
        setResultado({ origem: buscar, versao, dados: inicial, erro })
      })

    return () => controller.abort()
  }, [buscar, versao, inicial, mensagem404])

  // Resultado de uma busca anterior (outra turma, outra avaliação) não vale mais.
  const atual = buscar && resultado?.origem === buscar && resultado.versao === versao ? resultado : null

  const recarregar = useCallback(() => setVersao((v) => v + 1), [])

  /** Troca os dados sem buscar de novo (ex.: com a resposta de um cadastro). */
  const atualizar = useCallback((mudar: (dados: T) => T) => {
    setResultado((r) => (r && !r.erro ? { ...r, dados: mudar(r.dados) } : r))
  }, [])

  return {
    dados: atual?.dados ?? inicial,
    carregando: buscar !== null && atual === null,
    erro: atual?.erro ?? null,
    recarregar,
    atualizar,
  }
}
