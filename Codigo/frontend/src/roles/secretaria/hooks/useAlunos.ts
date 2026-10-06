import { useCallback, useEffect, useState } from 'react'
import { atualizarAluno, cadastrarAluno } from '../../../services/alunoService'
import { ApiError } from '../../../services/api'
import { listarAlunos } from '../../../services/matriculaService'
import type { Aluno, AlunoRequest, AlunoResumo } from '../../../types/aluno'

function mensagemDeErro(erro: unknown): string {
  return erro instanceof ApiError ? erro.message : 'Erro inesperado ao carregar os alunos.'
}

function resumo(aluno: Aluno): AlunoResumo {
  return { id: aluno.id, nome: aluno.nome, matricula: aluno.matricula, genero: aluno.genero }
}

export function useAlunos() {
  const [alunos, setAlunos] = useState<AlunoResumo[]>([])
  const [carregando, setCarregando] = useState(true)
  const [erro, setErro] = useState<string | null>(null)
  const [versao, setVersao] = useState(0)

  useEffect(() => {
    const controller = new AbortController()

    listarAlunos(controller.signal)
      .then(setAlunos)
      .catch((e: unknown) => {
        if (controller.signal.aborted) return
        setAlunos([])
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

  /** Cadastra um aluno e o coloca no topo da lista; lança ApiError se falhar. */
  const cadastrar = useCallback(async (dados: AlunoRequest) => {
    const criado = await cadastrarAluno(dados)
    setAlunos((atuais) => [resumo(criado), ...atuais])
    return criado
  }, [])

  /** Salva a edição e atualiza a linha na lista; lança ApiError se falhar. */
  const atualizar = useCallback(async (id: string, dados: AlunoRequest) => {
    const atualizado = await atualizarAluno(id, dados)
    setAlunos((atuais) => atuais.map((a) => (a.id === id ? resumo(atualizado) : a)))
    return atualizado
  }, [])

  return { alunos, carregando, erro, recarregar, cadastrar, atualizar }
}
