// Horário das aulas (IU10 e IU13). Espelha o HorarioAulaResponse do backend.
// diaSemana segue a ISO-8601: 1 = segunda ... 6 = sábado.

export type HorarioAula = {
  disciplinaId: number
  diaSemana: number
  /** "HH:mm" ou "HH:mm:ss", como o backend manda o LocalTime. */
  horaInicio: string
}

export const DIAS_SEMANA: { dia: number; curto: string; longo: string }[] = [
  { dia: 1, curto: 'Seg', longo: 'Segunda' },
  { dia: 2, curto: 'Ter', longo: 'Terça' },
  { dia: 3, curto: 'Qua', longo: 'Quarta' },
  { dia: 4, curto: 'Qui', longo: 'Quinta' },
  { dia: 5, curto: 'Sex', longo: 'Sexta' },
  { dia: 6, curto: 'Sáb', longo: 'Sábado' },
]

/** "07:30:00" -> "07h30". */
export function formatarHora(hora: string): string {
  const [h, m] = hora.split(':')
  return `${h}h${m}`
}

/** "07:30:00" -> "07:30", para comparar e mandar ao backend. */
export function horaCurta(hora: string): string {
  return hora.slice(0, 5)
}

/**
 * Resumo do horário de uma disciplina, como na IU13: "Seg, Qua e Sex · 07h30".
 * Horas diferentes ficam separadas: "Seg · 07h30 · Qua · 09h20".
 */
export function descreverHorarios(horarios: HorarioAula[]): string | null {
  if (horarios.length === 0) return null
  const porHora = new Map<string, number[]>()
  for (const h of [...horarios].sort((a, b) => a.diaSemana - b.diaSemana)) {
    const chave = horaCurta(h.horaInicio)
    porHora.set(chave, [...(porHora.get(chave) ?? []), h.diaSemana])
  }
  return [...porHora]
    .sort(([a], [b]) => a.localeCompare(b))
    .map(([hora, dias]) => {
      const nomes = dias.map((d) => DIAS_SEMANA.find((x) => x.dia === d)?.curto ?? String(d))
      const lista = nomes.length > 1 ? `${nomes.slice(0, -1).join(', ')} e ${nomes[nomes.length - 1]}` : nomes[0]
      return `${lista} · ${formatarHora(hora)}`
    })
    .join(' · ')
}
