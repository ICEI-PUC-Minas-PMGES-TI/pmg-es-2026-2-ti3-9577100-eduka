import type {
  AvaliacaoDoAluno,
  BoletimTurma,
  FrequenciaBoletim,
} from '../../../../types/portal/portalAluno'
import { atingiuMedia } from '../../../professor/notas'
import { somarFrequencias } from '../../formatacao'

// Boletim do aluno por ano letivo e bimestre. O backend manda uma entrada por
// turma (com os bimestres de cada uma); aqui as turmas do mesmo ano viram um
// boletim só: no ano com dois semestres, o 1º semestre traz o 1º e o 2º
// bimestre e o 2º semestre, o 3º e o 4º.

/** Pontos de um conjunto de avaliações. */
export type Pontos = {
  /** Soma das notas lançadas. */
  obtidos: number
  /** Soma do valor das avaliações que já têm nota. */
  avaliados: number
  /** Soma do valor de todas as avaliações. */
  distribuidos: number
}

export type PontosEFrequencia = Pontos & { frequencia: FrequenciaBoletim | null }

export type BimestreDoAno = {
  numero: number
  /** ISO (yyyy-MM-dd). */
  inicio: string
  /** ISO (yyyy-MM-dd). */
  fim: string
}

export type AnoLetivo = {
  /** Identifica o ano na URL: o ano ("2026") ou, sem datas, o nome do período. */
  chave: string
  rotulo: string
  /** Na ordem do backend: as ativas primeiro. */
  turmas: BoletimTurma[]
  /** Os bimestres das turmas do ano, do 1º ao 4º. */
  bimestres: BimestreDoAno[]
  /** Tem turma em que o aluno está cursando agora. */
  emCurso: boolean
  limiteFaltasPercentual: number
}

/** Uma linha do quadro de notas: a disciplina no ano todo e em cada bimestre. */
export type LinhaDisciplina = {
  disciplinaId: number
  nome: string
  professorNome: string | null
  total: Pontos
  frequencia: FrequenciaBoletim | null
  /** Pelo número do bimestre; o bimestre sem nada não aparece. */
  bimestres: Record<number, PontosEFrequencia>
  /** Por data. */
  avaliacoes: AvaliacaoDoAluno[]
}

export type Situacao = 'sem-notas' | 'na-media' | 'abaixo'

export const ZERO: Pontos = { obtidos: 0, avaliados: 0, distribuidos: 0 }

function somar(a: Pontos, b: Pontos): Pontos {
  return {
    obtidos: a.obtidos + b.obtidos,
    avaliados: a.avaliados + b.avaliados,
    distribuidos: a.distribuidos + b.distribuidos,
  }
}

function chaveDoAno(turma: BoletimTurma): string {
  return turma.ano !== null ? String(turma.ano) : `periodo:${turma.periodoLetivoNome}`
}

/**
 * As turmas agrupadas por ano letivo, do mais recente ao mais antigo; turma
 * de período sem datas fica num grupo com o nome do período, no fim.
 */
export function agruparPorAno(turmas: BoletimTurma[]): AnoLetivo[] {
  const anos = new Map<string, AnoLetivo>()
  for (const turma of turmas) {
    const chave = chaveDoAno(turma)
    let ano = anos.get(chave)
    if (!ano) {
      ano = {
        chave,
        rotulo: turma.ano !== null ? String(turma.ano) : turma.periodoLetivoNome,
        turmas: [],
        bimestres: [],
        emCurso: false,
        limiteFaltasPercentual: turma.limiteFaltasPercentual,
      }
      anos.set(chave, ano)
    }
    ano.turmas.push(turma)
    ano.emCurso ||= turma.situacaoMatricula === 'ATIVA'
    for (const b of turma.bimestres) {
      const existente = ano.bimestres.find((x) => x.numero === b.numero)
      if (!existente) {
        ano.bimestres.push({ numero: b.numero, inicio: b.inicio, fim: b.fim })
      } else {
        // Duas turmas no mesmo bimestre (ex.: trocou de turma): o bimestre cobre as duas.
        if (b.inicio < existente.inicio) existente.inicio = b.inicio
        if (b.fim > existente.fim) existente.fim = b.fim
      }
    }
  }
  const lista = [...anos.values()]
  lista.forEach((ano) => ano.bimestres.sort((a, b) => a.numero - b.numero))
  return lista.sort((a, b) => {
    const anoA = a.turmas[0].ano
    const anoB = b.turmas[0].ano
    if (anoA === null || anoB === null) return anoA === null ? (anoB === null ? 0 : 1) : -1
    return anoB - anoA
  })
}

/**
 * Quadro de notas do ano: uma linha por disciplina (a mesma disciplina em
 * duas turmas do ano vira uma linha só), por nome.
 */
export function quadroDoAno(ano: AnoLetivo): LinhaDisciplina[] {
  const linhas = new Map<number, LinhaDisciplina>()
  const frequencias = new Map<number, (FrequenciaBoletim | null)[]>()
  const frequenciasPorBimestre = new Map<string, (FrequenciaBoletim | null)[]>()

  for (const turma of ano.turmas) {
    for (const d of turma.disciplinas) {
      let linha = linhas.get(d.disciplinaId)
      if (!linha) {
        linha = {
          disciplinaId: d.disciplinaId,
          nome: d.disciplinaNome,
          professorNome: d.professorNome,
          total: ZERO,
          frequencia: null,
          bimestres: {},
          avaliacoes: [],
        }
        linhas.set(d.disciplinaId, linha)
      }
      // Turmas ativas vêm primeiro: o professor de agora fica.
      linha.professorNome ??= d.professorNome
      linha.total = somar(linha.total, {
        obtidos: d.pontosObtidos,
        avaliados: d.pontosAvaliados,
        distribuidos: d.pontosDistribuidos,
      })
      linha.avaliacoes.push(...d.avaliacoes)
      frequencias.set(d.disciplinaId, [...(frequencias.get(d.disciplinaId) ?? []), d.frequencia])
      for (const b of d.bimestres) {
        const atual = linha.bimestres[b.numero] ?? { ...ZERO, frequencia: null }
        linha.bimestres[b.numero] = {
          ...somar(atual, { obtidos: b.pontosObtidos, avaliados: b.pontosAvaliados, distribuidos: b.pontosDistribuidos }),
          frequencia: null,
        }
        const chave = `${d.disciplinaId}:${b.numero}`
        frequenciasPorBimestre.set(chave, [...(frequenciasPorBimestre.get(chave) ?? []), b.frequencia])
      }
    }
  }

  for (const linha of linhas.values()) {
    linha.frequencia = somarFrequencias(frequencias.get(linha.disciplinaId) ?? [], ano.limiteFaltasPercentual)
    for (const numero of Object.keys(linha.bimestres).map(Number)) {
      linha.bimestres[numero].frequencia = somarFrequencias(
        frequenciasPorBimestre.get(`${linha.disciplinaId}:${numero}`) ?? [],
        ano.limiteFaltasPercentual,
      )
    }
    linha.avaliacoes.sort((a, b) => a.data.localeCompare(b.data) || a.titulo.localeCompare(b.titulo, 'pt-BR'))
  }
  return [...linhas.values()].sort((a, b) => a.nome.localeCompare(b.nome, 'pt-BR'))
}

/** Pontos e frequência da disciplina no ano todo (bimestre nulo) ou num bimestre. */
export function noRecorte(linha: LinhaDisciplina, bimestre: number | null): PontosEFrequencia {
  if (bimestre === null) return { ...linha.total, frequencia: linha.frequencia }
  return linha.bimestres[bimestre] ?? { ...ZERO, frequencia: null }
}

/** Avaliações da disciplina no ano todo ou num bimestre. */
export function avaliacoesNoRecorte(linha: LinhaDisciplina, bimestre: number | null): AvaliacaoDoAluno[] {
  return bimestre === null ? linha.avaliacoes : linha.avaliacoes.filter((a) => a.bimestre === bimestre)
}

/** Sem nota ainda, na média (60% do que já foi avaliado) ou abaixo dela. */
export function situacao(pontos: Pontos): Situacao {
  if (pontos.avaliados <= 0) return 'sem-notas'
  return atingiuMedia(pontos.obtidos, pontos.avaliados) ? 'na-media' : 'abaixo'
}

export type ResumoDoRecorte = {
  pontos: Pontos
  frequencia: FrequenciaBoletim | null
  /** Disciplinas abaixo da média no recorte. */
  abaixoDaMedia: LinhaDisciplina[]
  avaliacoes: number
  avaliacoesComNota: number
}

/** Os números do topo do boletim, para o ano todo ou um bimestre. */
export function resumir(ano: AnoLetivo, linhas: LinhaDisciplina[], bimestre: number | null): ResumoDoRecorte {
  const pontos = linhas.reduce((soma, l) => somar(soma, noRecorte(l, bimestre)), ZERO)
  const frequencia =
    bimestre === null
      ? somarFrequencias(
          ano.turmas.map((t) => t.frequencia),
          ano.limiteFaltasPercentual,
        )
      : somarFrequencias(
          ano.turmas.flatMap((t) => t.bimestres.filter((b) => b.numero === bimestre).map((b) => b.frequencia)),
          ano.limiteFaltasPercentual,
        )
  const avaliacoes = linhas.flatMap((l) => avaliacoesNoRecorte(l, bimestre))
  return {
    pontos,
    frequencia,
    abaixoDaMedia: linhas.filter((l) => situacao(noRecorte(l, bimestre)) === 'abaixo'),
    avaliacoes: avaliacoes.length,
    avaliacoesComNota: avaliacoes.filter((a) => a.nota !== null).length,
  }
}

/** O bimestre em que a data cai, se ela está dentro de algum. */
export function bimestreDaData(ano: AnoLetivo, dataIso: string): number | null {
  return ano.bimestres.find((b) => b.inicio <= dataIso && dataIso <= b.fim)?.numero ?? null
}
