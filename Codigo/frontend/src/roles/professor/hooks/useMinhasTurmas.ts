import { useCallback, useEffect, useState } from 'react'
import { ApiError } from '../../../services/api'
import { listarMinhasTurmas } from '../../../services/portal/portalProfessorService'
import type { TurmaDoProfessor } from '../../../types/academico/turmaProfessor'

function mensagemDeErro(erro: unknown): string {
  // O 404 aqui é o login sem cadastro de PROFESSOR vinculado na escola.
  if (erro instanceof ApiError && erro.status === 404) {
    return 'Seu login não está vinculado a um cadastro de professor. Fale com a secretaria da escola.'
  }
  return erro instanceof ApiError ? erro.message : 'Erro inesperado ao carregar suas turmas.'
}

export function useMinhasTurmas() {
  const [turmas, setTurmas] = useState<TurmaDoProfessor[]>([])
  const [carregando, setCarregando] = useState(true)
  const [erro, setErro] = useState<string | null>(null)
  const [versao, setVersao] = useState(0)

  useEffect(() => {
    const controller = new AbortController()

    listarMinhasTurmas(controller.signal)
      .then(setTurmas)
      .catch((e: unknown) => {
        if (controller.signal.aborted) return
        setTurmas([])
        setErro(mensagemDeErro(e))
      })
      .finally(() => {
        if (!controller.signal.aborted) setCarregando(false)
      })

    return () => controller.abort()
  }, [versao])

  const recarregar = useCallback(() => {
    setCarregando(true)
    setErro(null)
    setVersao((v) => v + 1)
  }, [])

  return { turmas, carregando, erro, recarregar }
}
