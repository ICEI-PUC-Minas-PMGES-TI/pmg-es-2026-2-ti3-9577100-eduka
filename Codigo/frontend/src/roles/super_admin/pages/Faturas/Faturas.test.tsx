import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { paths } from '../../../../routes/paths'
import { cortarPagina, parametros, simularApi, type ChamadaApi } from '../../../../test/api'
import type { Escola } from '../../../../types/escola'
import type { FaturaPlataforma } from '../../../../types/faturaPlataforma'
import { Faturas } from './Faturas'

const ESCOLA: Escola = {
  id: 'e1',
  nome: 'Colégio Bandeirantes',
  cnpjCpf: null,
  endereco: null,
  email: null,
  telefone: null,
  ativo: true,
  criadoEm: '2026-01-01',
  plano: null,
  inicioVigencia: null,
  fimVigencia: null,
  limiteUsuarios: null,
  modulos: [],
}

/** Faturas de 2025-01 a 2025-12 e 2026-01: a mais recente primeiro, como o backend ordena. */
function faturas(): FaturaPlataforma[] {
  return Array.from({ length: 13 }, (_, i) => {
    const data = new Date(2025, i, 5)
    const competencia = `${data.getFullYear()}-${String(data.getMonth() + 1).padStart(2, '0')}`
    return {
      id: `f${i}`,
      escolaId: 'e1',
      escolaNome: 'Colégio Bandeirantes',
      competencia,
      descricao: 'Plano Avançado',
      valor: 690,
      dataVencimento: `${competencia}-05`,
      status: 'PENDENTE',
      situacaoPagamento: 'AGUARDANDO_PAGAMENTO',
      formaPagamento: null,
      pagamento: null,
      acoes: ['CANCELAR'],
    } satisfies FaturaPlataforma
  }).reverse()
}

function abrir(lista: FaturaPlataforma[]): ChamadaApi[] {
  const chamadas = simularApi(({ metodo, caminho }) => {
    if (caminho.startsWith('/escolas/e1/faturas/pagina?')) return { corpo: cortarPagina(lista, caminho) }
    if (caminho === '/escolas/e1') return { corpo: ESCOLA }
    const cancelar = caminho.match(/^\/escolas\/e1\/faturas\/([^/]+)\/cancelar$/)
    if (cancelar && metodo === 'PATCH') {
      const alvo = lista.find((f) => f.id === cancelar[1]) as FaturaPlataforma
      alvo.status = 'CANCELADA'
      alvo.acoes = []
      return { corpo: alvo }
    }
    return undefined
  })
  render(
    <MemoryRouter initialEntries={['/super-admin/escolas/e1/faturas']}>
      <Routes>
        <Route path={paths.superAdmin.faturasEscola} element={<Faturas />} />
      </Routes>
    </MemoryRouter>,
  )
  return chamadas
}

function consultas(chamadas: ChamadaApi[]) {
  return chamadas.filter((c) => c.caminho.includes('/faturas/pagina?')).map((c) => parametros(c.caminho))
}

describe('Faturas da escola', () => {
  beforeEach(() => {
    Element.prototype.scrollIntoView = vi.fn()
  })
  afterEach(() => vi.unstubAllGlobals())

  it('pede ao backend só a página que mostra, da competência mais recente', async () => {
    const chamadas = abrir(faturas())

    expect(await screen.findByText('01/2026')).toBeInTheDocument()
    expect(screen.queryByText('03/2025')).not.toBeInTheDocument()
    expect(consultas(chamadas)).toEqual([{ pagina: '0', tamanho: '10' }])

    await userEvent.click(screen.getByRole('button', { name: /próxima/i }))
    expect(await screen.findByText('01/2025')).toBeInTheDocument()
    expect(screen.getByText('11–13 de 13')).toBeInTheDocument()
    expect(consultas(chamadas).at(-1)).toEqual({ pagina: '1', tamanho: '10' })
  })

  it('cancelar lê a mesma página de novo', async () => {
    const chamadas = abrir(faturas())
    const linha = (await screen.findByText('01/2026')).closest('tr') as HTMLElement

    await userEvent.click(within(linha).getByRole('button', { name: /cancelar/i }))
    await userEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Cancelar fatura' }))

    expect(await screen.findByText('Fatura de 2026-01 cancelada.')).toBeInTheDocument()
    expect(await within(linha).findByText('Cancelada')).toBeInTheDocument()
    expect(consultas(chamadas).length).toBeGreaterThanOrEqual(2)
  })

  it('sem fatura, avisa que nenhuma foi lançada', async () => {
    abrir([])

    expect(await screen.findByText('Nenhuma fatura lançada para esta escola.')).toBeInTheDocument()
    expect(screen.queryByRole('navigation', { name: /páginas/i })).not.toBeInTheDocument()
  })
})
