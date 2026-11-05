import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { problema, simularApi } from '../../../../test/api'
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

  it('gerencia as categorias: lista, cadastra e a nova já aparece no lançamento', async () => {
    const chamadas = simularApi(({ metodo, caminho, corpo }) => {
      if (caminho.startsWith('/despesas?')) return { corpo: [LUZ] }
      if (caminho === '/categorias-despesa' && metodo === 'GET') return { corpo: [{ id: 'cat-1', nome: 'Contas' }] }
      if (caminho === '/categorias-despesa' && metodo === 'POST') {
        const nome = (corpo as { nome: string }).nome
        return nome === 'Contas' ? problema(409, 'DUPLICADA') : { status: 201, corpo: { id: 'cat-2', nome } }
      }
      return undefined
    })
    render(
      <MemoryRouter>
        <Despesas />
      </MemoryRouter>,
    )
    await screen.findByRole('button', { name: 'Contas' })

    await userEvent.click(screen.getByRole('button', { name: /gerenciar categorias/i }))
    const dialogo = screen.getByRole('dialog')
    expect(within(dialogo).getByRole('list', { name: /categorias da escola/i })).toHaveTextContent('Contas')

    await userEvent.type(within(dialogo).getByLabelText(/nova categoria/i), 'Contas')
    await userEvent.click(within(dialogo).getByRole('button', { name: /adicionar categoria/i }))
    expect(await within(dialogo).findByRole('alert')).toHaveTextContent(/já tem uma categoria/i)

    await userEvent.clear(within(dialogo).getByLabelText(/nova categoria/i))
    await userEvent.type(within(dialogo).getByLabelText(/nova categoria/i), 'Transporte')
    await userEvent.click(within(dialogo).getByRole('button', { name: /adicionar categoria/i }))
    expect(await within(dialogo).findByRole('status')).toHaveTextContent('Categoria "Transporte" criada.')
    expect(chamadas.filter((c) => c.metodo === 'POST')).toHaveLength(2)

    await userEvent.click(within(dialogo).getAllByRole('button', { name: 'Fechar' })[0])
    expect(screen.getByRole('button', { name: 'Transporte' })).toBeInTheDocument()
  })
})

