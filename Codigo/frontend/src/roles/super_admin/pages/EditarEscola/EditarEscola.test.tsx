import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { createMemoryRouter, RouterProvider, useLocation } from 'react-router'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { escolherDia } from '../../../../test/calendario'
import { problema, simularApi, type ChamadaApi, type RespostaApi } from '../../../../test/api'
import type { Escola } from '../../../../types/escola/escola'
import { EditarEscola, type EstadoPainelEscolas } from './EditarEscola'

const ESCOLA: Escola = {
  id: 'escola-1',
  nome: 'Colégio Ipê Amarelo',
  cnpjCpf: '11222333000181',
  endereco: {
    logradouro: 'Rua das Flores',
    numero: '100',
    bairro: 'Centro',
    cidade: 'Belo Horizonte',
    estado: 'MG',
    cep: '30140071',
  },
  email: 'direcao@ipe.com.br',
  telefone: '3133334444',
  ativo: true,
  criadoEm: '2026-01-02T10:00:00Z',
  plano: 'AVANCADO',
  inicioVigencia: '2026-01-01',
  fimVigencia: '2026-12-31',
  limiteUsuarios: 1500,
  modulos: ['PORTAL_ALUNO', 'GESTAO_FINANCEIRA'],
}

/** O painel de escolas, que mostra o aviso de sucesso que a edição manda. */
function Painel() {
  const estado = useLocation().state as EstadoPainelEscolas | null
  return <p>Painel de escolas {estado?.sucesso ?? 'sem aviso'}</p>
}

function abrir(escola: RespostaApi | (() => RespostaApi) = { corpo: ESCOLA }, salvar?: (c: ChamadaApi) => RespostaApi) {
  const chamadas = simularApi((chamada) => {
    if (chamada.caminho !== '/escolas/escola-1') return undefined
    if (chamada.metodo === 'GET') return typeof escola === 'function' ? escola() : escola
    return salvar?.(chamada)
  })
  const router = createMemoryRouter(
    [
      { path: '/super-admin/escolas/:id/editar', element: <EditarEscola /> },
      { path: '/super-admin/escolas', element: <Painel /> },
    ],
    { initialEntries: ['/super-admin/escolas/escola-1/editar'] },
  )
  render(<RouterProvider router={router} />)
  return chamadas
}

const campo = (nome: RegExp) => screen.getByRole('textbox', { name: nome })
const salvar = () => userEvent.click(screen.getByRole('button', { name: 'Salvar alterações' }))

describe('EditarEscola', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('abre o formulário preenchido com os dados da escola', async () => {
    abrir()

    expect(await screen.findByDisplayValue('Colégio Ipê Amarelo')).toBeInTheDocument()
    expect(campo(/CNPJ/)).toHaveValue('11.222.333/0001-81')
    expect(campo(/Telefone/)).toHaveValue('(31) 3333-4444')
    expect(campo(/CEP/)).toHaveValue('30140-071')
    expect(campo(/Limite de usuários/)).toHaveValue('1.500')
    expect(screen.getByRole('combobox', { name: /Plano contratado/ })).toHaveTextContent('Avançado')
    expect(screen.getByRole('combobox', { name: /Estado/ })).toHaveTextContent('MG')
    expect(screen.getByRole('switch', { name: 'Status da assinatura' })).toBeChecked()
    expect(screen.getByRole('checkbox', { name: 'Portal do Aluno' })).toBeChecked()
    expect(screen.getByRole('checkbox', { name: 'Portal do Professor' })).not.toBeChecked()
  })

  it('salva as alterações e volta ao painel com o aviso de sucesso', async () => {
    const chamadas = abrir(undefined, ({ corpo }) => ({ corpo: { ...ESCOLA, ...(corpo as object) } }))
    await screen.findByDisplayValue('Colégio Ipê Amarelo')

    await userEvent.clear(campo(/Razão social/))
    await userEvent.type(campo(/Razão social/), '  Colégio Ipê Roxo ')
    await userEvent.click(screen.getByRole('checkbox', { name: 'Gestão Financeira' }))
    await userEvent.click(screen.getByRole('checkbox', { name: 'Portal do Professor' }))
    await userEvent.click(screen.getByRole('switch', { name: 'Status da assinatura' }))
    await escolherDia(userEvent.setup(), screen.getByLabelText(/Término da vigência/), new Date(2026, 11, 15))
    await salvar()

    expect(await screen.findByText(/Painel de escolas/)).toHaveTextContent(
      '"Colégio Ipê Roxo" foi atualizada com sucesso.',
    )
    const put = chamadas.find((c) => c.metodo === 'PUT')
    expect(put?.corpo).toEqual({
      nome: 'Colégio Ipê Roxo',
      cnpjCpf: '11222333000181',
      email: 'direcao@ipe.com.br',
      telefone: '(31) 3333-4444',
      endereco: {
        logradouro: 'Rua das Flores',
        numero: '100',
        bairro: 'Centro',
        cidade: 'Belo Horizonte',
        estado: 'MG',
        cep: '30140071',
      },
      plano: 'AVANCADO',
      ativo: false,
      // O início não vai: é fixado no cadastro.
      fimVigencia: '2026-12-15',
      limiteUsuarios: 1500,
      // Na ordem da tela, não na ordem dos cliques.
      modulos: ['PORTAL_ALUNO', 'PORTAL_PROFESSOR'],
    })
  })

  it('o início da vigência é só leitura', async () => {
    abrir()
    await screen.findByDisplayValue('Colégio Ipê Amarelo')

    expect(screen.getByLabelText(/Início da vigência/)).toHaveAttribute('readonly')
  })

  it('dados inválidos apontam cada campo, focam o primeiro e não enviam', async () => {
    const chamadas = abrir()
    await screen.findByDisplayValue('Colégio Ipê Amarelo')

    await userEvent.clear(campo(/Razão social/))
    await userEvent.clear(campo(/E-mail do responsável/))
    await userEvent.type(campo(/E-mail do responsável/), 'sem-arroba')
    await userEvent.clear(campo(/CNPJ/))
    await userEvent.type(campo(/CNPJ/), '11222333000100')
    await userEvent.clear(campo(/Telefone/))
    await userEvent.type(campo(/Telefone/), '3133')
    await salvar()

    expect(screen.getByText('Informe a razão social.')).toBeInTheDocument()
    expect(screen.getByText('CNPJ inválido.')).toBeInTheDocument()
    expect(screen.getByText('E-mail inválido.')).toBeInTheDocument()
    expect(screen.getByText('Telefone incompleto.')).toBeInTheDocument()
    expect(campo(/Razão social/)).toHaveFocus()
    expect(chamadas.some((c) => c.metodo === 'PUT')).toBe(false)
  })

  it('não deixa apagar o limite de usuários já salvo', async () => {
    const chamadas = abrir()
    await screen.findByDisplayValue('Colégio Ipê Amarelo')

    await userEvent.clear(campo(/Limite de usuários/))
    await salvar()

    expect(screen.getByText('Informe o limite de usuários.')).toBeInTheDocument()
    expect(chamadas.some((c) => c.metodo === 'PUT')).toBe(false)
  })

  it('escola antiga sem plano pede o plano antes de salvar', async () => {
    const chamadas = abrir({ corpo: { ...ESCOLA, plano: null } }, () => ({ corpo: { ...ESCOLA, plano: 'BASICO' } }))
    await screen.findByDisplayValue('Colégio Ipê Amarelo')

    await salvar()
    expect(screen.getByText('Selecione o plano contratado.')).toBeInTheDocument()

    await userEvent.click(screen.getByRole('combobox', { name: /Plano contratado/ }))
    await userEvent.click(screen.getByRole('option', { name: 'Básico' }))
    await salvar()

    expect(await screen.findByText(/Painel de escolas/)).toBeInTheDocument()
    expect(chamadas.find((c) => c.metodo === 'PUT')?.corpo).toMatchObject({ plano: 'BASICO' })
  })

  it('CNPJ de outra escola marca o campo do CNPJ', async () => {
    abrir(undefined, () => problema(409, 'CONFLITO', 'CNPJ já cadastrado'))
    await screen.findByDisplayValue('Colégio Ipê Amarelo')

    await salvar()

    expect(await screen.findByText('Já existe outra escola cadastrada com este CNPJ.')).toBeInTheDocument()
    expect(campo(/CNPJ/)).toHaveFocus()
    expect(screen.getByRole('button', { name: 'Salvar alterações' })).toBeEnabled()
  })

  it('dados recusados pelo servidor mostram o aviso e deixam tentar de novo', async () => {
    abrir(undefined, () => problema(400, 'DADOS_INVALIDOS', 'Campo invalido'))
    await screen.findByDisplayValue('Colégio Ipê Amarelo')

    await salvar()

    expect(await screen.findByRole('alert')).toHaveTextContent('Os dados foram recusados pelo servidor.')
    expect(screen.getByRole('button', { name: 'Salvar alterações' })).toBeEnabled()
  })

  it('Cancelar volta ao painel sem salvar nem aviso', async () => {
    const chamadas = abrir()
    await screen.findByDisplayValue('Colégio Ipê Amarelo')

    await userEvent.click(screen.getByRole('button', { name: 'Cancelar' }))

    expect(await screen.findByText(/Painel de escolas/)).toHaveTextContent('Painel de escolas sem aviso')
    expect(chamadas.some((c) => c.metodo === 'PUT')).toBe(false)
  })

  it('escola que não existe avisa, e Tentar novamente busca de novo', async () => {
    let tentativas = 0
    abrir(() => {
      tentativas += 1
      return tentativas === 1 ? problema(404, 'NAO_ENCONTRADO', 'Escola nao encontrada') : { corpo: ESCOLA }
    })

    const alerta = await screen.findByRole('alert')
    expect(alerta).toHaveTextContent('Escola não encontrada.')
    await userEvent.click(within(alerta).getByRole('button', { name: 'Tentar novamente' }))

    expect(await screen.findByDisplayValue('Colégio Ipê Amarelo')).toBeInTheDocument()
  })
})
