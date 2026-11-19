import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { cortarPagina, parametros, problema, simularApi, type ChamadaApi } from '../../../../test/api'
import type { Assinatura, FaturaPlataforma } from '../../../../types/escola/faturaPlataforma'
import { PlanoEFaturas } from './PlanoEFaturas'
import { mesPorExtenso, referenciaDaFatura } from './proximaFatura'

function fatura(id: string, competencia: string, status: FaturaPlataforma['status'], vencimento: string): FaturaPlataforma {
  const paga = status === 'PAGA'
  return {
    id,
    escolaId: 'e1',
    escolaNome: 'Colégio Santo Agostinho',
    competencia,
    descricao: 'Plano Avançado',
    valor: 690,
    dataVencimento: vencimento,
    status,
    situacaoPagamento: paga ? 'PAGA' : status === 'CANCELADA' ? 'CANCELADA' : 'AGUARDANDO_PAGAMENTO',
    formaPagamento: paga ? 'PIX' : null,
    pagamento: paga ? { valorPago: 690, valorEstornado: 0, dataPagamento: vencimento, forma: 'PIX' } : null,
    acoes: paga ? ['VER_COMPROVANTE'] : status === 'CANCELADA' ? [] : ['PAGAR'],
  }
}

const FATURAS = [
  fatura('f-ago', '2026-08', 'PAGA', '2026-08-05'),
  fatura('f-nov', '2026-11', 'PENDENTE', '2026-11-05'),
  fatura('f-out', '2026-10', 'PAGA', '2026-10-05'),
  fatura('f-set', '2026-09', 'CANCELADA', '2026-09-05'),
]

const ASSINATURA: Assinatura = {
  escolaNome: 'Colégio Santo Agostinho',
  plano: 'AVANCADO',
  ativa: true,
  inicioVigencia: '2024-01-15',
  fimVigencia: '2027-01-15',
  limiteUsuarios: 1500,
}

/**
 * O GET /config-pagamento/faturas/pagina como o backend: filtra pelos `status`
 * pedidos e ordena pela competência (da mais recente) ou pelo vencimento (o
 * mais antigo primeiro).
 */
function paginaDeFaturas(faturas: FaturaPlataforma[], caminho: string) {
  const { status, ordenarPor } = parametros(caminho)
  const situacoes = status ? status.split(',') : null
  const filtradas = faturas
    .filter((f) => !situacoes || situacoes.includes(f.status))
    .sort((a, b) =>
      ordenarPor === 'VENCIMENTO'
        ? a.dataVencimento.localeCompare(b.dataVencimento)
        : b.competencia.localeCompare(a.competencia),
    )
  return { corpo: cortarPagina(filtradas, caminho) }
}

function abrir(faturas: FaturaPlataforma[]): ChamadaApi[] {
  const chamadas = simularApi(({ caminho }) => {
    if (caminho.startsWith('/config-pagamento/faturas/pagina?')) return paginaDeFaturas(faturas, caminho)
    if (caminho === '/config-pagamento/assinatura') return { corpo: ASSINATURA }
    return undefined
  })
  render(
    <MemoryRouter>
      <PlanoEFaturas />
    </MemoryRouter>,
  )
  return chamadas
}

/** Parâmetros das consultas de faturas, na ordem em que saíram. */
function consultasDeFaturas(chamadas: ChamadaApi[]) {
  return chamadas.filter((c) => c.caminho.startsWith('/config-pagamento/faturas/pagina?')).map((c) => parametros(c.caminho))
}

describe('PlanoEFaturas', () => {
  beforeEach(() => {
    Element.prototype.scrollIntoView = vi.fn()
  })
  afterEach(() => vi.unstubAllGlobals())

  it('pede ao backend só a próxima em aberto e uma página das pagas ou canceladas', async () => {
    const chamadas = abrir([
      fatura('a', '2026-12', 'PENDENTE', '2026-12-05'),
      fatura('b', '2026-11', 'EM_ATRASO', '2026-11-05'),
      ...FATURAS.filter((f) => f.status !== 'PENDENTE'),
    ])

    const destaque = await screen.findByRole('region', { name: 'Próxima fatura' })
    expect(within(destaque).getByText('05/11/2026')).toBeInTheDocument()
    await screen.findByText('Plano Avançado · outubro/2026')

    const consultas = consultasDeFaturas(chamadas)
    // A vencida pode seguir PENDENTE no banco até a rotina diária: as duas situações vão juntas.
    expect(consultas).toContainEqual({ pagina: '0', tamanho: '1', status: 'PENDENTE,EM_ATRASO', ordenarPor: 'VENCIMENTO' })
    expect(consultas).toContainEqual({ pagina: '0', tamanho: '10', status: 'PAGA,CANCELADA' })
    expect(chamadas.some((c) => c.caminho === '/config-pagamento/faturas')).toBe(false)
  })

  it('a referência traz o mês por extenso quando a descrição não tem', () => {
    expect(mesPorExtenso('2026-11')).toBe('novembro/2026')
    expect(referenciaDaFatura(FATURAS[1])).toBe('Plano Avançado · novembro/2026')
    expect(referenciaDaFatura({ ...FATURAS[1], descricao: 'Plano Avançado · 11/2026' })).toBe('Plano Avançado · 11/2026')
  })

  it('mostra a próxima fatura, a assinatura e as anteriores com o comprovante', async () => {
    abrir(FATURAS)

    const destaque = await screen.findByRole('region', { name: 'Próxima fatura' })
    expect(within(destaque).getByText('R$ 690,00')).toBeInTheDocument()
    expect(within(destaque).getByText(/plano avançado · novembro\/2026 · vence em/i)).toBeInTheDocument()
    expect(within(destaque).getByText('05/11/2026')).toBeInTheDocument()
    expect(within(destaque).getByText('A pagar')).toBeInTheDocument()
    expect(within(destaque).getByRole('button', { name: /pagar fatura/i })).toBeInTheDocument()

    const assinatura = screen.getByRole('region', { name: 'Sua assinatura' })
    expect(await within(assinatura).findByText('Avançado')).toBeInTheDocument()
    expect(within(assinatura).getByText('Ativa')).toBeInTheDocument()
    expect(within(assinatura).getByText('15/01/2024 a 15/01/2027')).toBeInTheDocument()
    expect(within(assinatura).getByText('1.500')).toBeInTheDocument()

    const anteriores = screen.getByRole('region', { name: 'Faturas anteriores' })
    await within(anteriores).findByText('Plano Avançado · outubro/2026')
    const linhas = within(anteriores).getAllByRole('row').slice(1)
    expect(linhas.map((l) => within(l).getAllByRole('cell')[0].textContent)).toEqual([
      'Plano Avançado · outubro/2026',
      'Plano Avançado · setembro/2026',
      'Plano Avançado · agosto/2026',
    ])
    expect(within(anteriores).queryByText(/novembro/i)).not.toBeInTheDocument()
    expect(within(anteriores).getAllByRole('button', { name: /ver comprovante/i })).toHaveLength(2)
    expect(within(linhas[1]).getByText('Cancelada')).toBeInTheDocument()
  })

  it('Pagar fatura abre a mesma escolha de PIX ou boleto do aluno', async () => {
    const usuario = userEvent.setup()
    abrir(FATURAS)

    await usuario.click(await screen.findByRole('button', { name: /pagar fatura/i }))

    const modal = screen.getByRole('dialog')
    expect(within(modal).getByRole('heading', { name: 'Pagar fatura' })).toBeInTheDocument()
    expect(within(modal).getByRole('radio', { name: /pix/i })).toBeChecked()
    expect(within(modal).getByRole('radio', { name: /boleto/i })).toBeInTheDocument()
    expect(within(modal).getByRole('button', { name: 'Pagar com PIX' })).toBeInTheDocument()
  })

  it('fatura em atraso aparece em vermelho e continua podendo ser paga', async () => {
    abrir([fatura('f-nov', '2026-11', 'EM_ATRASO', '2026-11-05')])

    const destaque = await screen.findByRole('region', { name: 'Próxima fatura' })
    expect(within(destaque).getByText('Em atraso')).toBeInTheDocument()
    expect(within(destaque).getByRole('button', { name: /pagar fatura/i })).toBeInTheDocument()
  })

  it('as anteriores trocam de página no backend', async () => {
    const usuario = userEvent.setup()
    const pagas = Array.from({ length: 12 }, (_, i) => {
      const mes = String(i + 1).padStart(2, '0')
      return fatura(`f-${mes}`, `2025-${mes}`, 'PAGA', `2025-${mes}-05`)
    })
    const chamadas = abrir(pagas)

    const anteriores = await screen.findByRole('region', { name: 'Faturas anteriores' })
    await within(anteriores).findByText('Plano Avançado · dezembro/2025')
    expect(within(anteriores).getAllByRole('row')).toHaveLength(11)

    await usuario.click(screen.getByRole('button', { name: /próxima/i }))

    expect(await within(anteriores).findByText('Plano Avançado · fevereiro/2025')).toBeInTheDocument()
    expect(within(anteriores).getAllByRole('row')).toHaveLength(3)
    expect(consultasDeFaturas(chamadas).at(-1)).toEqual({ pagina: '1', tamanho: '10', status: 'PAGA,CANCELADA' })
  })

  it('erro nas anteriores não esconde a próxima fatura e deixa tentar de novo', async () => {
    const usuario = userEvent.setup()
    let falhar = true
    simularApi(({ caminho }) => {
      if (caminho.includes('status=PAGA')) return falhar ? problema(500, 'ERRO', 'Falhou') : paginaDeFaturas(FATURAS, caminho)
      if (caminho.startsWith('/config-pagamento/faturas/pagina?')) return paginaDeFaturas(FATURAS, caminho)
      if (caminho === '/config-pagamento/assinatura') return { corpo: ASSINATURA }
      return undefined
    })
    render(
      <MemoryRouter>
        <PlanoEFaturas />
      </MemoryRouter>,
    )

    const anteriores = await screen.findByRole('region', { name: 'Faturas anteriores' })
    expect(await within(anteriores).findByRole('alert')).toHaveTextContent('Falhou')
    expect(screen.getByRole('button', { name: /pagar fatura/i })).toBeInTheDocument()

    falhar = false
    await usuario.click(within(anteriores).getByRole('button', { name: 'Tentar novamente' }))
    expect(await within(anteriores).findByText('Plano Avançado · outubro/2026')).toBeInTheDocument()
  })

  it('sem fatura em aberto, avisa que está tudo em dia', async () => {
    abrir([FATURAS[2]])

    expect(await screen.findByText(/nenhuma fatura em aberto/i)).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /pagar fatura/i })).not.toBeInTheDocument()
  })
})
