import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { createMemoryRouter, RouterProvider } from 'react-router'
import { describe, expect, it } from 'vitest'
import { problema, simularApi, type ChamadaApi, type RespostaApi } from '../../../../test/api'
import type { Aula, AulaResumo, ChamadaDoDia } from '../../../../types/academico/aula'
import type { TurmaDoProfessor } from '../../../../types/academico/turmaProfessor'
import { hojeIso } from '../../../../utils/formatters'
import { Chamada } from './Chamada'

const HOJE = hojeIso()
const ANTIGA = '2026-01-10'

const TURMAS: TurmaDoProfessor[] = [
  {
    id: 1,
    nome: '1A',
    codigo: null,
    turno: 'MATUTINO',
    ativa: true,
    periodoLetivoId: 1,
    periodoLetivoNome: '2026',
    cursoId: 1,
    cursoNome: 'Médio',
    disciplinas: [{ id: 10, nome: 'Matemática', codigo: null, cargaHoraria: 60, horarios: [] }],
  },
]

const ANA = { alunoId: 'ana', alunoNome: 'Ana', alunoNumeroMatricula: '1' }
const BIA = { alunoId: 'bia', alunoNome: 'Bia', alunoNumeroMatricula: '2' }

function resumo(id: string, data: string, extra: Partial<AulaResumo> = {}): AulaResumo {
  return {
    id,
    turmaId: 1,
    turmaNome: '1A',
    disciplinaId: 10,
    disciplinaNome: 'Matemática',
    data,
    conteudo: `Conteúdo ${id}`,
    presentes: 1,
    faltas: 1,
    criadoEm: `${data}T10:00:00Z`,
    atualizadoEm: null,
    ...extra,
  }
}

function detalhe(aula: AulaResumo, chamada: Aula['chamada']): Aula {
  return { ...aula, chamada }
}

const NA_CHAMADA = (aluno: typeof ANA, presente: boolean): Aula['chamada'][number] => ({
  ...aluno,
  situacaoMatricula: 'ATIVA',
  presente,
})

type Backend = {
  dias?: Record<string, Partial<ChamadaDoDia>>
  aulas?: Aula[]
  historico?: AulaResumo[]
  extra?: (chamada: ChamadaApi) => RespostaApi | undefined
}

function abrir({ dias = {}, aulas = [], historico = [], extra }: Backend = {}, url = '/') {
  const chamadas = simularApi((chamada) => {
    const especifica = extra?.(chamada)
    if (especifica) return especifica
    const [rota, consulta] = chamada.caminho.split('?')
    if (rota === '/portal/professor/turmas') return { corpo: TURMAS }
    if (chamada.metodo === 'GET' && rota === '/portal/professor/aulas/chamada') {
      const data = new URLSearchParams(consulta).get('data') as string
      return { corpo: { data, alunos: [ANA, BIA], aulas: [], ...dias[data] } }
    }
    if (chamada.metodo === 'GET' && rota === '/portal/professor/aulas') {
      return {
        corpo: { conteudo: historico, pagina: 0, tamanho: 10, totalElementos: historico.length, totalPaginas: 1 },
      }
    }
    const aula = aulas.find((a) => rota === `/portal/professor/aulas/${a.id}`)
    if (aula && chamada.metodo === 'GET') return { corpo: aula }
    return undefined
  })
  render(
    <RouterProvider
      router={createMemoryRouter(
        [
          { path: '/', element: <Chamada /> },
          { path: '/outra', element: <p>Outra tela</p> },
        ],
        { initialEntries: [url] },
      )}
    />,
  )
  return chamadas
}

function presenca(nome: string) {
  return within(screen.getByRole('group', { name: `Presença de ${nome}` }))
}

describe('Chamada', () => {
  it('abre a aula de hoje, ainda por registrar, com todos presentes', async () => {
    const chamadas = abrir()

    expect(await screen.findByRole('heading', { name: 'Aula de hoje' })).toBeInTheDocument()
    expect(screen.getByText('Chamada por registrar')).toBeInTheDocument()
    expect(presenca('Ana').getByRole('button', { name: 'Presente' })).toHaveAttribute('aria-pressed', 'true')
    expect(presenca('Bia').getByRole('button', { name: 'Presente' })).toHaveAttribute('aria-pressed', 'true')
    expect(chamadas.map((c) => c.caminho)).toContain(
      `/portal/professor/aulas/chamada?turmaId=1&disciplinaId=10&data=${HOJE}`,
    )
  })

  it('exige o conteúdo antes de salvar', async () => {
    const chamadas = abrir()

    await userEvent.click(await screen.findByRole('button', { name: 'Salvar chamada' }))

    expect(await screen.findByText('Informe o conteúdo da aula.')).toBeInTheDocument()
    expect(chamadas.some((c) => c.metodo === 'POST')).toBe(false)
  })

  it('registra a chamada com a falta e passa a mostrar a aula salva', async () => {
    const salva = detalhe(resumo('salva', HOJE, { conteudo: 'Frações' }), [NA_CHAMADA(ANA, true), NA_CHAMADA(BIA, false)])
    let registrada = false
    const chamadas = abrir({
      aulas: [salva],
      extra: ({ metodo, caminho }) => {
        if (metodo === 'POST') {
          registrada = true
          return { status: 201, corpo: salva }
        }
        if (caminho.startsWith('/portal/professor/aulas/chamada') && registrada) {
          return { corpo: { data: HOJE, alunos: [ANA, BIA], aulas: [salva] } }
        }
        return undefined
      },
    })

    await userEvent.type(await screen.findByRole('textbox', { name: /Conteúdo da aula/ }), 'Frações')
    await userEvent.click(presenca('Bia').getByRole('button', { name: 'Falta' }))
    expect(screen.getByRole('region', { name: 'Resumo da chamada' })).toHaveTextContent('de 2 alunos')
    await userEvent.click(screen.getByRole('button', { name: 'Salvar chamada' }))

    expect(await screen.findByText('Chamada registrada')).toBeInTheDocument()
    expect(screen.getByRole('status')).toHaveTextContent('1 presente e 1 falta')
    expect(chamadas.find((c) => c.metodo === 'POST')?.corpo).toEqual({
      turmaId: 1,
      disciplinaId: 10,
      data: HOJE,
      conteudo: 'Frações',
      chamada: [
        { alunoId: 'ana', presente: true },
        { alunoId: 'bia', presente: false },
      ],
    })
    expect(presenca('Bia').getByRole('button', { name: 'Falta' })).toHaveAttribute('aria-pressed', 'true')
  }, 30000)

  it('explica a recusa quando a lista de alunos mudou e mantém o que foi digitado', async () => {
    abrir({
      extra: ({ metodo }) =>
        metodo === 'POST' ? problema(400, 'ERRO', 'Chamada incompleta: 1 aluno(s) com matricula ativa') : undefined,
    })

    const conteudo = await screen.findByRole('textbox', { name: /Conteúdo da aula/ })
    await userEvent.type(conteudo, 'Frações')
    await userEvent.click(screen.getByRole('button', { name: 'Salvar chamada' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('A lista de alunos da turma mudou')
    expect(conteudo).toHaveValue('Frações')
  })

  it('no histórico, abre uma aula antiga e corrige a chamada com PUT', async () => {
    const antiga = resumo('antiga', ANTIGA)
    const completa = detalhe(antiga, [NA_CHAMADA(ANA, true), NA_CHAMADA(BIA, false)])
    const chamadas = abrir({
      dias: { [ANTIGA]: { alunos: [ANA, BIA], aulas: [antiga] } },
      aulas: [completa],
      historico: [antiga],
      extra: ({ metodo }) => (metodo === 'PUT' ? { corpo: completa } : undefined),
    })

    await userEvent.click(await screen.findByRole('button', { name: 'Abrir a aula de 10/01/2026' }))

    expect(await screen.findByRole('heading', { name: 'Aula de 10/01/2026' })).toBeInTheDocument()
    expect(screen.getByRole('textbox', { name: /Conteúdo da aula/ })).toHaveValue('Conteúdo antiga')
    await userEvent.click(presenca('Bia').getByRole('button', { name: 'Presente' }))
    await userEvent.click(screen.getByRole('button', { name: 'Salvar alterações' }))

    await screen.findByText(/Chamada de 10\/01\/2026 salva/)
    const put = chamadas.find((c) => c.metodo === 'PUT')
    expect(put?.caminho).toBe('/portal/professor/aulas/antiga')
    expect(put?.corpo).toEqual({
      conteudo: 'Conteúdo antiga',
      chamada: [
        { alunoId: 'ana', presente: true },
        { alunoId: 'bia', presente: true },
      ],
    })
  })

  it('na correção, envia também quem entrou na turma e ainda não tinha presença', async () => {
    const antiga = resumo('antiga', ANTIGA)
    const chamadas = abrir(
      {
        dias: { [ANTIGA]: { alunos: [ANA, BIA], aulas: [antiga] } },
        aulas: [detalhe(antiga, [NA_CHAMADA(ANA, true)])],
        extra: ({ metodo }) => (metodo === 'PUT' ? { corpo: detalhe(antiga, []) } : undefined),
      },
      `/?data=${ANTIGA}`,
    )

    const bia = (await screen.findByText('Bia')).closest('li') as HTMLElement
    expect(within(bia).getByText('Sem registro nesta aula')).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Salvar alterações' }))

    await screen.findByText(/Chamada de 10\/01\/2026 salva/)
    expect(chamadas.find((c) => c.metodo === 'PUT')?.corpo).toMatchObject({
      chamada: [
        { alunoId: 'ana', presente: true },
        { alunoId: 'bia', presente: true },
      ],
    })
  })

  it('mostra quem saiu da turma na aula antiga', async () => {
    const antiga = resumo('antiga', ANTIGA)
    abrir(
      {
        dias: { [ANTIGA]: { alunos: [ANA], aulas: [antiga] } },
        aulas: [detalhe(antiga, [NA_CHAMADA(ANA, true), { ...BIA, situacaoMatricula: null, presente: true }])],
      },
      `/?data=${ANTIGA}`,
    )

    const bia = (await screen.findByText('Bia')).closest('li') as HTMLElement
    expect(within(bia).getByText('Saiu da turma')).toBeInTheDocument()
  })

  it('num dia com aula, permite registrar outra e cancelar', async () => {
    const hoje = resumo('hoje', HOJE)
    abrir({
      dias: { [HOJE]: { alunos: [ANA, BIA], aulas: [hoje] } },
      aulas: [detalhe(hoje, [NA_CHAMADA(ANA, true), NA_CHAMADA(BIA, true)])],
    })

    await userEvent.click(await screen.findByRole('button', { name: 'Registrar outra aula neste dia' }))
    expect(await screen.findByText('Chamada por registrar')).toBeInTheDocument()
    expect(screen.getByRole('textbox', { name: /Conteúdo da aula/ })).toHaveValue('')

    await userEvent.click(screen.getByRole('button', { name: 'Cancelar' }))
    expect(await screen.findByText('Chamada registrada')).toBeInTheDocument()
    expect(screen.getByRole('textbox', { name: /Conteúdo da aula/ })).toHaveValue('Conteúdo hoje')
  })

  it('exclui a aula depois de confirmar', async () => {
    const hoje = resumo('hoje', HOJE)
    let excluida = false
    const chamadas = abrir({
      aulas: [detalhe(hoje, [NA_CHAMADA(ANA, true), NA_CHAMADA(BIA, true)])],
      extra: ({ metodo, caminho }) => {
        if (metodo === 'DELETE') {
          excluida = true
          return { status: 204 }
        }
        if (caminho.startsWith('/portal/professor/aulas/chamada')) {
          return { corpo: { data: HOJE, alunos: [ANA, BIA], aulas: excluida ? [] : [hoje] } }
        }
        return undefined
      },
    })

    await userEvent.click(await screen.findByRole('button', { name: 'Excluir aula' }))
    expect(chamadas.some((c) => c.metodo === 'DELETE')).toBe(false)
    const dialogo = screen.getByRole('dialog', { name: 'Excluir aula' })
    await userEvent.click(within(dialogo).getByRole('button', { name: 'Excluir' }))

    expect(await screen.findByText('Chamada por registrar')).toBeInTheDocument()
    expect(chamadas.find((c) => c.metodo === 'DELETE')?.caminho).toBe('/portal/professor/aulas/hoje')
  })

  it('pede confirmação antes de trocar de aula com alteração não salva', async () => {
    const antiga = resumo('antiga', ANTIGA)
    abrir({
      dias: { [ANTIGA]: { alunos: [ANA, BIA], aulas: [antiga] } },
      aulas: [detalhe(antiga, [NA_CHAMADA(ANA, true), NA_CHAMADA(BIA, true)])],
      historico: [antiga],
    })

    await userEvent.click((await screen.findAllByRole('button', { name: 'Falta' }))[0])
    await userEvent.click(await screen.findByRole('button', { name: 'Abrir a aula de 10/01/2026' }))

    expect(await screen.findByText(/ainda não foram salvas/)).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Aula de hoje' })).toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: 'Sair sem salvar' }))
    expect(await screen.findByRole('heading', { name: 'Aula de 10/01/2026' })).toBeInTheDocument()
  })

  it('não oferece chamada num dia sem alunos', async () => {
    abrir({ dias: { [ANTIGA]: { alunos: [] } } }, `/?data=${ANTIGA}`)

    expect(await screen.findByText('Nenhum aluno para a chamada de 10/01/2026.')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Salvar chamada' })).not.toBeInTheDocument()
  })

  it('data futura na URL volta para hoje', async () => {
    const chamadas = abrir({}, '/?data=2999-01-01')

    expect(await screen.findByRole('heading', { name: 'Aula de hoje' })).toBeInTheDocument()
    expect(chamadas.some((c) => c.caminho.includes('2999'))).toBe(false)
  })

  it('o calendário não deixa escolher dia futuro', async () => {
    abrir()

    await userEvent.click(await screen.findByRole('button', { name: /Dia da aula/ }))
    const calendario = screen.getByRole('dialog', { name: 'Escolher data' })
    const amanha = new Date()
    amanha.setDate(amanha.getDate() + 1)
    const mes = new Intl.DateTimeFormat('pt-BR', { month: 'long' }).format(amanha)
    const rotuloAmanha = new RegExp(`(?<!\\d)${amanha.getDate()} de ${mes} de ${amanha.getFullYear()}`, 'i')

    expect(within(calendario).getByRole('button', { name: rotuloAmanha })).toBeDisabled()
    expect(within(calendario).getByRole('button', { name: /Hoje/ })).toBeEnabled()
  })
})
