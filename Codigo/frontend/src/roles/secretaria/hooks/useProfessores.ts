import { useCallback, useEffect, useState } from 'react'
import { ApiError } from '../../../services/api'
import { listarProfessores } from '../../../services/professorService'
import type { Professor } from '../../../types/professor'

function mensagemDeErro(erro: unknown): string {
  return erro instanceof ApiError ? erro.message : 'Erro inesperado ao carregar os professores.'
}

export function useProfessores() {
  const [professores, setProfessores] = useState<Professor[]>([])
  const [carregando, setCarregando] = useState(true)
  const [erro, setErro] = useState<string | null>(null)
  const [versao, setVersao] = useState(0)

  useEffect(() => {
    const controller = new AbortController()

    listarProfessores(controller.signal)
      .then(setProfessores)
      .catch((e: unknown) => {
        if (controller.signal.aborted) return
        setProfessores([])
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

  return { professores, carregando, erro, recarregar }
}
