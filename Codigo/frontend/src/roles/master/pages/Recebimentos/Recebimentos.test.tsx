import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { problema, simularApi, type ChamadaApi, type RespostaApi } from '../../../../test/api'
import type { ContaRecebimento } from '../../../../types/financeiro/contaRecebimento'
import { Recebimentos } from './Recebimentos'

const CONTA = '/config-pagamento/conta'

const NAO_INICIADA: ContaRecebimento = {
  status: 'NAO_INICIADA',
  recebimentosHabilitados: false,
  motivoNaoHabilitado: null,
  razaoSocial: null,
  emailFinanceiro: null,
  telefoneCelular: null,
  tipoEmpresa: null,
  faturamentoMensal: null,
  logradouro: null,
  numero: null,
  complemento: null,
  bairro: null,
  cep: null,
  cnpj: '34567891000184',
  bancoCodigo: null,
  contaFinal: null,
  situacao: null,
  ultimoErro: null,
  solicitadaEm: null,
  aprovadaEm: null,
  sincronizadaEm: null,
  podeAtivar: true,
  podeSincronizar: false,
}

const AGUARDANDO: ContaRecebimento = {
  ...NAO_INICIADA,
  status: 'AGUARDANDO_APROVACAO',
  razaoSocial: 'Colégio Ipê Amarelo Ltda',
  tipoEmpresa: 'LIMITED',
  bancoCodigo: '341',
  contaFinal: '89',
  solicitadaEm: '2026-11-10T13:00:00Z',
  podeAtivar: false,
  podeSincronizar: true,
}

const APROVADA: ContaRecebimento = {
  ...AGUARDANDO,
  status: 'APROVADA',
  recebimentosHabilitados: true,
  aprovadaEm: '2026-11-11T15:00:00Z',
  podeSincronizar: false,
}

function abrir(responder: (chamada: ChamadaApi) => RespostaApi | undefined) {
  const chamadas = simularApi(responder)
  render(
    <MemoryRouter>
      <Recebimentos />
    </MemoryRouter>,
  )
  return chamadas
}

/** Preenche o cadastro inteiro do modal com dados válidos. */
async function preencherCadastro(modal: HTMLElement) {
  const campo = (nome: RegExp) => within(modal).getByRole('textbox', { name: nome })
  await userEvent.type(campo(/Razão social/), 'Colégio Ipê Amarelo Ltda')
  await userEvent.type(campo(/E-mail financeiro/), 'financeiro@ipe.com.br')
  await userEvent.type(campo(/Celular/), '31999998888')
  await userEvent.click(within(modal).getByRole('combobox', { name: /Tipo de empresa/ }))
  await userEvent.click(screen.getByRole('option', { name: 'Sociedade limitada (Ltda)' }))
  await userEvent.type(campo(/Faturamento mensal/), '50.000,50')
  await userEvent.type(campo(/Logradouro/), 'Rua das Flores')
  await userEvent.type(campo(/Número/), '100')
  await userEvent.type(campo(/Bairro/), 'Centro')
  await userEvent.type(campo(/CEP/), '30140071')
  await userEvent.type(campo(/Banco \(código\)/), '341')
  await userEvent.type(campo(/Agência/), '1234')
  await userEvent.type(campo(/^Conta/), '567889')
  await userEvent.type(campo(/Dígito/), '0')
  await userEvent.click(within(modal).getByRole('combobox', { name: /Tipo de conta/ }))
  await userEvent.click(screen.getByRole('option', { name: 'Corrente' }))
}

describe('Recebimentos', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('ativa os recebimentos: envia o cadastro e mostra a conta em análise', async () => {
    const chamadas = abrir(({ metodo, caminho }) => {
      if (metodo === 'GET' && caminho === CONTA) return { corpo: NAO_INICIADA }
      if (metodo === 'POST' && caminho === `${CONTA}/ativar`) return { corpo: AGUARDANDO }
      return undefined
    })

    expect(await screen.findByText('Não ativada')).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Ativar recebimentos' }))
    const modal = await screen.findByRole('dialog', { name: 'Ativar recebimentos online' })
    // O CNPJ vem do cadastro da escola e não se edita aqui.
    expect(within(modal).getByDisplayValue('34.567.891/0001-84')).toBeDisabled()

    await preencherCadastro(modal)
    await userEvent.click(within(modal).getByRole('button', { name: 'Enviar cadastro' }))

    expect(await screen.findByText('Aguardando aprovação')).toBeInTheDocument()
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    expect(screen.getByText('Colégio Ipê Amarelo Ltda')).toBeInTheDocument()
    expect(screen.getByText('Banco 341 · final 89')).toBeInTheDocument()
    expect(within(screen.getByRole('list', { name: 'Etapas da ativação' })).getAllByRole('listitem')).toHaveLength(3)

    const post = chamadas.find((c) => c.metodo === 'POST')
    expect(post?.corpo).toEqual({
      razaoSocial: 'Colégio Ipê Amarelo Ltda',
      emailFinanceiro: 'financeiro@ipe.com.br',
      telefoneCelular: '31999998888',
      tipoEmpresa: 'LIMITED',
      faturamentoMensal: 50000.5,
      logradouro: 'Rua das Flores',
      numero: '100',
      complemento: null,
      bairro: 'Centro',
      cep: '30140071',
      dadosBancarios: { codigoBanco: '341', agencia: '1234', conta: '567889', digitoConta: '0', tipo: 'CORRENTE' },
    })
  })

  it('cadastro incompleto aponta os campos e não envia nada', async () => {
    const chamadas = abrir(({ metodo }) => (metodo === 'GET' ? { corpo: NAO_INICIADA } : undefined))

    await userEvent.click(await screen.findByRole('button', { name: 'Ativar recebimentos' }))
    const modal = await screen.findByRole('dialog', { name: 'Ativar recebimentos online' })
    await userEvent.click(within(modal).getByRole('button', { name: 'Enviar cadastro' }))

    expect(within(modal).getByText('Informe a razão social.')).toBeInTheDocument()
    expect(within(modal).getByText('Escolha o tipo de empresa.')).toBeInTheDocument()
    expect(within(modal).getByText('Código do banco com 3 dígitos.')).toBeInTheDocument()
    expect(chamadas.some((c) => c.metodo === 'POST')).toBe(false)
  })

  it('recusa do provedor aparece no modal, que continua aberto com os dados', async () => {
    abrir(({ metodo }) => {
      if (metodo === 'GET') return { corpo: NAO_INICIADA }
      return problema(422, 'PROVEDOR_RECUSOU', 'O CNPJ informado não confere com a Receita.')
    })

    await userEvent.click(await screen.findByRole('button', { name: 'Ativar recebimentos' }))
    const modal = await screen.findByRole('dialog', { name: 'Ativar recebimentos online' })
    await preencherCadastro(modal)
    await userEvent.click(within(modal).getByRole('button', { name: 'Enviar cadastro' }))

    expect(await within(modal).findByRole('alert')).toHaveTextContent(
      'O provedor de pagamento recusou o cadastro: O CNPJ informado não confere com a Receita.',
    )
    expect(within(modal).getByRole('textbox', { name: /Razão social/ })).toHaveValue('Colégio Ipê Amarelo Ltda')
  })

  it('Sincronizar agora lê a situação nova da conta', async () => {
    const chamadas = abrir(({ metodo, caminho }) => {
      if (metodo === 'GET') return { corpo: AGUARDANDO }
      if (caminho === `${CONTA}/sincronizar`) return { corpo: APROVADA }
      return undefined
    })

    await userEvent.click(await screen.findByRole('button', { name: 'Sincronizar agora' }))

    expect(await screen.findByText('Aprovada')).toBeInTheDocument()
    expect(screen.getByText('Os alunos já podem pagar online.')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Sincronizar agora' })).not.toBeInTheDocument()
    expect(chamadas.filter((c) => c.metodo === 'POST').map((c) => c.caminho)).toEqual([`${CONTA}/sincronizar`])
  })

  it('provedor fora do ar ao sincronizar: avisa e mantém a conta como estava', async () => {
    abrir(({ metodo }) =>
      metodo === 'GET' ? { corpo: AGUARDANDO } : problema(503, 'PROVEDOR_INDISPONIVEL', 'Asaas fora do ar'),
    )

    await userEvent.click(await screen.findByRole('button', { name: 'Sincronizar agora' }))

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'O provedor de pagamento não respondeu. Tente de novo em instantes.',
    )
    expect(screen.getByText('Aguardando aprovação')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Sincronizar agora' })).toBeEnabled()
  })

  it('no Sandbox, Aprovar aprova a conta e já sincroniza', async () => {
    const chamadas = abrir(({ metodo, caminho }) => {
      if (metodo === 'GET') return { corpo: AGUARDANDO }
      if (caminho === `${CONTA}/sandbox/aprovar`) return { status: 204 }
      if (caminho === `${CONTA}/sincronizar`) return { corpo: APROVADA }
      return undefined
    })

    await userEvent.click(await screen.findByRole('button', { name: /aprovar no sandbox/i }))

    expect(await screen.findByText('Aprovada')).toBeInTheDocument()
    expect(chamadas.filter((c) => c.metodo === 'POST').map((c) => c.caminho)).toEqual([
      `${CONTA}/sandbox/aprovar`,
      `${CONTA}/sincronizar`,
    ])
  })

  it('erro ao carregar oferece tentar de novo', async () => {
    let tentativas = 0
    abrir(() => {
      tentativas += 1
      return tentativas === 1 ? problema(500, 'ERRO_INTERNO', 'Falha') : { corpo: NAO_INICIADA }
    })

    expect(await screen.findByRole('alert')).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Tentar novamente' }))

    expect(await screen.findByText('Não ativada')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Ativar recebimentos' })).toBeInTheDocument()
  })
})
