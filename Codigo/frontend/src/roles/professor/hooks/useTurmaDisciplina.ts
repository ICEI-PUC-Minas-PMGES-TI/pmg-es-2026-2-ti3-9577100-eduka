import { useSearchParams } from 'react-router'
import type { DisciplinaDoProfessor, TurmaDoProfessor } from '../../../types/academico/turmaProfessor'
import { useMinhasTurmas } from './useMinhasTurmas'

/** Uma disciplina que o professor dá numa turma: o par em que as avaliações vivem. */
export type TurmaDisciplina = {
  /** "turmaId-disciplinaId", valor do seletor. */
  chave: string
  turma: TurmaDoProfessor
  disciplina: DisciplinaDoProfessor
}

function chaveDe(turmaId: number | string | null, disciplinaId: number | string | null): string {
  return `${turmaId}-${disciplinaId}`
}

/**
 * Turma/disciplina escolhida nas telas de avaliações e notas. Fica na URL
 * (?turma=&disciplina=) para sobreviver ao F5 e para uma tela abrir a outra
 * já no mesmo lugar. Sem nada na URL, vale a primeira de uma turma ativa.
 */
export function useTurmaDisciplina() {
  const { turmas, carregando, erro, recarregar } = useMinhasTurmas()
  const [params, setParams] = useSearchParams()

  const opcoes: TurmaDisciplina[] = turmas.flatMap((turma) =>
    turma.disciplinas.map((disciplina) => ({ chave: chaveDe(turma.id, disciplina.id), turma, disciplina })),
  )
  const pedida = chaveDe(params.get('turma'), params.get('disciplina'))
  const selecionada =
    opcoes.find((o) => o.chave === pedida) ?? opcoes.find((o) => o.turma.ativa) ?? opcoes[0] ?? null

  /** Troca a turma/disciplina; os demais parâmetros (ex.: a avaliação) caem. */
  function selecionar(chave: string) {
    const opcao = opcoes.find((o) => o.chave === chave)
    if (!opcao) return
    setParams({ turma: String(opcao.turma.id), disciplina: String(opcao.disciplina.id) }, { replace: true })
  }

  return { opcoes, selecionada, selecionar, carregando, erro, recarregar }
}
