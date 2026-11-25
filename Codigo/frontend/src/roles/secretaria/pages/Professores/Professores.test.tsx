import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { cortarPagina, parametros, problema, simularApi, type ChamadaApi, type RespostaApi } from '../../../../test/api'
import type { Professor, ResumoProfessores } from '../../../../types/pessoas/professor'
import { Professores } from './Professores'

function professor(n: number, ativo = true, aguardandoPrimeiroAcesso = false): Professor {
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
    aguardandoPrimeiroAcesso,
  }
}

/** Faz o papel do GET /professores/pagina, do PATCH de inativar e do POST de reenviar o acesso. */
function abrir(professores: Professor[], reenvio: RespostaApi = { status: 204 }): ChamadaApi[] {
  const chamadas = simularApi(({ metodo, caminho }) => {
    if (metodo === 'POST' && /^\/professores\/[^/]+\/primeiro-acesso$/.test(caminho)) return reenvio
    if (caminho === '/professores/prof-2/alocacoes') {
      return {
        corpo: [
          {
            id: 'a1',
            professorId: 'prof-2',
            professorNome: 'Professor 02',
            turmaId: 1,
            turmaNome: '1º ano A',
            turno: 'MATUTINO',
            periodoLetivoId: 1,
            periodoLetivoNome: '2026/2',
            disciplinaId: 10,
            disciplinaNome: 'Matemática',
          },
        ],
      }
    }
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

/** Abre o "⋯" da linha e escolhe a ação. */
async function escolherNoMenu(linha: HTMLElement, nome: string, acao: RegExp) {
  await userEvent.click(within(linha).getByRole('button', { name: `Mais ações para ${nome}` }))
  await userEvent.click(await screen.findByRole('menuitem', { name: acao }))
}

/** Os rótulos das ações no "⋯" da linha; fecha o menu depois de ler. */
async function itensDoMenu(linha: HTMLElement, nome: string): Promise<(string | undefined)[]> {
  await userEvent.click(within(linha).getByRole('button', { name: `Mais ações para ${nome}` }))
  const rotulos = screen.getAllByRole('menuitem').map((item) => item.querySelector('strong')?.textContent ?? undefined)
  await userEvent.keyboard('{Escape}')
  return rotulos
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

  it('os botões Disciplinas e Turmas da linha abrem o modal com as alocações do professor', async () => {
    abrir([professor(1), professor(2)])

    await userEvent.click(await screen.findByRole('button', { name: 'Disciplinas de Professor 02' }))
    const disciplinas = screen.getByRole('dialog', { name: 'Disciplinas de Professor 02' })
    expect(await within(disciplinas).findByText('Matemática')).toBeInTheDocument()
    expect(within(disciplinas).getByText('1º ano A · 2026/2')).toBeInTheDocument()
    // O X e o botão do rodapé se chamam "Fechar"; qualquer um fecha.
    await userEvent.click(within(disciplinas).getAllByRole('button', { name: 'Fechar' })[0])
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: 'Turmas de Professor 02' }))
    const turmas = screen.getByRole('dialog', { name: 'Turmas de Professor 02' })
    expect(await within(turmas).findByText('1º ano A')).toBeInTheDocument()
    expect(within(turmas).getByRole('region', { name: 'Período 2026/2' })).toHaveTextContent(/matutino/i)
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

    await escolherNoMenu(linha, 'Professor 02', /^inativar/i)

    await waitFor(() => expect(resumo).toHaveTextContent(/Professores ativos\s*1\s*De 2/))
    expect(screen.getByRole('status')).toHaveTextContent('"Professor 02" foi inativado.')
  })

  it('Reenviar acesso aparece só para o professor ativo que ainda não entrou', async () => {
    abrir([professor(1, true, true), professor(2, true, false), professor(3, false, true)])

    const pendente = (await screen.findByText('Professor 01')).closest('tr') as HTMLElement
    expect(within(pendente).getByText('Aguardando acesso')).toBeInTheDocument()
    expect(await itensDoMenu(pendente, 'Professor 01')).toEqual(['Reenviar acesso', 'Inativar', 'Excluir'])

    const jaEntrou = screen.getByText('Professor 02').closest('tr') as HTMLElement
    expect(await itensDoMenu(jaEntrou, 'Professor 02')).toEqual(['Inativar', 'Excluir'])
    // Inativo não recebe acesso: o backend recusaria.
    const inativo = screen.getByText('Professor 03').closest('tr') as HTMLElement
    expect(await itensDoMenu(inativo, 'Professor 03')).toEqual(['Reativar', 'Excluir'])
  })

  it('reenvia o acesso e confirma para qual e-mail foi', async () => {
    const chamadas = abrir([professor(1, true, true)])

    const linha = (await screen.findByText('Professor 01')).closest('tr') as HTMLElement
    await escolherNoMenu(linha, 'Professor 01', /^reenviar acesso/i)

    expect(await screen.findByRole('status')).toHaveTextContent(
      'O acesso de "Professor 01" foi reenviado para p1@escola.com, com uma nova senha provisória.',
    )
    expect(chamadas.find((c) => c.metodo === 'POST')?.caminho).toBe('/professores/prof-1/primeiro-acesso')
  })

  it('quem já definiu a senha não recebe reenvio: explica e lê a lista de novo', async () => {
    const chamadas = abrir([professor(1, true, true)], problema(409, 'ACESSO_JA_ATIVADO'))

    const linha = (await screen.findByText('Professor 01')).closest('tr') as HTMLElement
    await escolherNoMenu(linha, 'Professor 01', /^reenviar acesso/i)

    expect(await screen.findByRole('alert')).toHaveTextContent('"Professor 01" já definiu a senha')
    await waitFor(() => expect(consultas(chamadas)).toHaveLength(2))
  })
})
