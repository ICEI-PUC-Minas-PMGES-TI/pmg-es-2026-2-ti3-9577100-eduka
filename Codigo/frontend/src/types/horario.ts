// Grade semanal das turmas (IU10 e IU13). Espelha o HorarioTurmaResponse e o
// DiaSemana do backend: cada aula é de uma alocação (disciplina e professor).

export type DiaSemana = 'SEGUNDA' | 'TERCA' | 'QUARTA' | 'QUINTA' | 'SEXTA' | 'SABADO'

/** Uma aula na grade da turma, como a secretaria monta. */
export type HorarioTurma = {
  id: string
  alocacaoId: string
  turmaId: number
  diaSemana: DiaSemana
  /** "HH:mm" ou "HH:mm:ss", como o backend manda o LocalTime. */
  horaInicio: string
  horaFim: string
  disciplinaId: number
  disciplinaNome: string
  professorId: string
  professorNome: string
}

/** Uma aula da disciplina, como vem no portal do professor. */
export type HorarioDaDisciplina = Pick<HorarioTurma, 'diaSemana' | 'horaInicio' | 'horaFim'>

export const DIAS_SEMANA: { dia: DiaSemana; curto: string; longo: string }[] = [
  { dia: 'SEGUNDA', curto: 'Seg', longo: 'Segunda' },
  { dia: 'TERCA', curto: 'Ter', longo: 'Terça' },
  { dia: 'QUARTA', curto: 'Qua', longo: 'Quarta' },
  { dia: 'QUINTA', curto: 'Qui', longo: 'Quinta' },
  { dia: 'SEXTA', curto: 'Sex', longo: 'Sexta' },
  { dia: 'SABADO', curto: 'Sáb', longo: 'Sábado' },
]

/** Posição do dia na semana, para ordenar. */
export function ordemDoDia(dia: DiaSemana): number {
  return DIAS_SEMANA.findIndex((d) => d.dia === dia)
}

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
 * Horas de início diferentes ficam separadas: "Qui · 07h30 · Ter · 09h20".
 */
export function descreverHorarios(horarios: HorarioDaDisciplina[]): string | null {
  if (horarios.length === 0) return null
  const porHora = new Map<string, DiaSemana[]>()
  for (const h of [...horarios].sort((a, b) => ordemDoDia(a.diaSemana) - ordemDoDia(b.diaSemana))) {
    const chave = horaCurta(h.horaInicio)
    porHora.set(chave, [...(porHora.get(chave) ?? []), h.diaSemana])
  }
  return [...porHora]
    .sort(([a], [b]) => a.localeCompare(b))
    .map(([hora, dias]) => {
      const nomes = dias.map((d) => DIAS_SEMANA[ordemDoDia(d)]?.curto ?? d)
      const lista = nomes.length > 1 ? `${nomes.slice(0, -1).join(', ')} e ${nomes[nomes.length - 1]}` : nomes[0]
      return `${lista} · ${formatarHora(hora)}`
    })
    .join(' · ')
}
