import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { PaginaDoBackend } from '../../../../hooks/usePaginacao'
import { problema, simularApi, type ChamadaApi, type RespostaApi } from '../../../../test/api'
import type { Atividade, AutorAtividade } from '../../../../types/auditoria/auditoria'
import { RegistroAtividades } from './RegistroAtividades'

const MARTA: AutorAtividade = { usuarioId: 'u-marta', nome: 'Marta Gestora', perfil: 'MASTER' }
const FERNANDA: AutorAtividade = { usuarioId: 'u-fer', nome: 'Fernanda Rocha', perfil: 'FINANCEIRO' }

function atividade(dados: Partial<Atividade>): Atividade {
  return {
    id: crypto.randomUUID(),
    dataHora: '2026-11-09T17:32:00Z',
    origem: 'USUARIO',
    operacao: 'CRIAR',
    entidade: 'ALUNO',
    registroId: 'r-1',
    usuarioId: MARTA.usuarioId,
    usuarioNome: MARTA.nome,
    usuarioPerfil: 'MASTER',
    ...dados,
  }
}

function pagina(conteudo: Atividade[], total = conteudo.length): PaginaDoBackend<Atividade> {
  return { conteudo, pagina: 0, tamanho: 10, totalElementos: total, totalPaginas: Math.ceil(total / 10) }
}

/** Query string da última busca de atividades, como objeto. */
function ultimaBusca(chamadas: ChamadaApi[]): Record<string, string> {
  const ultima = chamadas.filter((c) => c.caminho.startsWith('/auditoria/pagina')).at(-1)
  return Object.fromEntries(new URLSearchParams(ultima?.caminho.split('?')[1]))
}

function abrir(atividades: (chamada: ChamadaApi) => RespostaApi) {
  const chamadas = simularApi((chamada) => {
    if (chamada.caminho === '/auditoria/autores') return { corpo: [FERNANDA, MARTA] }
    if (chamada.caminho.startsWith('/auditoria/pagina')) return atividades(chamada)
    return undefined
  })
  render(
    <MemoryRouter>
      <RegistroAtividades />
    </MemoryRouter>,
  )
  return chamadas
}

async function escolher(filtro: string, opcao: string) {
  await userEvent.click(screen.getByRole('combobox', { name: filtro }))
  await userEvent.click(await screen.findByRole('option', { name: opcao }))
}

describe('Registro de atividades (IU28)', () => {
  beforeEach(() => {
    Element.prototype.scrollIntoView = vi.fn()
  })
  afterEach(() => vi.unstubAllGlobals())

  it('lista quem fez o quê e quando, com o provedor e o sistema pelo nome', async () => {
    abrir(() => ({
      corpo: pagina([
        atividade({ operacao: 'PAGAR', entidade: 'COBRANCA' }),
        atividade({
          origem: 'PROVEDOR',
          operacao: 'CONFIRMAR_PAGAMENTO',
          entidade: 'COBRANCA',
          usuarioId: null,
          usuarioNome: null,
          usuarioPerfil: null,
        }),
        atividade({
          origem: 'SISTEMA',
          operacao: 'SUSPENDER',
          entidade: 'ESCOLA',
          usuarioId: null,
          usuarioNome: null,
          usuarioPerfil: null,
        }),
        atividade({ usuarioNome: null, operacao: 'ANONIMIZAR' }),
      ]),
    }))

    const tabela = await screen.findByRole('table')
    const linhas = within(tabela).getAllByRole('row').slice(1)
    expect(linhas[0]).toHaveTextContent('09/11/2026 às 14:32')
    expect(linhas[0]).toHaveTextContent('Marta Gestora')
    expect(linhas[0]).toHaveTextContent('Gestão')
    expect(linhas[0]).toHaveTextContent('Registrou o pagamento de uma cobrança')
    expect(linhas[1]).toHaveTextContent('Asaas')
    expect(linhas[2]).toHaveTextContent('Eduka (automático)')
    expect(linhas[2]).toHaveTextContent('Suspendeu a escola')
    expect(linhas[3]).toHaveTextContent('Ex-integrante da escola')
    expect(screen.getByText(/^4 atividades\./)).toBeInTheDocument()
  })

  it('filtra por pessoa, entidade e operação e volta para a primeira página', async () => {
    const chamadas = abrir(() => ({ corpo: pagina([atividade({})], 25) }))
    await screen.findByRole('table')
    await userEvent.click(screen.getByRole('button', { name: 'Página 2' }))
    expect(ultimaBusca(chamadas)).toMatchObject({ pagina: '1' })

    await escolher('Filtrar por pessoa', 'Fernanda Rocha · Financeiro')
    expect(ultimaBusca(chamadas)).toEqual({ pagina: '0', tamanho: '10', usuarioId: 'u-fer' })

    await escolher('Filtrar por entidade', 'Matrícula')
    await escolher('Filtrar por operação', 'Cancelar')
    expect(ultimaBusca(chamadas)).toEqual({
      pagina: '0',
      tamanho: '10',
      usuarioId: 'u-fer',
      entidade: 'MATRICULA',
      operacao: 'CANCELAR',
    })

    await userEvent.click(screen.getByRole('button', { name: 'Limpar filtros' }))
    expect(ultimaBusca(chamadas)).toEqual({ pagina: '0', tamanho: '10' })
  })

  it('sem resultado para os filtros, avisa', async () => {
    abrir(({ caminho }) => ({ corpo: pagina(caminho.includes('operacao=') ? [] : [atividade({})]) }))
    await screen.findByRole('table')

    await escolher('Filtrar por operação', 'Excluir')

    expect(await screen.findByText('Nenhuma atividade encontrada para os filtros.')).toBeInTheDocument()
  })

  it('erro do backend mostra a mensagem e tenta de novo', async () => {
    let falhar = true
    abrir(() => (falhar ? problema(500, 'ERRO', 'Falhou ao ler o registro.') : { corpo: pagina([atividade({})]) }))

    expect(await screen.findByText('Falhou ao ler o registro.')).toBeInTheDocument()
    falhar = false
    await userEvent.click(screen.getByRole('button', { name: 'Tentar novamente' }))

    expect(await screen.findByRole('table')).toBeInTheDocument()
  })
})
