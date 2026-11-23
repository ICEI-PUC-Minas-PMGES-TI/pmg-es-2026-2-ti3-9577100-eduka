import { useCallback, useEffect, useState } from 'react'
import {
  alocarProfessor,
  desalocarProfessor,
  listarAlocacoesDaTurma,
  listarTurmas,
} from '../../../services/academico/alocacaoService'
import { ApiError } from '../../../services/api'
import { listarProfessores } from '../../../services/pessoas/professorService'
import type { Alocacao, DisciplinaDaTurma, Turma } from '../../../types/academico/alocacao'
import type { Professor } from '../../../types/pessoas/professor'

/** Professor escolhido por disciplina; '' é "sem professor". */
export type EscolhasDaTurma = Record<number, string>

function mensagemDeErro(erro: unknown): string {
  return erro instanceof ApiError ? erro.message : 'Erro inesperado ao carregar as turmas e os professores.'
}

/**
 * Dados da tela de alocação: as turmas ativas da escola, os professores e as
 * alocações de todas as turmas (uma consulta por turma, para o contador de
 * cada cartão).
 */
export function useAlocacoes() {
  const [turmas, setTurmas] = useState<Turma[]>([])
  const [professores, setProfessores] = useState<Professor[]>([])
  const [alocacoes, setAlocacoes] = useState<Alocacao[]>([])
  const [carregando, setCarregando] = useState(true)
  const [erro, setErro] = useState<string | null>(null)
  const [versao, setVersao] = useState(0)

  useEffect(() => {
    const controller = new AbortController()
    const { signal } = controller

    async function carregar() {
      const [turmasDaEscola, professoresDaEscola] = await Promise.all([
        listarTurmas(signal),
        listarProfessores(signal),
      ])
      // Turma inativa não recebe alocação, então não entra na tela.
      const ativas = turmasDaEscola
        .filter((t) => t.ativa)
        .sort((a, b) => a.nome.localeCompare(b.nome, 'pt-BR', { numeric: true }))
      const porTurma = await Promise.all(ativas.map((t) => listarAlocacoesDaTurma(t.id, signal)))
      setTurmas(ativas)
      setProfessores(professoresDaEscola)
      setAlocacoes(porTurma.flat())
    }

    carregar()
      .catch((e: unknown) => {
        if (signal.aborted) return
        setTurmas([])
        setProfessores([])
        setAlocacoes([])
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

  /**
   * Aplica as escolhas da turma, uma disciplina por vez, e devolve as que
   * falharam. Não existe "trocar" na API: a disciplina só aceita um professor
   * por turma, então a troca é remover o atual e alocar o novo.
   */
  const salvar = useCallback(
    async (turma: Turma, escolhas: EscolhasDaTurma): Promise<DisciplinaDaTurma[]> => {
      const falhas: DisciplinaDaTurma[] = []

      for (const disciplina of turma.disciplinas) {
        const escolhido = escolhas[disciplina.id]
        const atual = alocacoes.find((a) => a.turmaId === turma.id && a.disciplinaId === disciplina.id)
        if (escolhido === undefined || escolhido === (atual?.professorId ?? '')) continue

        try {
          if (atual) {
            await desalocarProfessor(atual.professorId, atual.id)
            setAlocacoes((atuais) => atuais.filter((a) => a.id !== atual.id))
          }
          if (escolhido) {
            const criada = await alocarProfessor(escolhido, { turmaId: turma.id, disciplinaId: disciplina.id })
            setAlocacoes((atuais) => [...atuais, criada])
          }
        } catch {
          falhas.push(disciplina)
        }
      }

      // Uma falha pode ser outra pessoa mexendo na mesma turma (409, 404):
      // busca de novo para a tela mostrar o que ficou gravado de verdade.
      if (falhas.length > 0) {
        try {
          const gravadas = await listarAlocacoesDaTurma(turma.id)
          setAlocacoes((atuais) => [...atuais.filter((a) => a.turmaId !== turma.id), ...gravadas])
        } catch {
          // Fica com o que já está na tela; a mensagem de erro cobre o caso.
        }
      }
      return falhas
    },
    [alocacoes],
  )

  return { turmas, professores, alocacoes, carregando, erro, recarregar, salvar }
}
