import { useCallback, useEffect, useState } from 'react'
import { ApiError } from '../../../services/api'
import {
  atualizarProfessor,
  ativarProfessor,
  cadastrarProfessor,
  excluirProfessor,
  inativarProfessor,
  listarProfessores,
} from '../../../services/professorService'
import type { Professor, ProfessorRequest } from '../../../types/professor'

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

  /** Cadastra um professor e o coloca no topo da lista; lança ApiError se falhar. */
  const cadastrar = useCallback(async (dados: ProfessorRequest) => {
    const criado = await cadastrarProfessor(dados)
    setProfessores((atuais) => [criado, ...atuais])
    return criado
  }, [])

  /** Salva a edição e atualiza a linha na lista; lança ApiError se falhar. */
  const atualizar = useCallback(async (id: string, dados: ProfessorRequest) => {
    const atualizado = await atualizarProfessor(id, dados)
    setProfessores((atuais) => atuais.map((p) => (p.id === id ? atualizado : p)))
    return atualizado
  }, [])

  /** Inativa um professor ativo ou reativa um inativo; lança ApiError se falhar. */
  const alternarSituacao = useCallback(async (professor: Professor) => {
    const atualizado = professor.ativo
      ? await inativarProfessor(professor.id)
      : await ativarProfessor(professor.id)
    setProfessores((atuais) => atuais.map((p) => (p.id === atualizado.id ? atualizado : p)))
  }, [])

  /** Exclui um professor e o tira da lista; lança ApiError se falhar. */
  const excluir = useCallback(async (professor: Professor) => {
    await excluirProfessor(professor.id)
    setProfessores((atuais) => atuais.filter((p) => p.id !== professor.id))
  }, [])

  return { professores, carregando, erro, recarregar, cadastrar, atualizar, alternarSituacao, excluir }
}
