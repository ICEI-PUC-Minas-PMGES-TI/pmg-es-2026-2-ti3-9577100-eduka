import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { problema, simularApi, type ChamadaApi, type RespostaApi } from '../../../../test/api'
import type { Alocacao, Turma } from '../../../../types/alocacao'
import { CadastroProfessorModal } from '../CadastroProfessorModal/CadastroProfessorModal'
import { AlocacoesDoProfessor } from './AlocacoesDoProfessor'

const TURMA: Turma = {
  id: 1,
  nome: '1A',
  codigo: null,
  turno: 'MATUTINO',
  periodoLetivoId: 5,
  periodoLetivoNome: '2026/2',
  cursoNome: 'Ensino Médio',
  ativa: true,
  disciplinas: [
    { id: 10, nome: 'Matemática', codigo: null, cargaHoraria: null },
    { id: 11, nome: 'Física', codigo: null, cargaHoraria: null },
    { id: 12, nome: 'Química', codigo: null, cargaHoraria: null },
  ],
}

const INATIVA: Turma = { ...TURMA, id: 2, nome: '2B', ativa: false, disciplinas: [] }

function alocacao(id: string, professorId: string, professorNome: string, disciplinaId: number, disciplinaNome: string) {
  return {
    id,
    professorId,
    professorNome,
    turmaId: 1,
    turmaNome: '1A',
    turno: 'MATUTINO',
    periodoLetivoId: 5,
    periodoLetivoNome: '2026/2',
    disciplinaId,
    disciplinaNome,
  } satisfies Alocacao
}

const MATEMATICA = alocacao('a1', 'prof', 'Rui', 10, 'Matemática')
const FISICA_DA_ANA = alocacao('a2', 'ana', 'Ana', 11, 'Física')

const CAMINHO = '/professores/prof/alocacoes'

function backend(extra: (chamada: ChamadaApi) => RespostaApi | undefined = () => undefined) {
  return simularApi((chamada) => {
    const resposta = extra(chamada)
    if (resposta) return resposta
    if (chamada.caminho === CAMINHO) return { corpo: [MATEMATICA] }
    if (chamada.caminho === '/academico/secretaria/turmas') return { corpo: [TURMA, INATIVA] }
    if (chamada.caminho === '/turmas/1/alocacoes') return { corpo: [MATEMATICA, FISICA_DA_ANA] }
    return undefined
  })
}

async function escolherTurmaEDisciplina(disciplina: string) {
  await userEvent.click(await screen.findByRole('combobox', { name: 'Turma' }))
  await userEvent.click(await screen.findByRole('option', { name: /^1A/ }))
  await screen.findByText(/Já têm professor: Física \(Ana\)/)
  await userEvent.click(screen.getByRole('combobox', { name: 'Disciplina' }))
  await userEvent.click(await screen.findByRole('option', { name: disciplina }))
}

describe('AlocacoesDoProfessor', () => {
  it('lista as alocações e só oferece turma ativa e disciplina sem professor', async () => {
    backend()
    render(<AlocacoesDoProfessor professorId="prof" professorAtivo />)

    const item = (await screen.findByText('Matemática')).closest('li') as HTMLElement
    expect(within(item).getByText('1A · 2026/2 · Matutino')).toBeInTheDocument()

    await userEvent.click(screen.getByRole('combobox', { name: 'Turma' }))
    expect(screen.queryByRole('option', { name: /^2B/ })).not.toBeInTheDocument()
    await userEvent.click(screen.getByRole('option', { name: /^1A/ }))

    await screen.findByText(/Já têm professor: Física \(Ana\)/)
    await userEvent.click(screen.getByRole('combobox', { name: 'Disciplina' }))
    expect(screen.getAllByRole('option').map((o) => o.textContent)).toEqual(['Química'])
  })

  it('inclui pelo POST e a alocação entra na lista', async () => {
    const chamadas = backend(({ metodo, caminho }) =>
      metodo === 'POST' && caminho === CAMINHO
        ? { status: 201, corpo: alocacao('a3', 'prof', 'Rui', 12, 'Química') }
        : undefined,
    )
    render(<AlocacoesDoProfessor professorId="prof" professorAtivo />)

    await escolherTurmaEDisciplina('Química')
    await userEvent.click(screen.getByRole('button', { name: 'Incluir' }))

    expect(await screen.findByRole('button', { name: 'Remover Química de 1A' })).toBeInTheDocument()
    expect(chamadas.find((c) => c.metodo === 'POST')?.corpo).toEqual({ turmaId: 1, disciplinaId: 12 })
  })

  it('avisa quando outra pessoa alocou a disciplina antes', async () => {
    backend(({ metodo }) => (metodo === 'POST' ? problema(409, 'ALOCACAO_JA_EXISTE') : undefined))
    render(<AlocacoesDoProfessor professorId="prof" professorAtivo />)

    await escolherTurmaEDisciplina('Química')
    await userEvent.click(screen.getByRole('button', { name: 'Incluir' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('Esta disciplina já tem professor nesta turma.')
    expect(screen.getByRole('button', { name: 'Incluir' })).toBeDisabled()
  })

  it('só remove depois de confirmar, avisando da grade de horários', async () => {
    const chamadas = backend(({ metodo }) => (metodo === 'DELETE' ? { status: 204 } : undefined))
    render(<AlocacoesDoProfessor professorId="prof" professorAtivo />)

    await userEvent.click(await screen.findByRole('button', { name: 'Remover Matemática de 1A' }))
    expect(screen.getByRole('alert')).toHaveTextContent('grade de horários')
    expect(chamadas.some((c) => c.metodo === 'DELETE')).toBe(false)

    await userEvent.click(screen.getByRole('button', { name: 'Manter' }))
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: 'Remover Matemática de 1A' }))
    await userEvent.click(screen.getByRole('button', { name: 'Confirmar remoção' }))

    expect(await screen.findByText('Este professor ainda não dá aula em nenhuma turma.')).toBeInTheDocument()
    expect(chamadas.find((c) => c.metodo === 'DELETE')?.caminho).toBe(`${CAMINHO}/a1`)
  })

  it('professor inativo pode remover, mas não incluir', async () => {
    backend()
    render(<AlocacoesDoProfessor professorId="prof" professorAtivo={false} />)

    expect(await screen.findByRole('button', { name: 'Remover Matemática de 1A' })).toBeInTheDocument()
    expect(screen.getByText(/Professor inativo não recebe novas alocações/)).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Incluir' })).not.toBeInTheDocument()
  })

  it('falha ao carregar não some com a seção: dá para tentar de novo', async () => {
    let falhar = true
    backend(({ caminho }) => {
      if (caminho === CAMINHO && falhar) return problema(500, 'ERRO', 'Falhou')
      return undefined
    })
    render(<AlocacoesDoProfessor professorId="prof" professorAtivo />)

    expect(await screen.findByRole('alert')).toHaveTextContent('Falhou')
    falhar = false
    await userEvent.click(screen.getByRole('button', { name: 'Tentar novamente' }))

    expect(await screen.findByText('Matemática')).toBeInTheDocument()
  })
})

describe('CadastroProfessorModal', () => {
  const PROFESSOR = {
    id: 'prof',
    escolaId: 'e',
    nome: 'Rui',
    cpf: '52998224725',
    email: 'rui@escola.com',
    telefone: null,
    formacao: null,
    dataAdmissao: null,
    vinculo: 'CLT_40H',
    ativo: true,
  }

  it('mostra Turmas e disciplinas ao editar', async () => {
    backend(({ caminho }) => (caminho === '/professores/prof' ? { corpo: PROFESSOR } : undefined))
    render(
      <CadastroProfessorModal professorId="prof" onFechar={vi.fn()} onSalvar={vi.fn()} onCadastrado={vi.fn()} />,
    )

    expect(await screen.findByRole('heading', { name: 'Turmas e disciplinas' })).toBeInTheDocument()
    expect(await screen.findByText('Matemática')).toBeInTheDocument()
  })

  it('não mostra a seção no cadastro novo, que ainda não tem professor', () => {
    const chamadas = simularApi(() => undefined)
    render(<CadastroProfessorModal onFechar={vi.fn()} onSalvar={vi.fn()} onCadastrado={vi.fn()} />)

    expect(screen.queryByRole('heading', { name: 'Turmas e disciplinas' })).not.toBeInTheDocument()
    expect(chamadas).toEqual([])
  })
})
