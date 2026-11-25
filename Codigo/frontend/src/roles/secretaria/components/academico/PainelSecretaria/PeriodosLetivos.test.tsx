import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { PeriodoLetivo } from '../../../../../types/academico/academico'
import { PeriodosLetivos } from './PeriodosLetivos'
import { situacao } from './situacaoPeriodo'

function periodo(id: number, nome: string, inicio: string, fim: string, extra: Partial<PeriodoLetivo> = {}): PeriodoLetivo {
  return { id, nome, dataInicio: inicio, dataFim: fim, ativo: true, matriculasAbertas: true, encerradoEm: null, ...extra }
}

const EM_ANDAMENTO = periodo(1, '2026/2', '2026-08-01', '2026-12-18')
const PLANEJADO = periodo(2, '2027/1', '2027-02-01', '2027-06-30', { matriculasAbertas: false })
const ENCERRADO = periodo(3, '2026/1', '2026-02-01', '2026-06-30')

function renderizar(periodos: PeriodoLetivo[]) {
  const props = {
    onCadastrar: vi.fn(),
    onAtualizar: vi.fn(),
    onExcluir: vi.fn(),
    onEncerrar: vi.fn(async (id: number) => ({ ...EM_ANDAMENTO, id, encerradoEm: '2026-11-02' })),
    onAbrirMatriculas: vi.fn(async (id: number) => ({ ...PLANEJADO, id, matriculasAbertas: true })),
  }
  render(<PeriodosLetivos periodos={periodos} {...props} />)
  return props
}

describe('PeriodosLetivos', () => {
  beforeEach(() => {
    vi.useFakeTimers({ toFake: ['Date'] })
    vi.setSystemTime(new Date('2026-11-02T15:00:00Z'))
    Element.prototype.scrollIntoView = vi.fn()
  })

  it('a situação considera o encerramento feito pela secretaria', () => {
    expect(situacao(EM_ANDAMENTO, '2026-11-02').fase).toBe('andamento')
    expect(situacao({ ...EM_ANDAMENTO, encerradoEm: '2026-11-01' }, '2026-11-02').fase).toBe('encerrado')
    expect(situacao(PLANEJADO, '2026-11-02').fase).toBe('planejado')
    expect(situacao(ENCERRADO, '2026-11-02').fase).toBe('encerrado')
  })

  it('encerra o período em andamento depois de confirmar', async () => {
    const { onEncerrar } = renderizar([EM_ANDAMENTO, PLANEJADO, ENCERRADO])

    // Encerrar fica no "⋯" e só no período em andamento.
    await userEvent.click(screen.getByRole('button', { name: 'Mais ações para 2026/1' }))
    expect(screen.queryByRole('menuitem', { name: /^encerrar/i })).not.toBeInTheDocument()
    await userEvent.keyboard('{Escape}')

    await userEvent.click(screen.getByRole('button', { name: 'Mais ações para 2026/2' }))
    await userEvent.click(screen.getByRole('menuitem', { name: /^encerrar/i }))
    await userEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: /encerrar período/i }))

    expect(onEncerrar).toHaveBeenCalledWith(1)
    expect(await screen.findByText('Período "2026/2" encerrado.')).toBeInTheDocument()
  })

  it('abre as matrículas do período planejado que ainda está fechado', async () => {
    const { onAbrirMatriculas } = renderizar([EM_ANDAMENTO, PLANEJADO])

    expect(screen.getByText('Matrículas fechadas')).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: /abrir matrículas/i }))

    expect(onAbrirMatriculas).toHaveBeenCalledWith(2)
    expect(await screen.findByText('Matrículas do período "2027/1" abertas.')).toBeInTheDocument()
  })

  it('planejado já aberto não oferece abrir de novo', () => {
    renderizar([{ ...PLANEJADO, matriculasAbertas: true }])

    expect(screen.getByText('Matrículas abertas')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /abrir matrículas/i })).not.toBeInTheDocument()
  })
})
