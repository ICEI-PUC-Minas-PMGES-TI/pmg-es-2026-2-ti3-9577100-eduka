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
  responsavel: 'Administração',
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
    if (caminho === '/despesas' && metodo === 'POST') {
      const nova = { ...LUZ, ...(corpo as Partial<Despesa>), id: 'desp-nova', situacao: 'A_PAGAR' as const }
      despesas.push(nova)
      return { status: 201, corpo: nova }
    }
    const marcar = caminho.match(/^\/despesas\/([^/?]+)$/)
    if (marcar && metodo === 'PUT') {
      const alvo = despesas.find((d) => d.id === marcar[1]) as Despesa
      if (alvo.situacao === 'CANCELADA') return problema(400, 'ERRO', 'Despesa CANCELADA nao pode ser editada')
      Object.assign(alvo, corpo, { atualizadoEm: '2026-02-16T12:00:00Z' })
      return { corpo: alvo }
    }
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

  it('mostra o responsável de cada despesa, e um traço na antiga que não tem', async () => {
    abrir([LUZ, { ...despesa(2), responsavel: null }])

    const linhaLuz = (await screen.findByText('Conta de luz')).closest('tr') as HTMLElement
    expect(screen.getByRole('columnheader', { name: 'Responsável' })).toBeInTheDocument()
    expect(within(linhaLuz).getByText('Administração')).toBeInTheDocument()
    const linhaAntiga = screen.getByText('Despesa 02').closest('tr') as HTMLElement
    expect(within(linhaAntiga).getByText('—')).toBeInTheDocument()
  })

  it('lançar exige o responsável e manda o nome sem espaços nas pontas', async () => {
    vi.useFakeTimers({ toFake: ['Date'] })
    vi.setSystemTime(new Date('2026-02-15T15:00:00Z'))
    const chamadas = abrir([])
    await screen.findByRole('button', { name: 'Contas' })

    await userEvent.type(screen.getByLabelText('O que foi pago'), 'Conta de água')
    await userEvent.type(screen.getByLabelText('Valor'), '120,50')
    await userEvent.type(screen.getByLabelText('Responsável'), '   ')
    await userEvent.click(screen.getByRole('button', { name: 'Salvar despesa' }))

    expect(screen.getByRole('alert')).toHaveTextContent('Informe o responsável pela despesa.')
    expect(screen.getByLabelText('Responsável')).toHaveAttribute('aria-invalid', 'true')
    expect(screen.getByLabelText('Responsável')).toHaveFocus()
    expect(chamadas.some((c) => c.metodo === 'POST')).toBe(false)

    await userEvent.type(screen.getByLabelText('Responsável'), 'Zelador Antônio ')
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Salvar despesa' }))

    expect(await screen.findByRole('status')).toHaveTextContent('Despesa "Conta de água" salva.')
    const post = chamadas.find((c) => c.metodo === 'POST')
    expect(post?.corpo).toMatchObject({ descricao: 'Conta de água', responsavel: 'Zelador Antônio', valor: 120.5 })
    // Fica preenchido para o próximo lançamento, como a categoria e a data.
    expect(screen.getByLabelText('Responsável')).toHaveValue('   Zelador Antônio ')
  })

  it('edita a despesa pelo modal, manda tudo menos a situação e lê a lista de novo', async () => {
    vi.useFakeTimers({ toFake: ['Date'] })
    vi.setSystemTime(new Date('2026-02-15T15:00:00Z'))
    const chamadas = abrir([{ ...LUZ }])
    await screen.findByText('Conta de luz')

    await userEvent.click(screen.getByRole('button', { name: 'Editar Conta de luz' }))
    const dialogo = screen.getByRole('dialog', { name: 'Editar despesa' })
    expect(within(dialogo).getByLabelText(/O que foi pago/)).toHaveValue('Conta de luz')
    expect(within(dialogo).getByLabelText(/Responsável/)).toHaveValue('Administração')
    expect(within(dialogo).getByLabelText(/Valor/)).toHaveValue('90,00')
    expect(within(dialogo).getByRole('combobox', { name: /Categoria/ })).toHaveTextContent('Contas')

    await userEvent.clear(within(dialogo).getByLabelText(/Responsável/))
    await userEvent.type(within(dialogo).getByLabelText(/Responsável/), ' Zelador Antônio ')
    await userEvent.clear(within(dialogo).getByLabelText(/Valor/))
    await userEvent.type(within(dialogo).getByLabelText(/Valor/), '1.234,56')
    await userEvent.click(within(dialogo).getByRole('button', { name: 'Salvar alterações' }))

    expect(await screen.findByText('Despesa "Conta de luz" atualizada.')).toBeInTheDocument()
    expect(screen.queryByRole('dialog', { name: 'Editar despesa' })).not.toBeInTheDocument()
    const put = chamadas.find((c) => c.metodo === 'PUT')
    expect(put?.caminho).toBe('/despesas/desp-1')
    expect(put?.corpo).toEqual({
      descricao: 'Conta de luz',
      responsavel: 'Zelador Antônio',
      categoriaId: 'cat-1',
      valor: 1234.56,
      dataVencimento: '2026-02-10',
    })
    // A página e os totais vêm de novo do backend, já com o valor novo.
    const linha = (await screen.findByText('Zelador Antônio')).closest('tr') as HTMLElement
    expect(linha).toHaveTextContent(/R\$\s*1\.234,56/)
  })

  it('a despesa antiga, sem responsável, só é salva depois de ganhar um', async () => {
    vi.useFakeTimers({ toFake: ['Date'] })
    vi.setSystemTime(new Date('2026-02-15T15:00:00Z'))
    const chamadas = abrir([{ ...LUZ, responsavel: null }])
    await screen.findByText('Conta de luz')

    await userEvent.click(screen.getByRole('button', { name: 'Editar Conta de luz' }))
    const dialogo = screen.getByRole('dialog', { name: 'Editar despesa' })
    expect(within(dialogo).getByLabelText(/Responsável/)).toHaveValue('')
    await userEvent.click(within(dialogo).getByRole('button', { name: 'Salvar alterações' }))

    expect(await within(dialogo).findByText('Informe o responsável pela despesa.')).toBeInTheDocument()
    expect(chamadas.some((c) => c.metodo === 'PUT')).toBe(false)

    await userEvent.type(within(dialogo).getByLabelText(/Responsável/), 'Direção')
    await userEvent.click(within(dialogo).getByRole('button', { name: 'Salvar alterações' }))
    expect(await screen.findByText('Despesa "Conta de luz" atualizada.')).toBeInTheDocument()
  })

  it('mostra no modal o motivo quando o backend recusa a edição', async () => {
    vi.useFakeTimers({ toFake: ['Date'] })
    vi.setSystemTime(new Date('2026-02-15T15:00:00Z'))
    // Cancelada por outra pessoa enquanto a lista estava aberta.
    const luz = { ...LUZ }
    abrir([luz])
    await screen.findByText('Conta de luz')
    await userEvent.click(screen.getByRole('button', { name: 'Editar Conta de luz' }))
    luz.situacao = 'CANCELADA'

    const dialogo = screen.getByRole('dialog', { name: 'Editar despesa' })
    await userEvent.click(within(dialogo).getByRole('button', { name: 'Salvar alterações' }))

    expect(await within(dialogo).findByText('A despesa foi cancelada e não pode mais ser editada.')).toBeInTheDocument()
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

