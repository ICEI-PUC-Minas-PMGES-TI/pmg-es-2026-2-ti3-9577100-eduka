import { useLocation } from 'react-router'
import { paths } from './paths'

// O MASTER usa as telas da secretaria (alunos, matrículas, acadêmico) e do
// financeiro (cobranças, despesas) dentro da área dele, em /master/... As
// páginas são as mesmas: a área sai da URL, e com ela o menu e os links.

/** A tela está aberta dentro da área do MASTER? */
export function ehAreaMaster(pathname: string): boolean {
  return pathname === paths.master.raiz || pathname.startsWith(`${paths.master.raiz}/`)
}

export type RotasFinanceiras = { inicio: string; cobrancas: string; novaCobranca: string; despesas: string }

export type RotasAcademicas = {
  inicio: string
  alunos: string
  professores: string
  academico: string
  alocacoes: string
  matriculas: string
}

const FINANCEIRAS_MASTER: RotasFinanceiras = {
  inicio: paths.master.inicio,
  cobrancas: paths.master.cobrancas,
  novaCobranca: paths.master.novaCobranca,
  despesas: paths.master.despesas,
}

const ACADEMICAS_MASTER: RotasAcademicas = {
  inicio: paths.master.inicio,
  alunos: paths.master.alunos,
  professores: paths.master.professores,
  academico: paths.master.academico,
  alocacoes: paths.master.alocacoes,
  matriculas: paths.master.matriculas,
}

/** Links do financeiro na área em que a tela está (financeiro ou gestão). */
export function useRotasFinanceiras(): RotasFinanceiras {
  return ehAreaMaster(useLocation().pathname) ? FINANCEIRAS_MASTER : paths.financeiro
}

/** Links da secretaria na área em que a tela está (secretaria ou gestão). */
export function useRotasAcademicas(): RotasAcademicas {
  return ehAreaMaster(useLocation().pathname) ? ACADEMICAS_MASTER : paths.secretaria
}

/** Se a tela está na área do MASTER, para escolher o layout. */
export function useAreaMaster(): boolean {
  return ehAreaMaster(useLocation().pathname)
}
