import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { simularApi } from '../../../../test/api'
import type { Cobranca, StatusCobranca } from '../../../../types/cobranca'
import { Cobrancas } from './Cobrancas'

function cobranca(n: number, status: StatusCobranca = 'PENDENTE'): Cobranca {
  return {
    id: `cob-${n}`,
    alunoId: `aluno-${n}`,
    alunoNome: `Aluno ${String(n).padStart(2, '0')}`,
    alunoNumeroMatricula: `2026${n}`,
    matriculaId: null,
    turmaId: null,
    turmaNome: '6º ano A',
    responsavelId: null,
    responsavelNome: null,
    descricao: 'Mensalidade de outubro',
    valor: 500,
    dataVencimento: '2026-11-10',
    status,
    canceladaEm: null,
    pagaEm: null,
    pagamento: null,
    criadoEm: '2026-10-01T12:00:00Z',
    atualizadoEm: '2026-10-01T12:00:00Z',
  }
}

function abrir(cobrancas: Cobranca[]) {
  simularApi(({ caminho }) => (caminho === '/cobrancas' ? { corpo: cobrancas } : undefined))
  render(
    <MemoryRouter>
      <Cobrancas />
    </MemoryRouter>,
  )
}

/** Botão de abrir/fechar do card, pelo nome do aluno. */
function card(nome: string) {
  return screen.getByRole('button', { name: new RegExp(nome) })
}

describe('Cobrancas', () => {
  // Trocar de página rola até o topo da lista; o jsdom não tem scrollIntoView.
  beforeEach(() => {
    Element.prototype.scrollIntoView = vi.fn()
  })

  it('mostra 10 por página e navega entre as páginas', async () => {
    abrir(Array.from({ length: 23 }, (_, i) => cobranca(i + 1)))

    expect(await screen.findByText('Aluno 01')).toBeInTheDocument()
    expect(screen.getByText('Aluno 10')).toBeInTheDocument()
    expect(screen.queryByText('Aluno 11')).not.toBeInTheDocument()
    expect(screen.getByText('Página 1 de 3')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /anterior/i })).toBeDisabled()

    await userEvent.click(screen.getByRole('button', { name: /próxima/i }))
    expect(screen.getByText('Aluno 11')).toBeInTheDocument()
    expect(screen.queryByText('Aluno 01')).not.toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: /próxima/i }))
    expect(screen.getByText('21–23 de 23')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /próxima/i })).toBeDisabled()
  })

  it('trocar o filtro volta para a primeira página', async () => {
    abrir([...Array.from({ length: 12 }, (_, i) => cobranca(i + 1)), cobranca(13, 'PAGA')])

    await userEvent.click(await screen.findByRole('button', { name: /próxima/i }))
    expect(screen.getByText('Página 2 de 2')).toBeInTheDocument()

    await userEvent.click(screen.getByRole('tab', { name: /pagas/i }))
    expect(screen.getByText('Aluno 13')).toBeInTheDocument()
    expect(screen.queryByRole('navigation', { name: /páginas/i })).not.toBeInTheDocument()
  })

  it('sem paginação com até 10 cobranças', async () => {
    abrir([cobranca(1), cobranca(2)])

    expect(await screen.findByText('Aluno 01')).toBeInTheDocument()
    expect(screen.queryByRole('navigation', { name: /páginas/i })).not.toBeInTheDocument()
  })

  it('a seta abre e fecha os detalhes, um card por vez', async () => {
    abrir([cobranca(1), cobranca(2)])

    await screen.findByText('Aluno 01')
    expect(card('Aluno 01')).toHaveAttribute('aria-expanded', 'false')
    expect(screen.queryByText('Matrícula')).not.toBeInTheDocument()

    await userEvent.click(card('Aluno 01'))
    expect(card('Aluno 01')).toHaveAttribute('aria-expanded', 'true')
    expect(screen.getByText('20261')).toBeInTheDocument()
    expect(screen.getByText('6º ano A')).toBeInTheDocument()

    await userEvent.click(card('Aluno 02'))
    expect(card('Aluno 01')).toHaveAttribute('aria-expanded', 'false')
    expect(card('Aluno 02')).toHaveAttribute('aria-expanded', 'true')

    await userEvent.click(card('Aluno 02'))
    expect(screen.queryByText('Matrícula')).not.toBeInTheDocument()
  })
})
