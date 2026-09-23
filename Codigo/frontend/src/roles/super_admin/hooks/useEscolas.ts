import { useCallback, useEffect, useState } from 'react'
import { ApiError } from '../../../services/api'
import {
  ativarEscola,
  cadastrarEscola,
  listarEscolas,
  suspenderEscola,
} from '../../../services/escolaService'
import type { Escola, EscolaRequest } from '../../../types/escola'

function mensagemDeErro(erro: unknown): string {
  return erro instanceof ApiError ? erro.message : 'Erro inesperado ao carregar as escolas.'
}

export function useEscolas() {
  const [escolas, setEscolas] = useState<Escola[]>([])
  const [carregando, setCarregando] = useState(true)
  const [erro, setErro] = useState<string | null>(null)
  const [versao, setVersao] = useState(0)

  useEffect(() => {
    const controller = new AbortController()

    listarEscolas(controller.signal)
      .then(setEscolas)
      .catch((e: unknown) => {
        if (controller.signal.aborted) return
        setEscolas([])
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

  /** Suspende uma escola ativa ou reativa uma suspensa; lança ApiError se falhar. */
  const alternarSituacao = useCallback(async (escola: Escola) => {
    const atualizada = escola.ativo
      ? await suspenderEscola(escola.id)
      : await ativarEscola(escola.id)
    setEscolas((atuais) => atuais.map((e) => (e.id === atualizada.id ? atualizada : e)))
  }, [])

  /** Cadastra uma escola e a coloca no topo da lista; lança ApiError se falhar. */
  const cadastrar = useCallback(async (dados: EscolaRequest) => {
    const criada = await cadastrarEscola(dados)
    setEscolas((atuais) => [criada, ...atuais])
    return criada
  }, [])

  return { escolas, carregando, erro, recarregar, alternarSituacao, cadastrar }
}
