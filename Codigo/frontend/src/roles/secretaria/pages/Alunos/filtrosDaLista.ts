import type { ConsultaAlunos } from '../../../../services/pessoas/alunoService'
import type { FaixaAtraso, SituacaoFinanceira, SituacaoNaEscola } from '../../../../types/pessoas/aluno'

// Os filtros da tela Alunos ficam na URL: o atalho do Início chega com o
// filtro aplicado (ex.: ?financeiro=EM_ATRASO) e recarregar a página não os
// perde. A busca por nome fica fora: muda a cada tecla.

const SITUACOES: readonly SituacaoNaEscola[] = ['MATRICULADO', 'TRANCADO', 'SEM_TURMA', 'INATIVO', 'ANONIMIZADO']
export const SITUACOES_FINANCEIRAS: readonly SituacaoFinanceira[] = ['EM_ATRASO', 'A_VENCER', 'EM_DIA', 'SEM_COBRANCA']
export const FAIXAS_ATRASO: readonly FaixaAtraso[] = ['ATE_30_DIAS', 'DE_31_A_60_DIAS', 'MAIS_DE_60_DIAS']

/** Parâmetros da URL da tela (não os do backend). */
export const PARAMETRO = {
  situacao: 'situacao',
  financeiro: 'financeiro',
  atraso: 'atraso',
  venceEm7Dias: 'vence7',
  semMensalidade: 'semMensalidade',
  ordem: 'ordem',
  direcao: 'direcao',
} as const

export type OrdemDaLista = { campo: 'NOME' } | { campo: 'EM_ABERTO'; direcao: 'ASC' | 'DESC' }

export type FiltrosDaLista = {
  situacao: SituacaoNaEscola | null
  financeiro: SituacaoFinanceira | null
  /** Só com `financeiro` EM_ATRASO. */
  faixaAtraso: FaixaAtraso | null
  venceEm7Dias: boolean
  semMensalidade: boolean
  ordem: OrdemDaLista
}

function umDe<T extends string>(valores: readonly T[], valor: string | null): T | null {
  return valores.find((v) => v === valor) ?? null
}

/**
 * Os filtros que valem, lidos da URL. Valor desconhecido vira "sem filtro".
 * Sem `veFinanceiro` (SECRETARIA, ou escola sem a gestão financeira), os
 * filtros e a ordem financeiros são ignorados: o backend os recusaria.
 */
export function lerFiltros(params: URLSearchParams, veFinanceiro: boolean): FiltrosDaLista {
  const situacao = umDe(SITUACOES, params.get(PARAMETRO.situacao))
  if (!veFinanceiro) {
    return {
      situacao,
      financeiro: null,
      faixaAtraso: null,
      venceEm7Dias: false,
      semMensalidade: false,
      ordem: { campo: 'NOME' },
    }
  }
  const financeiro = umDe(SITUACOES_FINANCEIRAS, params.get(PARAMETRO.financeiro))
  const porValor = params.get(PARAMETRO.ordem) === 'EM_ABERTO'
  return {
    situacao,
    financeiro,
    faixaAtraso: financeiro === 'EM_ATRASO' ? umDe(FAIXAS_ATRASO, params.get(PARAMETRO.atraso)) : null,
    venceEm7Dias: params.get(PARAMETRO.venceEm7Dias) === '1',
    semMensalidade: params.get(PARAMETRO.semMensalidade) === '1',
    ordem: porValor
      ? { campo: 'EM_ABERTO', direcao: params.get(PARAMETRO.direcao) === 'ASC' ? 'ASC' : 'DESC' }
      : { campo: 'NOME' },
  }
}

/** A URL com os filtros; os vazios saem dela. Mantém os outros parâmetros que houver. */
export function gravarFiltros(atuais: URLSearchParams, filtros: FiltrosDaLista): URLSearchParams {
  const params = new URLSearchParams(atuais)
  const definir = (nome: string, valor: string | null) => (valor ? params.set(nome, valor) : params.delete(nome))
  definir(PARAMETRO.situacao, filtros.situacao)
  definir(PARAMETRO.financeiro, filtros.financeiro)
  definir(PARAMETRO.atraso, filtros.financeiro === 'EM_ATRASO' ? filtros.faixaAtraso : null)
  definir(PARAMETRO.venceEm7Dias, filtros.venceEm7Dias ? '1' : null)
  definir(PARAMETRO.semMensalidade, filtros.semMensalidade ? '1' : null)
  definir(PARAMETRO.ordem, filtros.ordem.campo === 'EM_ABERTO' ? 'EM_ABERTO' : null)
  definir(PARAMETRO.direcao, filtros.ordem.campo === 'EM_ABERTO' && filtros.ordem.direcao === 'ASC' ? 'ASC' : null)
  return params
}

/** Os filtros na forma que o backend recebe (GET /alunos/pagina). */
export function consultaDosFiltros(
  filtros: FiltrosDaLista,
): Omit<ConsultaAlunos, 'pagina' | 'tamanho' | 'busca'> {
  return {
    situacao: filtros.situacao ?? undefined,
    situacaoFinanceira: filtros.financeiro ?? undefined,
    faixaAtraso: filtros.faixaAtraso ?? undefined,
    venceEm7Dias: filtros.venceEm7Dias || undefined,
    semMensalidadeNoMes: filtros.semMensalidade || undefined,
    ...(filtros.ordem.campo === 'EM_ABERTO'
      ? { ordenarPor: 'EM_ABERTO' as const, direcao: filtros.ordem.direcao }
      : {}),
  }
}

/** Se algum filtro está aplicado (a ordem não conta). */
export function temFiltro(filtros: FiltrosDaLista): boolean {
  return Boolean(filtros.situacao || filtros.financeiro || filtros.venceEm7Dias || filtros.semMensalidade)
}

/** Próxima ordem ao clicar em "Em aberto": maior primeiro, menor primeiro, e volta ao nome. */
export function proximaOrdem(atual: OrdemDaLista): OrdemDaLista {
  if (atual.campo === 'NOME') return { campo: 'EM_ABERTO', direcao: 'DESC' }
  return atual.direcao === 'DESC' ? { campo: 'EM_ABERTO', direcao: 'ASC' } : { campo: 'NOME' }
}
