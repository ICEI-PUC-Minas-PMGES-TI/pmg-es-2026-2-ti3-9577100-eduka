// "Última atividade" no fuso da escola, como o backend conta o mês.

const DIA = new Intl.DateTimeFormat('en-CA', {
  timeZone: 'America/Sao_Paulo',
  year: 'numeric',
  month: '2-digit',
  day: '2-digit',
})

const HORA = new Intl.DateTimeFormat('pt-BR', { timeZone: 'America/Sao_Paulo', hour: '2-digit', minute: '2-digit' })

const DATA = new Intl.DateTimeFormat('pt-BR', {
  timeZone: 'America/Sao_Paulo',
  day: '2-digit',
  month: '2-digit',
  year: 'numeric',
})

/** Dias de calendário entre os dois instantes, no fuso da escola. */
function diasEntre(antes: Date, depois: Date): number {
  const [a, b] = [antes, depois].map((d) => Date.parse(`${DIA.format(d)}T00:00:00Z`))
  return Math.round((b - a) / 86_400_000)
}

/** Quantos dias sem atividade a escola já está; nulo se nunca houve. */
export function diasSemAtividade(ultimaAtividade: string | null, agora: Date = new Date()): number | null {
  return ultimaAtividade === null ? null : Math.max(0, diasEntre(new Date(ultimaAtividade), agora))
}

/** "Hoje às 14:32", "Ontem às 09:10", "Há 5 dias" ou, passado um mês, "12/08/2026". */
export function descreverUltimaAtividade(ultimaAtividade: string | null, agora: Date = new Date()): string {
  if (ultimaAtividade === null) return 'Nenhuma ainda'
  const instante = new Date(ultimaAtividade)
  const dias = diasSemAtividade(ultimaAtividade, agora) ?? 0
  if (dias === 0) return `Hoje às ${HORA.format(instante)}`
  if (dias === 1) return `Ontem às ${HORA.format(instante)}`
  if (dias <= 30) return `Há ${dias} dias`
  return DATA.format(instante)
}
