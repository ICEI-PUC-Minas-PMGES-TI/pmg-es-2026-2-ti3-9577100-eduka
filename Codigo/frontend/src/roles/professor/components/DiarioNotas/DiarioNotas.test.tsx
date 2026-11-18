import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { simularApi } from '../../../../test/api'
import type { AvaliacaoDoProfessor, NotaDoAluno } from '../../../../types/academico/avaliacaoProfessor'
import { DiarioNotas } from './DiarioNotas'

function avaliacao(notasPublicadas: boolean): AvaliacaoDoProfessor {
  return {
    id: 'av-1',
    turmaId: 1,
    turmaNome: '2º ano A',
    disciplinaId: 1,
    disciplinaNome: 'Matemática',
    tipo: 'PROVA',
    titulo: 'Prova 1',
    descricao: null,
    data: '2026-08-12',
    valor: 10,
    notasLancadas: 0,
    notasPublicadas,
    criadoEm: '2026-08-01T12:00:00Z',
    atualizadoEm: null,
  }
}

const LUCAS: NotaDoAluno = {
  alunoId: 'aluno-1',
  alunoNome: 'Lucas Andrade',
  alunoNumeroMatricula: '2026-0412',
  situacaoMatricula: 'ATIVA',
  valor: null,
  observacao: null,
}

function abrir(notasPublicadas: boolean, notas: NotaDoAluno[] = [LUCAS]) {
  const chamadas = simularApi(({ metodo, caminho, corpo }) => {
    if (metodo === 'PUT' && caminho === '/portal/professor/avaliacoes/av-1/notas') {
      const lote = (corpo as { notas: { alunoId: string; valor: number | null }[] }).notas
      return { corpo: notas.map((n) => ({ ...n, valor: lote.find((l) => l.alunoId === n.alunoId)?.valor ?? n.valor })) }
    }
    return undefined
  })
  const onSalvo = vi.fn()
  render(<DiarioNotas avaliacao={avaliacao(notasPublicadas)} notas={notas} onPendente={vi.fn()} onSalvo={onSalvo} />)
  return { chamadas, onSalvo }
}

describe('DiarioNotas', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('salvar rascunho grava sem publicar e avisa que o aluno ainda não vê', async () => {
    const { chamadas, onSalvo } = abrir(true)

    await userEvent.type(screen.getByLabelText('Nota de Lucas Andrade'), '8,5')
    await userEvent.click(screen.getByRole('button', { name: /salvar rascunho/i }))

    expect(await screen.findByText(/rascunho salvo/i)).toBeInTheDocument()
    expect(chamadas[0].corpo).toEqual({ notas: [{ alunoId: 'aluno-1', valor: 8.5, observacao: null }], rascunho: true })
    expect(onSalvo).toHaveBeenCalledWith(expect.any(Array), false)
    expect(screen.getByText(/rascunho: os alunos ainda não veem/i)).toBeInTheDocument()
  })

  it('lançar notas publica, mesmo sem alteração quando estava em rascunho', async () => {
    const { chamadas, onSalvo } = abrir(false, [{ ...LUCAS, valor: 7 }])

    expect(screen.getByRole('button', { name: /salvar rascunho/i })).toBeDisabled()
    await userEvent.click(screen.getByRole('button', { name: /lançar notas/i }))

    expect(await screen.findByText(/os alunos já veem no boletim/i)).toBeInTheDocument()
    expect(chamadas[0].corpo).toEqual({ notas: [], rascunho: false })
    expect(onSalvo).toHaveBeenCalledWith(expect.any(Array), true)
    expect(screen.queryByText(/rascunho: os alunos ainda não veem/i)).not.toBeInTheDocument()
  })

  it('já publicado e sem alteração, não há o que lançar', () => {
    abrir(true, [{ ...LUCAS, valor: 7 }])

    expect(screen.getByRole('button', { name: /lançar notas/i })).toBeDisabled()
  })
})
