import type { AvaliacaoDoAluno } from '../../../../types/portal/portalAluno'
import { plural } from '../../../../utils/formatters'

// Datas da agenda sempre como "AAAA-MM-DD", contadas em UTC: o fuso do
// navegador não muda o dia.

const UM_DIA_MS = 24 * 60 * 60 * 1000

function emUtc(iso: string): number {
  const [ano, mes, dia] = iso.split('-').map(Number)
  return Date.UTC(ano, mes - 1, dia)
}

/** Dias de `hoje` até `data` (negativo se já passou). */
export function diasAte(hoje: string, data: string): number {
  return Math.round((emUtc(data) - emUtc(hoje)) / UM_DIA_MS)
}

/** "Hoje", "Amanhã", "Em 3 dias", "Ontem", "Há 5 dias". */
export function quando(dias: number): string {
  if (dias === 0) return 'Hoje'
  if (dias === 1) return 'Amanhã'
  if (dias === -1) return 'Ontem'
  if (dias > 1) return `Em ${dias} dias`
  return `Há ${-dias} ${plural(-dias, 'dia', 'dias')}`
}

const DIA_POR_EXTENSO = new Intl.DateTimeFormat('pt-BR', {
  weekday: 'long',
  day: 'numeric',
  month: 'long',
  timeZone: 'UTC',
})

/** "sexta-feira, 27 de novembro". */
export function diaPorExtenso(iso: string): string {
  return DIA_POR_EXTENSO.format(new Date(emUtc(iso)))
}

export type DiaDaAgenda = { data: string; avaliacoes: AvaliacaoDoAluno[] }

/** As avaliações juntas por dia, na ordem em que vieram. */
export function agruparPorDia(avaliacoes: AvaliacaoDoAluno[]): DiaDaAgenda[] {
  const dias: DiaDaAgenda[] = []
  for (const avaliacao of avaliacoes) {
    const ultimo = dias.at(-1)
    if (ultimo?.data === avaliacao.data) ultimo.avaliacoes.push(avaliacao)
    else dias.push({ data: avaliacao.data, avaliacoes: [avaliacao] })
  }
  return dias
}

/** "3 provas · 1 trabalho · 2 tarefas", só com os tipos que aparecem. */
export function contarPorTipo(avaliacoes: AvaliacaoDoAluno[]): string {
  const provas = avaliacoes.filter((a) => a.tipo === 'PROVA').length
  const trabalhos = avaliacoes.filter((a) => a.tipo === 'TRABALHO').length
  const tarefas = avaliacoes.filter((a) => a.tipo === 'TAREFA').length
  return [
    provas > 0 && `${provas} ${plural(provas, 'prova', 'provas')}`,
    trabalhos > 0 && `${trabalhos} ${plural(trabalhos, 'trabalho', 'trabalhos')}`,
    tarefas > 0 && `${tarefas} ${plural(tarefas, 'tarefa', 'tarefas')}`,
  ]
    .filter(Boolean)
    .join(' · ')
}
