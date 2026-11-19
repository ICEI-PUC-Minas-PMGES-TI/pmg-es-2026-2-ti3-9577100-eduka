import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { cortarPagina, parametros, problema, simularApi, type ChamadaApi } from '../../../../../test/api'
import type { TurmaAcademica } from '../../../../../types/academico/academico'
import { Turmas } from './Turmas'

function turma(id: number, nome: string): TurmaAcademica {
  return {
    id,
    periodoLetivoId: 1,
    periodoLetivoNome: '2026/2',
    cursoId: 1,
    cursoNome: 'Ensino Fundamental',
    nome,
    codigo: null,
    vagas: 30,
    turno: 'MATUTINO',
    ativa: true,
    disciplinas: [],
    matriculasAtivas: 0,
  }
}

const PAGINA = '/academico/secretaria/turmas/pagina?'

/** O backend com `turmas` já na ordem de nome; `turmas` pode mudar entre as consultas. */
function simular(turmas: { atuais: TurmaAcademica[] }) {
  return simularApi(({ caminho }) =>
    caminho.startsWith(PAGINA) ? { corpo: cortarPagina(turmas.atuais, caminho) } : undefined,
  )
}

function consultas(chamadas: ChamadaApi[]) {
  return chamadas.filter((c) => c.caminho.startsWith(PAGINA)).map((c) => parametros(c.caminho))
}

function renderizar(onExcluir = vi.fn(async () => {})) {
  render(
    <Turmas
      periodos={[]}
      cursos={[]}
      disciplinas={[]}
      onCadastrar={vi.fn()}
      onAtualizar={vi.fn()}
      onExcluir={onExcluir}
    />,
  )
  return { onExcluir }
}

describe('Turmas', () => {
  beforeEach(() => {
    Element.prototype.scrollIntoView = vi.fn()
  })
  afterEach(() => vi.unstubAllGlobals())

  it('mostra só a página que o backend devolve e pede a próxima ao trocar', async () => {
    const usuario = userEvent.setup()
    const todas = Array.from({ length: 12 }, (_, i) => turma(i + 1, `Turma ${String(i + 1).padStart(2, '0')}`))
    const chamadas = simular({ atuais: todas })
    renderizar()

    expect(screen.getByRole('status')).toHaveTextContent('Carregando as turmas…')
    expect(await screen.findByText('Turma 01')).toBeInTheDocument()
    expect(screen.getAllByRole('row')).toHaveLength(11)
    expect(screen.queryByText('Turma 11')).not.toBeInTheDocument()
    expect(consultas(chamadas)).toEqual([{ pagina: '0', tamanho: '10' }])

    await usuario.click(screen.getByRole('button', { name: /próxima/i }))

    expect(await screen.findByText('Turma 11')).toBeInTheDocument()
    expect(screen.queryByText('Turma 01')).not.toBeInTheDocument()
    expect(consultas(chamadas).at(-1)).toEqual({ pagina: '1', tamanho: '10' })
  })

  it('depois de excluir, lê a página de novo do backend', async () => {
    const usuario = userEvent.setup()
    const turmas = { atuais: [turma(1, '6º ano A'), turma(2, '7º ano B')] }
    const chamadas = simular(turmas)
    const { onExcluir } = renderizar(
      vi.fn(async () => {
        turmas.atuais = turmas.atuais.filter((t) => t.id !== 1)
      }),
    )

    const linha = (await screen.findByText('6º ano A')).closest('tr')!
    await usuario.click(within(linha).getByRole('button', { name: 'Excluir 6º ano A' }))
    await usuario.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Excluir' }))

    expect(onExcluir).toHaveBeenCalledWith(1)
    expect(await screen.findByText('Turma "6º ano A" excluída.')).toBeInTheDocument()
    await vi.waitFor(() => expect(screen.queryByText('6º ano A')).not.toBeInTheDocument())
    expect(screen.getByText('7º ano B')).toBeInTheDocument()
    expect(consultas(chamadas)).toHaveLength(2)
  })

  it('sem turma, avisa que nenhuma foi cadastrada', async () => {
    simular({ atuais: [] })
    renderizar()

    expect(await screen.findByText('Nenhuma turma cadastrada ainda.')).toBeInTheDocument()
  })

  it('se a consulta falha, mostra o motivo e deixa tentar de novo', async () => {
    const usuario = userEvent.setup()
    let falhar = true
    simularApi(({ caminho }) => {
      if (!caminho.startsWith(PAGINA)) return undefined
      return falhar ? problema(500, 'ERRO', 'Banco fora do ar') : { corpo: cortarPagina([turma(1, '6º ano A')], caminho) }
    })
    renderizar()

    expect(await screen.findByRole('alert')).toHaveTextContent('Banco fora do ar')

    falhar = false
    await usuario.click(screen.getByRole('button', { name: 'Tentar novamente' }))
    expect(await screen.findByText('6º ano A')).toBeInTheDocument()
  })
})
