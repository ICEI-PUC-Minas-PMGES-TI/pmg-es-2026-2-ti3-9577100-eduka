import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { cortarPagina, parametros, simularApi, type ChamadaApi } from '../../../../test/api'
import { logarComo } from '../../../../test/sessao'
import type { TurmaAcademica } from '../../../../types/academico/academico'
import type { AlunoDaLista, ResumoAlunos, SituacaoNaEscola } from '../../../../types/pessoas/aluno'
import { Alunos } from './Alunos'

const ALUNOS: AlunoDaLista[] = [
  { id: 'ana', nome: 'Ana Souza', matricula: '2026001', genero: null, situacao: 'MATRICULADO', turmaId: 1, turmaNome: '1A' },
  { id: 'bia', nome: 'Bia Lima', matricula: '2026002', genero: null, situacao: 'TRANCADO', turmaId: 2, turmaNome: '2B' },
  { id: 'caio', nome: 'Caio Dias', matricula: '2026003', genero: null, situacao: 'SEM_TURMA', turmaId: null, turmaNome: null },
]

const TURMA: TurmaAcademica = {
  id: 1,
  periodoLetivoId: 5,
  periodoLetivoNome: '2026/2',
  cursoId: 7,
  cursoNome: 'Ensino Médio',
  nome: '1A',
  codigo: null,
  vagas: 30,
  turno: 'MATUTINO',
  ativa: true,
  disciplinas: [],
  matriculasAtivas: 1,
}

function aluno(n: number, situacao: SituacaoNaEscola = 'SEM_TURMA'): AlunoDaLista {
  return {
    id: `aluno-${n}`,
    nome: `Aluno ${String(n).padStart(2, '0')}`,
    matricula: `2026${String(n).padStart(3, '0')}`,
    genero: null,
    situacao,
    turmaId: null,
    turmaNome: null,
  }
}

/** Faz o papel do GET /alunos/pagina: busca por nome ou matrícula, situação e o resumo da escola inteira. */
function abrir(alunos: AlunoDaLista[], area = '/secretaria/alunos'): ChamadaApi[] {
  const chamadas = simularApi(({ caminho }) => {
    if (caminho.startsWith('/alunos/pagina?')) {
      const { busca, situacao } = parametros(caminho)
      const termo = busca?.toLowerCase()
      const filtrados = alunos.filter(
        (a) =>
          (!termo || a.nome.toLowerCase().includes(termo) || a.matricula.includes(termo)) &&
          (!situacao || a.situacao === situacao),
      )
      const contar = (s: SituacaoNaEscola) => alunos.filter((a) => a.situacao === s).length
      const resumo: ResumoAlunos = {
        total: alunos.length,
        porSituacao: {
          MATRICULADO: contar('MATRICULADO'),
          TRANCADO: contar('TRANCADO'),
          SEM_TURMA: contar('SEM_TURMA'),
          ANONIMIZADO: contar('ANONIMIZADO'),
        },
      }
      return { corpo: { ...cortarPagina(filtrados, caminho), resumo } }
    }
    if (caminho === '/academico/secretaria/turmas') return { corpo: [TURMA] }
    return undefined
  })
  render(
    <MemoryRouter initialEntries={[area]}>
      <Alunos />
    </MemoryRouter>,
  )
  return chamadas
}

function consultasDeAlunos(chamadas: ChamadaApi[]) {
  return chamadas.filter((c) => c.caminho.startsWith('/alunos/pagina?')).map((c) => parametros(c.caminho))
}

describe('Alunos', () => {
  // Trocar de página rola até o topo da tabela; o jsdom não tem scrollIntoView.
  beforeEach(() => {
    Element.prototype.scrollIntoView = vi.fn()
    logarComo('SECRETARIA')
  })

  afterEach(() => {
    localStorage.clear()
  })

  it('a secretaria cadastra, edita e reenvia o acesso', async () => {
    abrir(ALUNOS)

    const linhaDaAna = (await screen.findByText('Ana Souza')).closest('tr') as HTMLElement
    expect(within(linhaDaAna).getByRole('button', { name: 'Editar' })).toBeInTheDocument()
    expect(within(linhaDaAna).getByRole('button', { name: 'Reenviar acesso' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /novo aluno/i })).toBeInTheDocument()
  })

  it('o financeiro só consulta, no próprio menu, e não busca as turmas do cadastro', async () => {
    logarComo('FINANCEIRO')
    const chamadas = abrir(ALUNOS, '/financeiro/alunos')

    const linhaDaAna = (await screen.findByText('Ana Souza')).closest('tr') as HTMLElement
    expect(within(linhaDaAna).getByRole('button', { name: 'Ver' })).toBeInTheDocument()
    expect(within(linhaDaAna).queryByRole('button', { name: 'Editar' })).not.toBeInTheDocument()
    expect(within(linhaDaAna).queryByRole('button', { name: 'Reenviar acesso' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /novo aluno/i })).not.toBeInTheDocument()
    expect(screen.getByRole('navigation', { name: /navegação do financeiro/i })).toBeInTheDocument()
    expect(chamadas.some((c) => c.caminho.startsWith('/academico'))).toBe(false)
  })

  it('mostra turma e situação que vêm com cada aluno, sem buscar as matrículas', async () => {
    const chamadas = abrir(ALUNOS)

    const linhaDaAna = (await screen.findByText('Ana Souza')).closest('tr') as HTMLElement
    expect(within(linhaDaAna).getByText('1A')).toBeInTheDocument()
    expect(within(linhaDaAna).getByText('Matriculado')).toBeInTheDocument()
    const linhaDaBia = screen.getByText('Bia Lima').closest('tr') as HTMLElement
    expect(within(linhaDaBia).getByText('Trancado')).toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: /^sem turma/i }))
    expect(await screen.findByText('Caio Dias')).toBeInTheDocument()
    expect(screen.queryByText('Ana Souza')).not.toBeInTheDocument()
    expect(consultasDeAlunos(chamadas).at(-1)).toEqual({ pagina: '0', tamanho: '10', situacao: 'SEM_TURMA' })

    expect(chamadas.some((c) => c.caminho.startsWith('/matriculas'))).toBe(false)
  })

  it('pede ao backend só a página que mostra, e cards e pílulas contam a escola inteira', async () => {
    const chamadas = abrir([...Array.from({ length: 12 }, (_, i) => aluno(i + 1)), aluno(13, 'MATRICULADO')])

    expect(await screen.findByText('Aluno 01')).toBeInTheDocument()
    expect(screen.queryByText('Aluno 11')).not.toBeInTheDocument()
    expect(consultasDeAlunos(chamadas)).toEqual([{ pagina: '0', tamanho: '10' }])
    expect(screen.getByRole('region', { name: 'Resumo dos alunos' })).toHaveTextContent(/Alunos cadastrados\s*13/)
    expect(screen.getByRole('button', { name: /^matriculados/i })).toHaveTextContent('1')

    await userEvent.click(screen.getByRole('button', { name: /próxima/i }))
    expect(await screen.findByText('Aluno 11')).toBeInTheDocument()
    expect(screen.getByText('11–13 de 13')).toBeInTheDocument()
    expect(consultasDeAlunos(chamadas).at(-1)).toEqual({ pagina: '1', tamanho: '10' })
  })

  it('a busca pela matrícula vai ao backend depois da digitação e volta à primeira página', async () => {
    const chamadas = abrir(Array.from({ length: 15 }, (_, i) => aluno(i + 1)))
    await userEvent.click(await screen.findByRole('button', { name: /próxima/i }))
    expect(await screen.findByText('Página 2 de 2')).toBeInTheDocument()

    await userEvent.type(screen.getByRole('searchbox', { name: /nome ou matrícula/i }), '2026003')

    expect(await screen.findByText('Aluno 03')).toBeInTheDocument()
    expect(screen.queryByText('Aluno 01')).not.toBeInTheDocument()
    expect(consultasDeAlunos(chamadas).at(-1)).toEqual({ pagina: '0', tamanho: '10', busca: '2026003' })
    // Uma consulta só pelo termo inteiro, não uma por tecla.
    expect(consultasDeAlunos(chamadas).filter((c) => c.busca)).toHaveLength(1)
    // Os cards não mudam com a busca.
    expect(screen.getByRole('region', { name: 'Resumo dos alunos' })).toHaveTextContent(/Alunos cadastrados\s*15/)
  })

  it('avisa quando a busca não encontra ninguém', async () => {
    abrir(ALUNOS)
    await screen.findByText('Ana Souza')

    await userEvent.type(screen.getByRole('searchbox', { name: /nome ou matrícula/i }), 'Zuleica')

    expect(await screen.findByText('Nenhum aluno encontrado para a busca.')).toBeInTheDocument()
  })

  it('mostra o erro do backend e tenta de novo', async () => {
    let falhar = true
    simularApi(({ caminho }) => {
      if (caminho.startsWith('/alunos/pagina?')) {
        if (falhar) return { status: 500, corpo: { status: 500, detail: 'Banco fora do ar' } }
        return { corpo: { ...cortarPagina(ALUNOS, caminho), resumo: { total: 3, porSituacao: { MATRICULADO: 1, TRANCADO: 1, SEM_TURMA: 1 } } } }
      }
      if (caminho === '/academico/secretaria/turmas') return { corpo: [TURMA] }
      return undefined
    })
    render(
      <MemoryRouter>
        <Alunos />
      </MemoryRouter>,
    )

    expect(await screen.findByRole('alert')).toHaveTextContent('Banco fora do ar')
    falhar = false
    await userEvent.click(screen.getByRole('button', { name: /tentar novamente/i }))
    expect(await screen.findByText('Ana Souza')).toBeInTheDocument()
  })

  it('aluno anonimizado não conta como cadastrado e só pode ser visto', async () => {
    const anonimizado: AlunoDaLista = {
      id: 'dani',
      nome: 'Aluno anonimizado',
      matricula: '2024009',
      genero: null,
      situacao: 'ANONIMIZADO',
      turmaId: null,
      turmaNome: null,
    }
    abrir([...ALUNOS, anonimizado])

    const linha = (await screen.findByText('Aluno anonimizado')).closest('tr') as HTMLElement
    expect(within(linha).getByText('Anonimizado')).toBeInTheDocument()
    expect(within(linha).getByRole('button', { name: 'Ver' })).toBeInTheDocument()
    expect(within(linha).queryByRole('button', { name: 'Editar' })).not.toBeInTheDocument()
    expect(within(linha).queryByRole('button', { name: 'Reenviar acesso' })).not.toBeInTheDocument()

    const resumo = screen.getByRole('region', { name: 'Resumo dos alunos' })
    expect(within(resumo).getByText('3')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /^anonimizados/i })).toBeInTheDocument()
  })
})
