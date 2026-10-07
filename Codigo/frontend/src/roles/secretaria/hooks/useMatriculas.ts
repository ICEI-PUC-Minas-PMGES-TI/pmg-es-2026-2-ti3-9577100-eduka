import { useCallback, useEffect, useState } from 'react'
import { listarTurmasAcademicas } from '../../../services/academicoService'
import { ApiError } from '../../../services/api'
import {
  atualizarMatricula,
  listarAlunos,
  listarMatriculas,
  matricularAluno,
} from '../../../services/matriculaService'
import type { TurmaAcademica } from '../../../types/academico'
import type { AlunoResumo } from '../../../types/aluno'
import type { AtualizarMatriculaRequest, Matricula, MatriculaRequest } from '../../../types/matricula'

function mensagemDeErro(erro: unknown): string {
  return erro instanceof ApiError ? erro.message : 'Erro inesperado ao carregar as matrículas.'
}

/**
 * Dados da tela de matrículas: as turmas da escola (com as vagas), os alunos
 * e todas as matrículas, numa consulta cada. Os contadores de vaga saem das
 * matrículas carregadas aqui.
 */
export function useMatriculas() {
  const [turmas, setTurmas] = useState<TurmaAcademica[]>([])
  const [alunos, setAlunos] = useState<AlunoResumo[]>([])
  const [matriculas, setMatriculas] = useState<Matricula[]>([])
  const [carregando, setCarregando] = useState(true)
  const [erro, setErro] = useState<string | null>(null)
  const [versao, setVersao] = useState(0)

  useEffect(() => {
    const controller = new AbortController()
    const { signal } = controller

    Promise.all([listarTurmasAcademicas(signal), listarAlunos(signal), listarMatriculas(signal)])
      .then(([turmasDaEscola, alunosDaEscola, matriculasDaEscola]) => {
        setTurmas(
          [...turmasDaEscola].sort((a, b) => a.nome.localeCompare(b.nome, 'pt-BR', { numeric: true })),
        )
        setAlunos([...alunosDaEscola].sort((a, b) => a.nome.localeCompare(b.nome, 'pt-BR')))
        setMatriculas(matriculasDaEscola)
      })
      .catch((e: unknown) => {
        if (signal.aborted) return
        setErro(mensagemDeErro(e))
      })
      .finally(() => {
        if (!signal.aborted) setCarregando(false)
      })

    return () => controller.abort()
  }, [versao])

  const recarregar = useCallback(() => {
    setCarregando(true)
    setErro(null)
    setVersao((v) => v + 1)
  }, [])

  /** Matricula um aluno; lança ApiError se o backend recusar. */
  const matricular = useCallback(async (dados: MatriculaRequest) => {
    const criada = await matricularAluno(dados)
    setMatriculas((atuais) => [...atuais, criada])
    return criada
  }, [])

  /** Muda situação, turma e/ou observação; lança ApiError se o backend recusar. */
  const atualizar = useCallback(async (id: string, dados: AtualizarMatriculaRequest) => {
    const atualizada = await atualizarMatricula(id, dados)
    setMatriculas((atuais) => atuais.map((m) => (m.id === atualizada.id ? atualizada : m)))
    return atualizada
  }, [])

  return { turmas, alunos, matriculas, carregando, erro, recarregar, matricular, atualizar }
}
