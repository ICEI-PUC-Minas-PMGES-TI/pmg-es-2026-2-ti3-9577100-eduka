import { useCallback, useEffect, useState } from 'react'
import { ApiError } from '../../../services/api'
import {
  ativarMembroEquipe,
  atualizarMembroEquipe,
  cadastrarMembroEquipe,
  inativarMembroEquipe,
  listarEquipe,
} from '../../../services/escola/equipeService'
import type { MembroEquipe, MembroEquipeRequest, MembroEquipeUpdateRequest } from '../../../types/escola/equipe'

function porNome(a: MembroEquipe, b: MembroEquipe): number {
  return a.nome.localeCompare(b.nome, 'pt-BR')
}

/** A equipe da escola carregada, com as escritas já refletidas na lista. */
export function useEquipe() {
  const [equipe, setEquipe] = useState<MembroEquipe[]>([])
  const [carregando, setCarregando] = useState(true)
  const [erro, setErro] = useState<string | null>(null)
  const [versao, setVersao] = useState(0)

  useEffect(() => {
    const controller = new AbortController()
    listarEquipe(controller.signal)
      .then(setEquipe)
      .catch((e: unknown) => {
        if (controller.signal.aborted) return
        setEquipe([])
        setErro(e instanceof ApiError ? e.message : 'Erro inesperado ao carregar a equipe.')
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

  const substituir = useCallback((membro: MembroEquipe) => {
    setEquipe((atuais) => atuais.map((m) => (m.id === membro.id ? membro : m)))
  }, [])

  /** Lança ApiError se falhar. */
  const cadastrar = useCallback(async (dados: MembroEquipeRequest) => {
    const criado = await cadastrarMembroEquipe(dados)
    setEquipe((atuais) => [...atuais, criado].sort(porNome))
    return criado
  }, [])

  /** Lança ApiError se falhar. */
  const atualizar = useCallback(
    async (id: string, dados: MembroEquipeUpdateRequest) => {
      const atualizado = await atualizarMembroEquipe(id, dados)
      substituir(atualizado)
      return atualizado
    },
    [substituir],
  )

  /** Inativa quem está ativo ou reativa quem está inativo; lança ApiError se falhar. */
  const alternarSituacao = useCallback(
    async (membro: MembroEquipe) => {
      const atualizado = membro.ativo ? await inativarMembroEquipe(membro.id) : await ativarMembroEquipe(membro.id)
      substituir(atualizado)
      return atualizado
    },
    [substituir],
  )

  return { equipe, carregando, erro, recarregar, cadastrar, atualizar, alternarSituacao }
}
