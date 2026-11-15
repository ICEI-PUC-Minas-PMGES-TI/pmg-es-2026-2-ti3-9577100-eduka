import { screen, within } from '@testing-library/react'
import type { UserEvent } from '@testing-library/user-event'

/** Rótulo do dia no calendário do CampoData, como o DayPicker escreve em pt-BR. */
export function rotuloDoDia(data: Date): RegExp {
  const mes = new Intl.DateTimeFormat('pt-BR', { month: 'long' }).format(data)
  return new RegExp(`(?<!\\d)${data.getDate()} de ${mes} de ${data.getFullYear()}`, 'i')
}

/** Abre o calendário do campo de data e escolhe o dia (do mês que ele mostra ao abrir). */
export async function escolherDia(usuario: UserEvent, campo: HTMLElement, data: Date): Promise<void> {
  await usuario.click(campo)
  const calendario = screen.getByRole('dialog', { name: 'Escolher data' })
  await usuario.click(within(calendario).getByRole('button', { name: rotuloDoDia(data) }))
}
