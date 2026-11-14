import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { cortarPagina, parametros, simularApi, type ChamadaApi } from '../../../../test/api'
import type { Professor, ResumoProfessores } from '../../../../types/pessoas/professor'
import { Professores } from './Professores'

function professor(n: number, ativo = true): Professor {
  return {
    id: `prof-${n}`,
    escolaId: 'escola-1',
    nome: `Professor ${String(n).padStart(2, '0')}`,
    cpf: null,
    email: `p${n}@escola.com`,
    telefone: null,
    formacao: null,
    dataAdmissao: null,
    vinculo: n === 1 ? null : 'CLT_40H',
    ativo,
  }
}

/** Faz o papel do GET /professores/pagina e do PATCH de inativar. */
function abrir(professores: Professor[]): ChamadaApi[] {
  const chamadas = simularApi(({ metodo, caminho }) => {
    if (caminho.startsWith('/professores/pagina?')) {
      const nome = parametros(caminho).nome?.toLowerCase()
      const filtrados = professores.filter((p) => !nome || p.nome.toLowerCase().includes(nome))
      const resumo: ResumoProfessores = {
        total: professores.length,
        ativos: professores.filter((p) => p.ativo).length,
        semVinculo: professores.filter((p) => p.vinculo === null).length,
      }
      return { corpo: { ...cortarPagina(filtrados, caminho), resumo } }
    }
    const inativar = caminho.match(/^\/professores\/([^/]+)\/inativar$/)
    if (inativar && metodo === 'PATCH') {
      const alvo = professores.find((p) => p.id === inativar[1]) as Professor
      alvo.ativo = false
      return { corpo: alvo }
    }
    return undefined
  })
  render(
    <MemoryRouter>
      <Professores />
    </MemoryRouter>,
  )
  return chamadas
}

function consultas(chamadas: ChamadaApi[]) {
  return chamadas.filter((c) => c.caminho.startsWith('/professores/pagina?')).map((c) => parametros(c.caminho))
}

describe('Professores', () => {
  beforeEach(() => {
    Element.prototype.scrollIntoView = vi.fn()
  })

  it('pede ao backend só a página que mostra, com os cards da escola inteira', async () => {
    const chamadas = abrir([...Array.from({ length: 11 }, (_, i) => professor(i + 1)), professor(12, false)])

    expect(await screen.findByText('Professor 01')).toBeInTheDocument()
    expect(screen.queryByText('Professor 11')).not.toBeInTheDocument()
    expect(consultas(chamadas)).toEqual([{ pagina: '0', tamanho: '10' }])
    const resumo = screen.getByRole('region', { name: 'Resumo dos professores' })
    expect(resumo).toHaveTextContent(/Professores ativos\s*11\s*De 12 cadastrados/)
    expect(resumo).toHaveTextContent(/Inativos\s*1/)
    expect(resumo).toHaveTextContent(/Sem vínculo informado\s*1/)

    await userEvent.click(screen.getByRole('button', { name: /próxima/i }))
    expect(await screen.findByText('Professor 12')).toBeInTheDocument()
    expect(consultas(chamadas).at(-1)).toEqual({ pagina: '1', tamanho: '10' })
  })

  it('a busca pelo nome vai ao backend, sem prometer a busca por disciplina', async () => {
    const chamadas = abrir(Array.from({ length: 3 }, (_, i) => professor(i + 1)))
    await screen.findByText('Professor 01')

    const busca = screen.getByRole('searchbox', { name: 'Buscar professor pelo nome' })
    expect(busca).toHaveAttribute('placeholder', 'Procurar pelo nome')
    await userEvent.type(busca, 'professor 02')

    await waitFor(() => expect(screen.queryByText('Professor 01')).not.toBeInTheDocument())
    expect(screen.getByText('Professor 02')).toBeInTheDocument()
    expect(consultas(chamadas).at(-1)).toEqual({ pagina: '0', tamanho: '10', nome: 'professor 02' })
  })

  it('inativar lê a página e os cards de novo', async () => {
    abrir([professor(1), professor(2)])
    const linha = (await screen.findByText('Professor 02')).closest('tr') as HTMLElement
    const resumo = screen.getByRole('region', { name: 'Resumo dos professores' })
    expect(resumo).toHaveTextContent(/Professores ativos\s*2/)

    await userEvent.click(within(linha).getByRole('button', { name: /inativar/i }))

    await waitFor(() => expect(resumo).toHaveTextContent(/Professores ativos\s*1\s*De 2/))
    expect(screen.getByRole('status')).toHaveTextContent('"Professor 02" foi inativado.')
  })
})
