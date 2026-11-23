import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router'
import { describe, expect, it } from 'vitest'
import { simularApi } from '../../../../test/api'
import { escolherDia } from '../../../../test/calendario'
import type { Matricula } from '../../../../types/academico/matricula'
import { NovaCobranca } from './NovaCobranca'

const ANA = { id: 'aluno-1', nome: 'Ana Souza', matricula: '2026001', genero: null }

function matricula(id: string, cursoNome: string, status: Matricula['status'] = 'ATIVA'): Matricula {
  return {
    id,
    alunoId: ANA.id,
    alunoNome: ANA.nome,
    alunoNumeroMatricula: ANA.matricula,
    turmaId: 1,
    turmaNome: 'Turma A',
    turno: 'MATUTINO',
    periodoLetivoId: 1,
    periodoLetivoNome: '2026/2',
    cursoId: 1,
    cursoNome,
    status,
    dataMatricula: '2026-02-01',
    observacao: null,
    criadoEm: '2026-02-01T12:00:00Z',
    atualizadoEm: null,
  }
}

function backend(matriculas: Matricula[]) {
  return simularApi(({ metodo, caminho }) => {
    if (caminho === '/alunos') return { corpo: [ANA] }
    if (caminho === '/matriculas?alunoId=aluno-1') return { corpo: matriculas }
    if (metodo === 'POST' && caminho === '/cobrancas') return { status: 201, corpo: { id: 'cob-1' } }
    return undefined
  })
}

async function preencherEEnviar(usuario: ReturnType<typeof userEvent.setup>) {
  await usuario.type(screen.getByLabelText('Valor'), '420,00')
  await escolherDia(usuario, screen.getByLabelText(/Vencimento/), new Date())
  await usuario.click(screen.getByRole('button', { name: 'Criar cobrança' }))
}

async function abrirComAna(usuario: ReturnType<typeof userEvent.setup>) {
  render(
    <MemoryRouter>
      <NovaCobranca />
    </MemoryRouter>,
  )
  await usuario.click(await screen.findByRole('button', { name: /Ana Souza/ }))
}

describe('NovaCobranca', () => {
  it('grava o tipo escolhido, que agrupa os relatórios financeiros', async () => {
    const chamadas = backend([matricula('mat-1', 'Ensino Médio')])
    const usuario = userEvent.setup()

    await abrirComAna(usuario)
    await usuario.click(screen.getByRole('button', { name: 'Material' }))
    await preencherEEnviar(usuario)

    const criar = chamadas.find((c) => c.metodo === 'POST')
    expect(criar?.corpo).toMatchObject({ alunoId: 'aluno-1', tipo: 'MATERIAL', valor: 420 })
  })

  it('com uma matrícula só não pergunta: o backend liga a cobrança a ela', async () => {
    const chamadas = backend([matricula('mat-1', 'Ensino Médio'), matricula('mat-2', 'Inglês', 'CONCLUIDA')])
    const usuario = userEvent.setup()

    await abrirComAna(usuario)
    await preencherEEnviar(usuario)

    expect(screen.queryByRole('combobox', { name: /Matrícula que recebe/ })).not.toBeInTheDocument()
    const criar = chamadas.find((c) => c.metodo === 'POST')
    expect(criar?.corpo).not.toHaveProperty('matriculaId')
  })

  it('com mais de uma matrícula exige a escolha e envia a escolhida', async () => {
    const chamadas = backend([matricula('mat-1', 'Ensino Médio'), matricula('mat-2', 'Inglês')])
    const usuario = userEvent.setup()

    await abrirComAna(usuario)
    const seletor = await screen.findByRole('combobox', { name: /Matrícula que recebe/ })
    await preencherEEnviar(usuario)

    expect(await screen.findByText(/escolha a que recebe a cobrança/)).toBeInTheDocument()
    expect(chamadas.some((c) => c.metodo === 'POST')).toBe(false)

    await usuario.click(seletor)
    await usuario.click(screen.getByRole('option', { name: 'Inglês · Turma A' }))
    await usuario.click(screen.getByRole('button', { name: 'Criar cobrança' }))

    const criar = chamadas.find((c) => c.metodo === 'POST')
    expect(criar?.corpo).toMatchObject({ alunoId: 'aluno-1', matriculaId: 'mat-2' })
  })

  it('com mais de uma matrícula aceita a cobrança avulsa, sem curso', async () => {
    const chamadas = backend([matricula('mat-1', 'Ensino Médio'), matricula('mat-2', 'Inglês')])
    const usuario = userEvent.setup()

    await abrirComAna(usuario)
    await usuario.click(await screen.findByRole('combobox', { name: /Matrícula que recebe/ }))
    await usuario.click(screen.getByRole('option', { name: 'Cobrança avulsa (sem curso)' }))
    await preencherEEnviar(usuario)

    const criar = chamadas.find((c) => c.metodo === 'POST')
    expect(criar?.corpo).not.toHaveProperty('matriculaId')
  })

  it('aberta pela aba Financeiro do aluno, já vem com ele escolhido', async () => {
    const chamadas = backend([matricula('mat-1', 'Ensino Médio')])
    const usuario = userEvent.setup()
    render(
      <MemoryRouter initialEntries={['/financeiro/cobrancas/nova?aluno=aluno-1']}>
        <NovaCobranca />
      </MemoryRouter>,
    )

    expect(await screen.findByRole('button', { name: /Ana Souza/ })).toHaveAttribute('aria-pressed', 'true')
    expect(screen.getByText(/Aluno selecionado:/)).toHaveTextContent('Aluno selecionado: Ana Souza · Matrícula 2026001')
    await preencherEEnviar(usuario)

    const criar = chamadas.find((c) => c.metodo === 'POST')
    expect(criar?.corpo).toMatchObject({ alunoId: 'aluno-1', valor: 420 })
  })

  it('aluno do endereço que não pode receber cobrança não fica escolhido', async () => {
    const chamadas = backend([])
    render(
      <MemoryRouter initialEntries={['/financeiro/cobrancas/nova?aluno=anonimizado']}>
        <NovaCobranca />
      </MemoryRouter>,
    )

    expect(await screen.findByRole('button', { name: /Ana Souza/ })).toHaveAttribute('aria-pressed', 'false')
    expect(screen.queryByText(/Aluno selecionado:/)).not.toBeInTheDocument()
    expect(chamadas.some((c) => c.caminho.startsWith('/matriculas'))).toBe(false)
  })
})
