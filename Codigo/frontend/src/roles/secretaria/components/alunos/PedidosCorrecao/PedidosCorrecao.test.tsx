import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { simularApi } from '../../../../../test/api'
import type { SolicitacaoCorrecao } from '../../../../../types/pessoas/solicitacaoCorrecao'
import { PedidosCorrecao } from './PedidosCorrecao'

const PEDIDO: SolicitacaoCorrecao = {
  id: 'pedido-1',
  alunoId: 'aluno-1',
  alunoNome: 'Lucas Andrade',
  alunoMatricula: '2026-0412',
  mensagem: 'Meu telefone mudou',
  status: 'ABERTA',
  criadoEm: '2026-10-30T12:00:00Z',
  atendidaEm: null,
}

describe('PedidosCorrecao', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('lista os pedidos abertos e marca como atendido', async () => {
    let abertos = [PEDIDO]
    const chamadas = simularApi(({ metodo, caminho }) => {
      if (caminho === '/alunos/correcoes') return { corpo: abertos }
      if (metodo === 'PATCH' && caminho === '/alunos/correcoes/pedido-1/atender') {
        abertos = []
        return { corpo: { ...PEDIDO, status: 'ATENDIDA', atendidaEm: '2026-10-31T10:00:00Z' } }
      }
      return undefined
    })
    render(<PedidosCorrecao onVerAlunos={vi.fn()} />)

    const lista = await screen.findByRole('list', { name: /pedidos de correção abertos/i })
    expect(within(lista).getByText('Lucas Andrade · 2026-0412')).toBeInTheDocument()
    expect(within(lista).getByText('Meu telefone mudou')).toBeInTheDocument()
    expect(within(lista).getByText('Enviado em 30/10/2026')).toBeInTheDocument()

    await userEvent.click(within(lista).getByRole('button', { name: /marcar como atendido/i }))

    expect(await screen.findByText(/nenhum pedido aguardando/i)).toBeInTheDocument()
    expect(chamadas.some((c) => c.metodo === 'PATCH')).toBe(true)
  })

  it('o atalho leva à tela de alunos', async () => {
    simularApi(({ caminho }) => (caminho === '/alunos/correcoes' ? { corpo: [] } : undefined))
    const onVerAlunos = vi.fn()
    render(<PedidosCorrecao onVerAlunos={onVerAlunos} />)

    await screen.findByText(/nenhum pedido aguardando/i)
    await userEvent.click(screen.getByRole('button', { name: /ir para alunos/i }))
    expect(onVerAlunos).toHaveBeenCalled()
  })
})
