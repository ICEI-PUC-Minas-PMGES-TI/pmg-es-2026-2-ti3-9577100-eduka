import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router'
import { describe, expect, it } from 'vitest'
import { simularApi, type ChamadaApi } from '../../../../test/api'
import type { TurmaAcademica } from '../../../../types/academico'
import type { AlunoDaLista } from '../../../../types/aluno'
import type { DashboardSecretaria } from '../../../../types/dashboard'
import type { Matricula } from '../../../../types/matricula'
import { Matriculas } from './Matriculas'

function turma(id: number, nome: string, vagas: number, matriculasAtivas: number): TurmaAcademica {
  return {
    id,
    periodoLetivoId: 5,
    periodoLetivoNome: '2026/2',
    cursoId: 7,
    cursoNome: 'Ensino Médio',
    nome,
    codigo: null,
    vagas,
    turno: 'MATUTINO',
    ativa: true,
    disciplinas: [],
    matriculasAtivas,
  }
}

function matricula(id: string, alunoId: string, alunoNome: string, turmaId: number, turmaNome: string): Matricula {
  return {
    id,
    alunoId,
    alunoNome,
    alunoNumeroMatricula: '2026001',
    turmaId,
    turmaNome,
    turno: 'MATUTINO',
    periodoLetivoId: 5,
    periodoLetivoNome: '2026/2',
    cursoId: 7,
    cursoNome: 'Ensino Médio',
    status: 'ATIVA',
    dataMatricula: '2026-08-01',
    observacao: null,
    criadoEm: '2026-08-01T12:00:00Z',
    atualizadoEm: null,
  }
}

const ALUNOS: AlunoDaLista[] = [
  { id: 'ana', nome: 'Ana Souza', matricula: '2026001', genero: null, situacao: 'MATRICULADO', turmaId: 1, turmaNome: '1A' },
  { id: 'bia', nome: 'Bia Lima', matricula: '2026002', genero: null, situacao: 'SEM_TURMA', turmaId: null, turmaNome: null },
]

const RESUMO: DashboardSecretaria = {
  matriculasPorSituacao: { ATIVA: 1, TRANCADA: 3, CANCELADA: 0, CONCLUIDA: 0 },
  matriculasUltimos30Dias: 0,
  alunosSemTurma: 1,
  vagas: { ofertadas: 5, ocupadas: 1, livres: 4, turmasLotadas: 0 },
  turmas: [],
}

function abrir(): ChamadaApi[] {
  const chamadas = simularApi(({ metodo, caminho, corpo }) => {
    if (caminho === '/academico/secretaria/turmas') {
      return { corpo: [turma(1, '1A', 2, 1), turma(2, '2B', 3, 0), turma(3, '3C', 3, 0), turma(4, '4D', 1, 1)] }
    }
    // A Ana já passou pela 3C: não pode voltar para lá.
    if (caminho === '/matriculas?alunoId=ana') {
      return { corpo: [matricula('m-ana', 'ana', 'Ana Souza', 1, '1A'), matricula('m-velha', 'ana', 'Ana Souza', 3, '3C')] }
    }
    if (caminho === '/alunos') return { corpo: ALUNOS }
    if (caminho === '/dashboard/secretaria') return { corpo: RESUMO }
    if (caminho === '/matriculas?turmaId=1') return { corpo: [matricula('m-ana', 'ana', 'Ana Souza', 1, '1A')] }
    if (caminho === '/matriculas?turmaId=2') return { corpo: [] }
    if (metodo === 'POST' && caminho === '/matriculas') {
      const { alunoId, turmaId } = corpo as { alunoId: string; turmaId: number }
      return { status: 201, corpo: matricula('m-nova', alunoId, 'Bia Lima', turmaId, '2B') }
    }
    return undefined
  })
  render(
    <MemoryRouter>
      <Matriculas />
    </MemoryRouter>,
  )
  return chamadas
}

describe('Matriculas', () => {
  it('busca só as matrículas da turma escolhida e o resumo contado no backend', async () => {
    const chamadas = abrir()

    const tabela = await screen.findByRole('table')
    expect(within(tabela).getByText('Ana Souza')).toBeInTheDocument()
    // O resumo vem do backend, não de somar as matrículas na tela.
    const resumo = screen.getByLabelText('Resumo das matrículas da escola')
    expect(within(resumo).getByText('Trancadas').closest('div')).toHaveTextContent('3')
    // Quem já está na 1A fica na aba da turma; a de matricular mostra só quem pode entrar.
    await userEvent.click(screen.getByRole('tab', { name: /matricular alunos/i }))
    const paraMatricular = screen.getByRole('list', { name: /podem entrar na turma 1A/ })
    expect(within(paraMatricular).getByText('Bia Lima')).toBeInTheDocument()
    expect(within(paraMatricular).queryByText('Ana Souza')).not.toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: /2B/ }))
    expect(await screen.findByText(/ativo em 1A/)).toBeInTheDocument()
    await userEvent.click(screen.getByRole('tab', { name: /alunos da turma/i }))
    expect(await screen.findByText('Nenhum aluno matriculado nesta turma ainda.')).toBeInTheDocument()

    const listagens = chamadas.filter((c) => c.metodo === 'GET' && c.caminho.startsWith('/matriculas'))
    expect(listagens.map((c) => c.caminho)).toEqual(['/matriculas?turmaId=1', '/matriculas?turmaId=2'])
  })

  it('transferir busca as turmas do aluno só quando escolhido e oferece as que têm vaga', async () => {
    const chamadas = abrir()

    const tabela = await screen.findByRole('table')
    await userEvent.click(within(tabela).getByRole('button', { name: 'Alterar' }))
    expect(chamadas.some((c) => c.caminho.includes('alunoId'))).toBe(false)

    await userEvent.click(screen.getByRole('combobox', { name: /o que fazer/i }))
    await userEvent.click(screen.getByRole('option', { name: 'Transferir para outra turma' }))
    await userEvent.click(await screen.findByRole('combobox', { name: /turma de destino/i }))

    // Fora: a própria 1A, a 3C (onde ela já tem matrícula) e a 4D (cheia).
    const opcoes = await screen.findAllByRole('option')
    expect(opcoes.map((o) => o.textContent)).toEqual(['2B · 2026/2 (3 vagas)'])
    expect(chamadas.filter((c) => c.caminho === '/matriculas?alunoId=ana')).toHaveLength(1)
  })

  it('a lista de matricular é paginada e a seleção vale em todas as páginas', async () => {
    const muitos: AlunoDaLista[] = Array.from({ length: 12 }, (_, i) => ({
      id: `a${i}`,
      nome: `Aluno ${String(i + 1).padStart(2, '0')}`,
      matricula: `2026${100 + i}`,
      genero: null,
      situacao: 'SEM_TURMA',
      turmaId: null,
      turmaNome: null,
    }))
    simularApi(({ caminho }) => {
      if (caminho === '/academico/secretaria/turmas') return { corpo: [turma(2, '2B', 30, 0)] }
      if (caminho === '/alunos') return { corpo: muitos }
      if (caminho === '/dashboard/secretaria') return { corpo: RESUMO }
      if (caminho === '/matriculas?turmaId=2') return { corpo: [] }
      return undefined
    })
    render(
      <MemoryRouter>
        <Matriculas />
      </MemoryRouter>,
    )

    await userEvent.click(await screen.findByRole('tab', { name: /matricular alunos/i }))
    const lista = screen.getByRole('list', { name: /podem entrar/ })
    expect(within(lista).getAllByRole('listitem')).toHaveLength(10)
    await userEvent.click(within(lista).getAllByRole('button', { name: 'Matricular' })[0])

    await userEvent.click(screen.getByRole('button', { name: 'Página 2' }))
    expect(within(screen.getByRole('list', { name: /podem entrar/ })).getAllByRole('listitem')).toHaveLength(2)
    await userEvent.click(screen.getAllByRole('button', { name: 'Matricular' })[0])

    expect(screen.getByText(/2 alunos selecionados/)).toBeInTheDocument()
  })

  it('matricular põe o aluno na lista da turma e relê as contagens', async () => {
    const chamadas = abrir()

    await userEvent.click(await screen.findByRole('button', { name: /2B/ }))
    await screen.findByText('Nenhum aluno matriculado nesta turma ainda.')
    await userEvent.click(screen.getByRole('tab', { name: /matricular alunos/i }))
    const linhaDaBia = screen.getAllByText('Bia Lima')[0].closest('li') as HTMLElement
    await userEvent.click(within(linhaDaBia).getByRole('button', { name: 'Matricular' }))
    await userEvent.click(screen.getByRole('button', { name: /confirmar matrículas/i }))

    // Confirmar volta para a aba da turma, já com a Bia na lista.
    expect(await screen.findByText(/1 aluno matriculado na turma 2B/)).toBeInTheDocument()
    expect(screen.getByRole('tab', { name: /alunos da turma/i })).toHaveAttribute('aria-selected', 'true')
    expect(within(screen.getByRole('table')).getByText('Bia Lima')).toBeInTheDocument()
    expect(chamadas.filter((c) => c.caminho === '/dashboard/secretaria')).toHaveLength(2)
  })
})
