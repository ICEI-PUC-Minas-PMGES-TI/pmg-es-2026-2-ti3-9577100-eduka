import { useCallback, useEffect, useState } from 'react'
import {
  atualizarCurso,
  atualizarDisciplina,
  atualizarPeriodoLetivo,
  atualizarTurma,
  cadastrarCurso,
  cadastrarDisciplina,
  abrirMatriculasDoPeriodo,
  cadastrarPeriodoLetivo,
  encerrarPeriodoLetivo,
  cadastrarTurma,
  excluirCurso,
  excluirDisciplina,
  excluirPeriodoLetivo,
  excluirTurma,
  listarCursos,
  listarDisciplinas,
  listarPeriodosLetivos,
} from '../../../services/academico/academicoService'
import { ApiError } from '../../../services/api'
import type {
  Curso,
  CursoRequest,
  Disciplina,
  DisciplinaRequest,
  PeriodoLetivo,
  PeriodoLetivoRequest,
} from '../../../types/academico/academico'

function mensagemDeErro(erro: unknown): string {
  return erro instanceof ApiError ? erro.message : 'Erro inesperado ao carregar a estrutura acadêmica.'
}

function porNome<T extends { nome: string }>(a: T, b: T): number {
  return a.nome.localeCompare(b.nome, 'pt-BR', { numeric: true })
}

function buscarTudo(signal?: AbortSignal) {
  return Promise.all([
    listarPeriodosLetivos(signal),
    listarCursos(signal),
    listarDisciplinas(signal),
  ])
}

/**
 * Dados do painel acadêmico da secretaria. Períodos, cursos e disciplinas são
 * carregados juntos porque se referenciam: a disciplina escolhe um curso, e o
 * formulário da turma escolhe período, curso e disciplinas. As turmas não vêm
 * aqui: a aba Turmas pede ao backend só a página que mostra.
 */
export function useEstruturaAcademica() {
  const [periodos, setPeriodos] = useState<PeriodoLetivo[]>([])
  const [cursos, setCursos] = useState<Curso[]>([])
  const [disciplinas, setDisciplinas] = useState<Disciplina[]>([])
  const [carregando, setCarregando] = useState(true)
  const [erro, setErro] = useState<string | null>(null)
  const [versao, setVersao] = useState(0)

  const aplicar = useCallback(
    ([p, c, d]: [PeriodoLetivo[], Curso[], Disciplina[]]) => {
      // Período mais recente primeiro; o resto em ordem alfabética.
      setPeriodos([...p].sort((a, b) => b.dataInicio.localeCompare(a.dataInicio)))
      setCursos([...c].sort(porNome))
      setDisciplinas([...d].sort(porNome))
    },
    [],
  )

  useEffect(() => {
    const controller = new AbortController()
    const { signal } = controller

    buscarTudo(signal)
      .then(aplicar)
      .catch((e: unknown) => {
        if (signal.aborted) return
        setErro(mensagemDeErro(e))
      })
      .finally(() => {
        if (!signal.aborted) setCarregando(false)
      })

    return () => controller.abort()
  }, [versao, aplicar])

  const recarregar = useCallback(() => {
    setCarregando(true)
    setErro(null)
    setVersao((v) => v + 1)
  }, [])

  /**
   * Depois de uma edição, relê as três listas sem o "carregando": um nome
   * de curso alterado aparece também nas disciplinas. A aba Turmas busca a
   * página de novo ao abrir, já com os nomes novos.
   * Se a releitura falhar, a lista atual fica (a edição já foi salva).
   */
  const sincronizar = useCallback(async () => {
    try {
      aplicar(await buscarTudo())
    } catch {
      // Mantém o que está na tela; o próximo carregamento corrige.
    }
  }, [aplicar])

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

  // Edição: salva e relê tudo. Exclusão só tira da lista (o backend recusa
  // excluir o que outro cadastro usa, então nada mais muda).

  const editarPeriodo = useCallback(
    async (id: number, dados: PeriodoLetivoRequest) => {
      const salvo = await atualizarPeriodoLetivo(id, dados)
      await sincronizar()
      return salvo
    },
    [sincronizar],
  )

  /** Encerrar ou abrir matrículas (IU09): grava e relê, como a edição. */
  const encerrarPeriodo = useCallback(
    async (id: number) => {
      const salvo = await encerrarPeriodoLetivo(id)
      await sincronizar()
      return salvo
    },
    [sincronizar],
  )

  const abrirMatriculas = useCallback(
    async (id: number) => {
      const salvo = await abrirMatriculasDoPeriodo(id)
      await sincronizar()
      return salvo
    },
    [sincronizar],
  )

  const editarCurso = useCallback(
    async (id: number, dados: CursoRequest) => {
      const salvo = await atualizarCurso(id, dados)
      await sincronizar()
      return salvo
    },
    [sincronizar],
  )

  const editarDisciplina = useCallback(
    async (id: number, dados: DisciplinaRequest) => {
      const salva = await atualizarDisciplina(id, dados)
      await sincronizar()
      return salva
    },
    [sincronizar],
  )

  const removerPeriodo = useCallback(async (id: number) => {
    await excluirPeriodoLetivo(id)
    setPeriodos((atuais) => atuais.filter((p) => p.id !== id))
  }, [])

  const removerCurso = useCallback(async (id: number) => {
    await excluirCurso(id)
    setCursos((atuais) => atuais.filter((c) => c.id !== id))
  }, [])

  const removerDisciplina = useCallback(async (id: number) => {
    await excluirDisciplina(id)
    setDisciplinas((atuais) => atuais.filter((d) => d.id !== id))
  }, [])

  return {
    periodos,
    cursos,
    disciplinas,
    carregando,
    erro,
    recarregar,
    cadastrarPeriodo,
    cadastrarCurso: cadastrarNovoCurso,
    cadastrarDisciplina: cadastrarNovaDisciplina,
    // A turma não muda as outras listas: só grava, e a aba relê a própria página.
    cadastrarTurma,
    editarPeriodo,
    encerrarPeriodo,
    abrirMatriculas,
    editarCurso,
    editarDisciplina,
    editarTurma: atualizarTurma,
    removerPeriodo,
    removerCurso,
    removerDisciplina,
    removerTurma: excluirTurma,
  }
}
