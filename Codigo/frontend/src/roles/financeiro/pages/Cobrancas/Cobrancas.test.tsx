import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { simularApi, type ChamadaApi } from '../../../../test/api'
import type { Cobranca, PaginaCobrancas, StatusCobranca } from '../../../../types/cobranca'
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

const SITUACOES: StatusCobranca[] = ['PENDENTE', 'EM_ATRASO', 'PAGA', 'CANCELADA']

/** Faz o papel do GET /cobrancas/pagina: filtra, corta a página e conta as situações sem status nem nome. */
function paginaDe(cobrancas: Cobranca[], caminho: string): PaginaCobrancas {
  const params = new URL(caminho, 'http://teste').searchParams
  const pagina = Number(params.get('pagina'))
  const tamanho = Number(params.get('tamanho'))
  const status = params.get('status')
  const nome = params.get('nome')?.toLowerCase()
  const filtradas = cobrancas.filter(
    (c) => (!status || c.status === status) && (!nome || (c.alunoNome ?? '').toLowerCase().includes(nome)),
  )
  return {
    conteudo: filtradas.slice(pagina * tamanho, (pagina + 1) * tamanho),
    pagina,
    tamanho,
    totalElementos: filtradas.length,
    totalPaginas: Math.ceil(filtradas.length / tamanho),
    totaisPorStatus: SITUACOES.map((s) => ({
      status: s,
      quantidade: cobrancas.filter((c) => c.status === s).length,
      valor: 0,
    })),
  }
}

function abrir(cobrancas: Cobranca[]): ChamadaApi[] {
  const chamadas = simularApi(({ caminho }) =>
    caminho.startsWith('/cobrancas/pagina?') ? { corpo: paginaDe(cobrancas, caminho) } : undefined,
  )
  render(
    <MemoryRouter>
      <Cobrancas />
    </MemoryRouter>,
  )
  return chamadas
}

/** Parâmetros da última consulta feita ao backend. */
function ultimaConsulta(chamadas: ChamadaApi[]) {
  return Object.fromEntries(new URL(chamadas[chamadas.length - 1].caminho, 'http://teste').searchParams)
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

  it('pede ao backend só a página que mostra, 10 por vez', async () => {
    const chamadas = abrir(Array.from({ length: 23 }, (_, i) => cobranca(i + 1)))

    expect(await screen.findByText('Aluno 01')).toBeInTheDocument()
    expect(screen.getByText('Aluno 10')).toBeInTheDocument()
    expect(screen.queryByText('Aluno 11')).not.toBeInTheDocument()
    expect(ultimaConsulta(chamadas)).toEqual({ pagina: '0', tamanho: '10' })
    expect(screen.getByText('Página 1 de 3')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /anterior/i })).toBeDisabled()

    await userEvent.click(screen.getByRole('button', { name: /próxima/i }))
    expect(await screen.findByText('Aluno 11')).toBeInTheDocument()
    expect(screen.queryByText('Aluno 01')).not.toBeInTheDocument()
    expect(ultimaConsulta(chamadas)).toMatchObject({ pagina: '1' })

    await userEvent.click(screen.getByRole('button', { name: /próxima/i }))
    expect(await screen.findByText('21–23 de 23')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /próxima/i })).toBeDisabled()
  })

  it('as pílulas contam a escola inteira e o filtro volta para a primeira página', async () => {
    const chamadas = abrir([...Array.from({ length: 12 }, (_, i) => cobranca(i + 1)), cobranca(13, 'PAGA')])

    expect(await screen.findByRole('button', { name: /^todas/i })).toHaveTextContent('13')
    expect(screen.getByRole('button', { name: /^pagas/i })).toHaveTextContent('1')

    await userEvent.click(await screen.findByRole('button', { name: /próxima/i }))
    expect(await screen.findByText('Página 2 de 2')).toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: /^pagas/i }))
    expect(await screen.findByText('Aluno 13')).toBeInTheDocument()
    expect(ultimaConsulta(chamadas)).toEqual({ pagina: '0', tamanho: '10', status: 'PAGA' })
    expect(screen.queryByRole('navigation', { name: /páginas/i })).not.toBeInTheDocument()
  })

  it('a busca pelo nome vai ao backend, volta à primeira página e mantém o filtro', async () => {
    const chamadas = abrir([...Array.from({ length: 15 }, (_, i) => cobranca(i + 1)), cobranca(16, 'PAGA')])

    await userEvent.click(await screen.findByRole('button', { name: /próxima/i }))
    expect(await screen.findByText('Página 2 de 2')).toBeInTheDocument()

    await userEvent.type(screen.getByRole('searchbox', { name: /nome do aluno/i }), 'aluno 1')
    // Aluno 10 estava na página 1: aparece quando a busca, já na primeira página, volta.
    expect(await screen.findByText('Aluno 10')).toBeInTheDocument()
    expect(screen.queryByText('Aluno 09')).not.toBeInTheDocument()
    expect(ultimaConsulta(chamadas)).toEqual({ pagina: '0', tamanho: '10', nome: 'aluno 1' })
    // A digitação espera parar: uma consulta só pelo termo inteiro.
    expect(chamadas.filter((c) => c.caminho.includes('nome='))).toHaveLength(1)
    // As pílulas continuam contando a escola inteira.
    expect(screen.getByRole('button', { name: /^todas/i })).toHaveTextContent('16')

    await userEvent.click(screen.getByRole('button', { name: /^pagas/i }))
    expect(await screen.findByText('Aluno 16')).toBeInTheDocument()
    expect(ultimaConsulta(chamadas)).toEqual({ pagina: '0', tamanho: '10', status: 'PAGA', nome: 'aluno 1' })
  })

  it('a quantidade por página escolhida vai ao backend', async () => {
    const chamadas = abrir(Array.from({ length: 23 }, (_, i) => cobranca(i + 1)))
    await screen.findByText('Aluno 01')

    await userEvent.click(screen.getByRole('combobox', { name: 'Itens por página' }))
    await userEvent.click(screen.getByRole('option', { name: '20' }))

    expect(await screen.findByText('Aluno 20')).toBeInTheDocument()
    expect(ultimaConsulta(chamadas)).toEqual({ pagina: '0', tamanho: '20' })
    expect(screen.getByText('1–20 de 23')).toBeInTheDocument()
  })

  it('avisa quando nada passa no filtro', async () => {
    abrir([cobranca(1)])

    await screen.findByText('Aluno 01')
    await userEvent.type(screen.getByRole('searchbox', { name: /nome do aluno/i }), 'Zuleica')
    expect(await screen.findByText('Nenhuma cobrança encontrada para este filtro.')).toBeInTheDocument()
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
