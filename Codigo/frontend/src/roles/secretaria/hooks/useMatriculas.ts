import { useCallback, useEffect, useState } from 'react'
import { listarTurmasAcademicas } from '../../../services/academicoService'
import { ApiError } from '../../../services/api'
import { obterDashboardSecretaria } from '../../../services/dashboardService'
import { listarAlunos } from '../../../services/matriculaService'
import type { TurmaAcademica } from '../../../types/academico'
import type { AlunoDaLista } from '../../../types/aluno'
import type { DashboardSecretaria } from '../../../types/dashboard'

function mensagemDeErro(erro: unknown): string {
  return erro instanceof ApiError ? erro.message : 'Erro inesperado ao carregar as matrículas.'
}

/**
 * Dados da tela de matrículas que não dependem da turma escolhida, numa
 * consulta cada: as turmas (com as vagas ocupadas), os alunos (com a turma
 * atual de cada um) e o resumo da escola. Nenhum traz o histórico de
 * matrículas; as da turma escolhida a tela busca à parte.
 */
export function useMatriculas() {
  const [turmas, setTurmas] = useState<TurmaAcademica[]>([])
  const [alunos, setAlunos] = useState<AlunoDaLista[]>([])
  const [resumo, setResumo] = useState<DashboardSecretaria | null>(null)
  const [carregando, setCarregando] = useState(true)
  const [erro, setErro] = useState<string | null>(null)
  const [versao, setVersao] = useState(0)

  useEffect(() => {
    const controller = new AbortController()
    const { signal } = controller

    Promise.all([listarTurmasAcademicas(signal), listarAlunos(signal), obterDashboardSecretaria(signal)])
      .then(([turmasDaEscola, alunosDaEscola, resumoDaEscola]) => {
        setTurmas(
          [...turmasDaEscola].sort((a, b) => a.nome.localeCompare(b.nome, 'pt-BR', { numeric: true })),
        )
        // O anonimizado saiu da escola: não volta a ser matriculado.
        setAlunos(
          alunosDaEscola
            .filter((a) => a.situacao !== 'ANONIMIZADO')
            .sort((a, b) => a.nome.localeCompare(b.nome, 'pt-BR')),
        )
        setResumo(resumoDaEscola)
        setErro(null)
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

  /** Lê de novo vagas, situações e resumo depois de uma mudança, sem voltar ao "Carregando". */
  const atualizarContagens = useCallback(() => setVersao((v) => v + 1), [])

  return { turmas, alunos, resumo, carregando, erro, recarregar, atualizarContagens }
}
