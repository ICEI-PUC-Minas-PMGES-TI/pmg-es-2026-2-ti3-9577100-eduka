import { useCallback, useEffect, useRef, useState } from 'react'
import { ApiError } from '../../../services/api'
import { listarAulas, listarMesesDeAulas } from '../../../services/portal/portalProfessorService'
import type { AulaResumo, FiltroDeAulas, MesDeAulas } from '../../../types/academico/aula'

/** Uma disciplina tem bem menos aulas que isto num mês: o mês vem numa página só. */
const AULAS_POR_MES = 100

const MENSAGEM_ERRO = 'Não foi possível carregar o histórico de aulas.'
const MENSAGEM_404 = 'Você não está mais alocado nesta turma/disciplina. Recarregue a página.'

type Estado = {
  /** A consulta (turma, disciplina e filtro) a que estes dados respondem. */
  chave: string
  /** Só a turma e a disciplina da consulta. */
  base: string
  /** Os meses com aula, o mais recente primeiro; null com erro. */
  meses: MesDeAulas[] | null
  /** As aulas de cada mês já carregado, por "AAAA-MM". */
  aulasPorMes: Record<string, AulaResumo[]>
  /** Quantos meses (do mais recente) já foram carregados. */
  carregados: number
  erro: string | null
}

const VAZIO: Estado = { chave: '', base: '', meses: null, aulasPorMes: {}, carregados: 0, erro: null }

function mensagemDeErro(erro: unknown): string {
  if (erro instanceof ApiError && erro.status === 404) return MENSAGEM_404
  return MENSAGEM_ERRO
}

/** As datas do mês ("AAAA-MM") que também estão dentro do filtro. */
function filtroDoMes(mes: string, filtro: FiltroDeAulas): FiltroDeAulas {
  const [ano, numero] = mes.split('-').map(Number)
  const primeiro = `${mes}-01`
  const ultimo = `${mes}-${String(new Date(ano, numero, 0).getDate()).padStart(2, '0')}`
  return {
    de: filtro.de && filtro.de > primeiro ? filtro.de : primeiro,
    ate: filtro.ate && filtro.ate < ultimo ? filtro.ate : ultimo,
    busca: filtro.busca,
  }
}

/**
 * Histórico de aulas da disciplina na turma, mês a mês: busca os meses com
 * aula (com os totais de cada um) e as aulas do mais recente; `carregarMais`
 * traz o mês seguinte. Outra turma, disciplina ou filtro recomeça do primeiro
 * mês; `recarregar` (depois de salvar ou excluir) mantém os meses já abertos.
 *
 * Trocar o filtro ou a busca não esvazia a lista: os dados anteriores ficam na
 * tela (com `atualizando`) até os novos chegarem, sem piscar. Só outra turma
 * ou disciplina volta para `carregando`, para não mostrar aulas de outra turma.
 */
export function useHistoricoDeAulas(turmaId: number | undefined, disciplinaId: number | undefined, filtro: FiltroDeAulas) {
  const { de, ate, busca } = filtro
  const base = turmaId && disciplinaId ? `${turmaId}-${disciplinaId}` : ''
  const chave = base ? JSON.stringify([base, de, ate, busca ?? '']) : ''
  const [estado, setEstado] = useState<Estado>(VAZIO)
  const [versao, setVersao] = useState(0)
  const [carregandoMais, setCarregandoMais] = useState(false)
  // Quantos meses reabrir ao recarregar a mesma consulta; outra consulta começa em um.
  const abertos = useRef({ chave: '', carregados: 1 })

  useEffect(() => {
    if (!turmaId || !disciplinaId) return
    const controller = new AbortController()
    const consulta = { de, ate, busca }
    const quantos = abertos.current.chave === chave ? Math.max(abertos.current.carregados, 1) : 1

    async function carregar() {
      try {
        const meses = await listarMesesDeAulas(turmaId as number, disciplinaId as number, consulta, controller.signal)
        const aCarregar = meses.slice(0, quantos)
        const paginas = await Promise.all(
          aCarregar.map((m) =>
            listarAulas(turmaId as number, disciplinaId as number, 0, AULAS_POR_MES, controller.signal, filtroDoMes(m.mes, consulta)),
          ),
        )
        if (controller.signal.aborted) return
        const aulasPorMes = Object.fromEntries(aCarregar.map((m, i) => [m.mes, paginas[i].conteudo]))
        abertos.current = { chave, carregados: aCarregar.length }
        setEstado({ chave, base, meses, aulasPorMes, carregados: aCarregar.length, erro: null })
      } catch (e) {
        if (controller.signal.aborted) return
        setEstado({ ...VAZIO, chave, base, erro: mensagemDeErro(e) })
      }
    }
    void carregar()
    return () => controller.abort()
  }, [chave, base, versao, turmaId, disciplinaId, de, ate, busca])

  // Mesma turma e disciplina: os dados na tela valem até os do filtro novo chegarem.
  const mesmaTurma = Boolean(base) && estado.base === base
  const atualizando = mesmaTurma && estado.chave !== chave
  const meses = mesmaTurma ? estado.meses : null
  const proximoMes = mesmaTurma && !atualizando && meses ? (meses[estado.carregados] ?? null) : null

  async function carregarMais() {
    if (!proximoMes || !turmaId || !disciplinaId || carregandoMais) return
    setCarregandoMais(true)
    try {
      const pagina = await listarAulas(turmaId, disciplinaId, 0, AULAS_POR_MES, undefined, filtroDoMes(proximoMes.mes, { de, ate, busca }))
      setEstado((anterior) => {
        if (anterior.chave !== chave) return anterior
        const carregados = anterior.carregados + 1
        abertos.current = { chave, carregados }
        return { ...anterior, aulasPorMes: { ...anterior.aulasPorMes, [proximoMes.mes]: pagina.conteudo }, carregados }
      })
    } catch (e) {
      setEstado((anterior) => ({ ...anterior, erro: mensagemDeErro(e) }))
    } finally {
      setCarregandoMais(false)
    }
  }

  const recarregar = useCallback(() => setVersao((v) => v + 1), [])

  return {
    /** Sem nada desta turma e disciplina para mostrar ainda. */
    carregando: Boolean(base) && !mesmaTurma,
    /** Outro filtro ou busca a caminho; a lista na tela ainda é a anterior. */
    atualizando,
    erro: mesmaTurma ? estado.erro : null,
    meses,
    aulasPorMes: mesmaTurma ? estado.aulasPorMes : {},
    carregados: mesmaTurma ? estado.carregados : 0,
    proximoMes,
    carregandoMais,
    carregarMais,
    recarregar,
  }
}
