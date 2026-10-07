import { useCallback, useEffect, useState } from 'react'
import { ApiError } from '../../../services/api'

function mensagemDeErro(erro: unknown): string {
  // O 404 aqui é o login sem cadastro de ALUNO vinculado na escola.
  if (erro instanceof ApiError && erro.status === 404) {
    return 'Seu login não está vinculado a um cadastro de aluno. Fale com a secretaria da escola.'
  }
  return erro instanceof ApiError ? erro.message : 'Erro inesperado ao carregar seus dados.'
}

/**
 * Carrega um recurso do portal do aluno, com os estados de carregando, erro
 * e "tentar novamente". `buscar` deve ser estável (função de módulo).
 */
export function useDadosDoAluno<T>(buscar: (signal: AbortSignal) => Promise<T>, inicial: T) {
  const [dados, setDados] = useState<T>(inicial)
  const [carregando, setCarregando] = useState(true)
  const [erro, setErro] = useState<string | null>(null)
  const [versao, setVersao] = useState(0)

  useEffect(() => {
    const controller = new AbortController()

    buscar(controller.signal)
      .then(setDados)
      .catch((e: unknown) => {
        if (controller.signal.aborted) return
        setErro(mensagemDeErro(e))
      })
      .finally(() => {
        if (!controller.signal.aborted) setCarregando(false)
      })

    return () => controller.abort()
  }, [buscar, versao])

  const recarregar = useCallback(() => {
    setCarregando(true)
    setErro(null)
    setVersao((v) => v + 1)
  }, [])

  return { dados, carregando, erro, recarregar }
}
