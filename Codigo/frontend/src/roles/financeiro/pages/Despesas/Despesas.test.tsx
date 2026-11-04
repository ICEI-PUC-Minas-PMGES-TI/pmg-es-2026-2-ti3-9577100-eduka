import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { simularApi } from '../../../../test/api'
import type { Despesa } from '../../../../types/despesa'
import { Despesas } from './Despesas'

const LUZ: Despesa = {
  id: 'desp-1',
  categoriaId: 'cat-1',
  categoriaNome: 'Contas',
  descricao: 'Conta de luz',
  valor: 90,
  dataVencimento: '2026-02-10',
  situacao: 'A_PAGAR',
  pagaEm: null,
  canceladaEm: null,
  criadoEm: '2026-02-01T12:00:00Z',
  atualizadoEm: '2026-02-01T12:00:00Z',
}

describe('Despesas', () => {
  afterEach(() => {
    vi.useRealTimers()
  })

  it('pede ao backend só as despesas do mês atual', async () => {
    vi.useFakeTimers({ toFake: ['Date'] })
    vi.setSystemTime(new Date('2026-02-15T15:00:00Z'))
    const chamadas = simularApi(({ caminho }) => {
      if (caminho.startsWith('/despesas?')) return { corpo: [LUZ] }
      if (caminho === '/categorias-despesa') return { corpo: [{ id: 'cat-1', nome: 'Contas' }] }
      return undefined
    })

    render(
      <MemoryRouter>
        <Despesas />
      </MemoryRouter>,
    )

    expect(await screen.findByText('Conta de luz')).toBeInTheDocument()
    expect(chamadas.map((c) => c.caminho)).toContain('/despesas?vencimentoDe=2026-02-01&vencimentoAte=2026-02-28')
  })
})
