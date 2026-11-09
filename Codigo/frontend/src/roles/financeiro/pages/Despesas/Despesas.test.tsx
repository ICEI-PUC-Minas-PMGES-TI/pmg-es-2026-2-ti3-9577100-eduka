import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { cortarPagina, problema, simularApi, type ChamadaApi } from '../../../../test/api'
import type { Despesa, ResumoDespesas, SituacaoDespesa } from '../../../../types/despesa'
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

const SITUACOES: SituacaoDespesa[] = ['A_PAGAR', 'PAGA', 'CANCELADA']

/** Faz o papel do GET /despesas/pagina: filtra pelas situações pedidas e soma cada situação sem esse filtro. */
function paginaDe(despesas: Despesa[], caminho: string) {
  const pedidas = new URL(caminho, 'http://teste').searchParams.getAll('situacao')
  const filtradas = despesas.filter((d) => pedidas.length === 0 || pedidas.includes(d.situacao))
  const resumo: ResumoDespesas = {
    porSituacao: SITUACOES.map((situacao) => {
      const daSituacao = despesas.filter((d) => d.situacao === situacao)
      return { situacao, quantidade: daSituacao.length, valor: daSituacao.reduce((soma, d) => soma + d.valor, 0) }
    }),
  }
  return { ...cortarPagina(filtradas, caminho), resumo }
}

function despesa(n: number, situacao: SituacaoDespesa = 'A_PAGAR', valor = 10): Despesa {
  return { ...LUZ, id: `desp-${n}`, descricao: `Despesa ${String(n).padStart(2, '0')}`, situacao, valor }
}

function abrir(despesas: Despesa[]): ChamadaApi[] {
  const chamadas = simularApi(({ metodo, caminho, corpo }) => {
    if (caminho.startsWith('/despesas/pagina?')) return { corpo: paginaDe(despesas, caminho) }
    if (caminho === '/categorias-despesa') return { corpo: [{ id: 'cat-1', nome: 'Contas' }] }
    const marcar = caminho.match(/^\/despesas\/([^/?]+)$/)
    if (marcar && metodo === 'PATCH') {
      const alvo = despesas.find((d) => d.id === marcar[1]) as Despesa
      alvo.situacao = (corpo as { situacao: SituacaoDespesa }).situacao
      return { corpo: alvo }
    }
    return undefined
  })
  render(
    <MemoryRouter>
      <Despesas />
    </MemoryRouter>,
  )
  return chamadas
}

function consultas(chamadas: ChamadaApi[]) {
  return chamadas
    .filter((c) => c.caminho.startsWith('/despesas/pagina?'))
    .map((c) => new URL(c.caminho, 'http://teste').searchParams)
}

describe('Despesas', () => {
  beforeEach(() => {
    Element.prototype.scrollIntoView = vi.fn()
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('pede ao backend só a página do mês atual, sem as canceladas', async () => {
    vi.useFakeTimers({ toFake: ['Date'] })
    vi.setSystemTime(new Date('2026-02-15T15:00:00Z'))
    const chamadas = abrir([LUZ])

    expect(await screen.findByText('Conta de luz')).toBeInTheDocument()
    const [consulta] = consultas(chamadas)
    expect(consulta.get('vencimentoDe')).toBe('2026-02-01')
    expect(consulta.get('vencimentoAte')).toBe('2026-02-28')
    expect(consulta.getAll('situacao')).toEqual(['A_PAGAR', 'PAGA'])
    expect(consulta.get('pagina')).toBe('0')
    expect(consulta.get('tamanho')).toBe('10')
  })

  it('os cards somam o mês no backend e a pílula pede só a situação escolhida', async () => {
    const chamadas = abrir([
      ...Array.from({ length: 11 }, (_, i) => despesa(i + 1, 'A_PAGAR', 10)),
      despesa(12, 'PAGA', 25.5),
      despesa(13, 'CANCELADA', 999),
    ])

    expect(await screen.findByText('Despesa 01')).toBeInTheDocument()
    const resumo = screen.getByRole('region', { name: 'Resumo das despesas' })
    expect(resumo).toHaveTextContent(/Já pago no mês\s*R\$\s*25,50/)
    expect(resumo).toHaveTextContent(/Ainda falta pagar\s*R\$\s*110,00/)
    expect(resumo).toHaveTextContent(/Total do mês\s*R\$\s*135,50/)
    // 12 que não estão canceladas: a página 2 tem 2.
    expect(screen.getByText('1–10 de 12')).toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: 'Pagas' }))
    expect(await screen.findByText('Despesa 12')).toBeInTheDocument()
    expect(screen.queryByText('Despesa 01')).not.toBeInTheDocument()
    expect(consultas(chamadas).at(-1)?.getAll('situacao')).toEqual(['PAGA'])
    // Os cards não mudam com a pílula.
    expect(resumo).toHaveTextContent(/Já pago no mês\s*R\$\s*25,50/)
  })

  it('marcar como paga lê a página e os totais de novo', async () => {
    abrir([despesa(1, 'A_PAGAR', 40)])
    const resumo = await screen.findByRole('region', { name: 'Resumo das despesas' })
    await screen.findByText('Despesa 01')

    await userEvent.click(screen.getByRole('button', { name: 'Marcar como paga' }))

    expect(await screen.findByRole('button', { name: 'Desfazer' })).toBeInTheDocument()
    expect(resumo).toHaveTextContent(/Já pago no mês\s*R\$\s*40,00/)
    expect(resumo).toHaveTextContent(/Ainda falta pagar\s*R\$\s*0,00/)
  })

  it('gerencia as categorias: lista, cadastra e a nova já aparece no lançamento', async () => {
    const chamadas = simularApi(({ metodo, caminho, corpo }) => {
      if (caminho.startsWith('/despesas/pagina?')) return { corpo: paginaDe([LUZ], caminho) }
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

