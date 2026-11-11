import type { StatusCobrancaAluno } from '../../types/pagamentoAluno'
import type { BoletimDisciplina, FrequenciaBoletim } from '../../types/portalAluno'
import { hojeIso, plural } from '../../utils/formatters'
import { atingiuMedia } from '../professor/notas'

/** Aproveitamento em %, ou null quando ainda não há nada avaliado. */
export function aproveitamento(obtidos: number, avaliados: number): number | null {
  return avaliados > 0 ? Math.round((obtidos / avaliados) * 100) : null
}

/**
 * Soma a frequência de várias turmas (o aluno pode ter mais de uma ativa) com a mesma regra do backend:
 * alerta só quando as faltas passam do limite, ou seja, acima dele e nunca no limite exato.
 * Null quando nenhuma teve aula com chamada.
 */
export function somarFrequencias(
  frequencias: (FrequenciaBoletim | null)[],
  limiteFaltasPercentual: number,
): FrequenciaBoletim | null {
  const dadas = frequencias.filter((f): f is FrequenciaBoletim => f !== null)
  const totalAulas = dadas.reduce((soma, f) => soma + f.totalAulas, 0)
  if (totalAulas === 0) return null
  const faltas = dadas.reduce((soma, f) => soma + f.faltas, 0)
  return {
    totalAulas,
    faltas,
    percentualFrequencia: Math.round(((totalAulas - faltas) / totalAulas) * 1000) / 10,
    alertaFaltas: faltas * 100 > totalAulas * limiteFaltasPercentual,
  }
}

/**
 * Linha digitável de boleto (47 dígitos) nos blocos do banco:
 * 00000.00000 00000.000000 00000.000000 0 00000000000000. Fora disso, devolve como veio.
 */
export function formatarLinhaDigitavel(linha: string): string {
  const d = linha.replace(/\D/g, '')
  if (d.length !== 47) return linha
  return `${d.slice(0, 5)}.${d.slice(5, 10)} ${d.slice(10, 15)}.${d.slice(15, 21)} ${d.slice(21, 26)}.${d.slice(26, 32)} ${d.slice(32, 33)} ${d.slice(33)}`
}

/** Disciplina com nota abaixo da média nos pontos já avaliados; sem nota ainda, não conta. */
export function emRecuperacao(disciplina: BoletimDisciplina): boolean {
  return disciplina.pontosAvaliados > 0 && !atingiuMedia(disciplina.pontosObtidos, disciplina.pontosAvaliados)
}

/** Cor do badge de cada situação da cobrança. */
export const TOM_STATUS_COBRANCA: Record<StatusCobrancaAluno, 'success' | 'danger' | 'neutral' | 'warning'> = {
  PENDENTE: 'warning',
  EM_ATRASO: 'danger',
  PAGA: 'success',
  CANCELADA: 'neutral',
}

const UM_DIA_MS = 24 * 60 * 60 * 1000

/** "Vence hoje", "Vence em 3 dias", "Venceu há 2 dias" (datas ISO, sem fuso). */
export function prazoDoVencimento(vencimento: string): string {
  const dias = Math.round((Date.parse(vencimento) - Date.parse(hojeIso())) / UM_DIA_MS)
  if (dias === 0) return 'Vence hoje'
  if (dias > 0) return `Vence em ${dias} ${plural(dias, 'dia', 'dias')}`
  return `Venceu há ${-dias} ${plural(-dias, 'dia', 'dias')}`
}
