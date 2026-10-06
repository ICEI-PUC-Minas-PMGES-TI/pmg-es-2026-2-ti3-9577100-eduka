import { useCallback, useEffect, useState } from 'react'
import {
  cadastrarCurso,
  cadastrarDisciplina,
  cadastrarPeriodoLetivo,
  cadastrarTurma,
  listarCursos,
  listarDisciplinas,
  listarPeriodosLetivos,
  listarTurmasAcademicas,
} from '../../../services/academicoService'
import { ApiError } from '../../../services/api'
import type {
  Curso,
  CursoRequest,
  Disciplina,
  DisciplinaRequest,
  PeriodoLetivo,
  PeriodoLetivoRequest,
  TurmaAcademica,
  TurmaRequest,
} from '../../../types/academico'

function mensagemDeErro(erro: unknown): string {
  return erro instanceof ApiError ? erro.message : 'Erro inesperado ao carregar a estrutura acadêmica.'
}

function porNome<T extends { nome: string }>(a: T, b: T): number {
  return a.nome.localeCompare(b.nome, 'pt-BR', { numeric: true })
}

/**
 * Dados do painel acadêmico da secretaria. As quatro listas são carregadas
 * juntas porque se referenciam: a disciplina escolhe um curso, e a turma
 * escolhe período, curso e disciplinas.
 */
export function useEstruturaAcademica() {
  const [periodos, setPeriodos] = useState<PeriodoLetivo[]>([])
  const [cursos, setCursos] = useState<Curso[]>([])
  const [disciplinas, setDisciplinas] = useState<Disciplina[]>([])
  const [turmas, setTurmas] = useState<TurmaAcademica[]>([])
  const [carregando, setCarregando] = useState(true)
  const [erro, setErro] = useState<string | null>(null)
  const [versao, setVersao] = useState(0)

  useEffect(() => {
    const controller = new AbortController()
    const { signal } = controller

    Promise.all([
      listarPeriodosLetivos(signal),
      listarCursos(signal),
      listarDisciplinas(signal),
      listarTurmasAcademicas(signal),
    ])
      .then(([p, c, d, t]) => {
        // Período mais recente primeiro; o resto em ordem alfabética.
        setPeriodos([...p].sort((a, b) => b.dataInicio.localeCompare(a.dataInicio)))
        setCursos([...c].sort(porNome))
        setDisciplinas([...d].sort(porNome))
        setTurmas([...t].sort(porNome))
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

  // Cada cadastro entra no topo da lista; todos lançam ApiError se falharem.

  const cadastrarPeriodo = useCallback(async (dados: PeriodoLetivoRequest) => {
    const criado = await cadastrarPeriodoLetivo(dados)
    setPeriodos((atuais) => [criado, ...atuais])
    return criado
  }, [])

  const cadastrarNovoCurso = useCallback(async (dados: CursoRequest) => {
    const criado = await cadastrarCurso(dados)
    setCursos((atuais) => [criado, ...atuais])
    return criado
  }, [])

  const cadastrarNovaDisciplina = useCallback(async (dados: DisciplinaRequest) => {
    const criada = await cadastrarDisciplina(dados)
    setDisciplinas((atuais) => [criada, ...atuais])
    return criada
  }, [])

  const cadastrarNovaTurma = useCallback(async (dados: TurmaRequest) => {
    const criada = await cadastrarTurma(dados)
    setTurmas((atuais) => [criada, ...atuais])
    return criada
  }, [])

  return {
    periodos,
    cursos,
    disciplinas,
    turmas,
    carregando,
    erro,
    recarregar,
    cadastrarPeriodo,
    cadastrarCurso: cadastrarNovoCurso,
    cadastrarDisciplina: cadastrarNovaDisciplina,
    cadastrarTurma: cadastrarNovaTurma,
  }
}
