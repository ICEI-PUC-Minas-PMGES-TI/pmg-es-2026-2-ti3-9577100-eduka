import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { createMemoryRouter, RouterProvider } from 'react-router'
import { afterAll, describe, expect, it, vi } from 'vitest'
import { problema, simularApi, type ChamadaApi, type RespostaApi } from '../../../../test/api'
import type { Aula, AulaResumo, ChamadaDoDia } from '../../../../types/academico/aula'
import type { TurmaDoProfessor } from '../../../../types/academico/turmaProfessor'
import { hojeIso } from '../../../../utils/formatters'
import { Chamada } from './Chamada'

// Relógio fixo numa quarta (dia de aula de Matemática no fixture), para a tela abrir sempre em hoje.
vi.useFakeTimers({ toFake: ['Date'] })
vi.setSystemTime(new Date('2026-11-25T15:00:00Z'))
afterAll(() => {
  vi.useRealTimers()
})

const HOJE = hojeIso()
/** Uma segunda: dia de aula de Matemática. */
const ANTIGA = '2026-01-12'

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
    alunosAtivos: 30,
    disciplinas: [
      {
        id: 10,
        nome: 'Matemática',
        codigo: null,
        cargaHoraria: 60,
        horarios: [
          { diaSemana: 'QUARTA', horaInicio: '09:20:00', horaFim: '10:10:00' },
          { diaSemana: 'SEGUNDA', horaInicio: '07:30:00', horaFim: '08:20:00' },
        ],
      },
      { id: 11, nome: 'Física', codigo: null, cargaHoraria: 60, horarios: [] },
    ],
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
      // Como o backend: dia de aula é o da grade da disciplina (Matemática, segunda e quarta; Física, nenhum).
      const disciplina = new URLSearchParams(consulta).get('disciplinaId')
      const diaDeAula = disciplina === '10' && [1, 3].includes(new Date(`${data}T12:00`).getDay())
      return { corpo: { data, diaDeAula, alunos: [ANA, BIA], aulas: [], ...dias[data] } }
    }
    // Histórico como o backend: filtrado por datas e conteúdo, a mais recente primeiro; /meses conta por mês.
    const filtro = new URLSearchParams(consulta)
    const filtradas = historico
      .filter((a) => !filtro.get('de') || a.data >= (filtro.get('de') as string))
      .filter((a) => !filtro.get('ate') || a.data <= (filtro.get('ate') as string))
      .filter((a) => a.conteudo.toLowerCase().includes((filtro.get('busca') ?? '').toLowerCase()))
      .sort((a, b) => b.data.localeCompare(a.data))
    if (chamada.metodo === 'GET' && rota === '/portal/professor/aulas') {
      return {
        corpo: { conteudo: filtradas, pagina: 0, tamanho: 100, totalElementos: filtradas.length, totalPaginas: 1 },
      }
    }
    if (chamada.metodo === 'GET' && rota === '/portal/professor/aulas/meses') {
      const meses = [...new Set(filtradas.map((a) => a.data.slice(0, 7)))].map((mes) => {
        const doMes = filtradas.filter((a) => a.data.startsWith(mes))
        return {
          mes,
          aulas: doMes.length,
          presentes: doMes.reduce((t, a) => t + a.presentes, 0),
          faltas: doMes.reduce((t, a) => t + a.faltas, 0),
        }
      })
      return { corpo: meses }
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
          return { corpo: { data: HOJE, diaDeAula: true, alunos: [ANA, BIA], aulas: [salva] } }
        }
        return undefined
      },
    })

    await userEvent.type(await screen.findByRole('textbox', { name: /Conteúdo da aula/ }), 'Frações')
    await userEvent.click(presenca('Bia').getByRole('button', { name: 'Falta' }))
    const painel = screen.getByRole('region', { name: 'Resumo da chamada' })
    expect(painel).toHaveTextContent('1 de 2 presente')
    expect(painel).toHaveTextContent('50%')
    expect(painel).toHaveTextContent('1 falta')
    expect(screen.getByRole('region', { name: 'Aula do dia' })).toHaveTextContent('Quarta-feira · 1A · Matemática')
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

    await userEvent.click(await screen.findByRole('button', { name: 'Abrir a aula de 12/01/2026' }))

    expect(await screen.findByRole('heading', { name: 'Aula de 12/01/2026' })).toBeInTheDocument()
    expect(screen.getByRole('textbox', { name: /Conteúdo da aula/ })).toHaveValue('Conteúdo antiga')
    await userEvent.click(presenca('Bia').getByRole('button', { name: 'Presente' }))
    await userEvent.click(screen.getByRole('button', { name: 'Salvar alterações' }))

    await screen.findByText(/Chamada de 12\/01\/2026 salva/)
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

    await screen.findByText(/Chamada de 12\/01\/2026 salva/)
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
          return { corpo: { data: HOJE, diaDeAula: true, alunos: [ANA, BIA], aulas: excluida ? [] : [hoje] } }
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
    await userEvent.click(await screen.findByRole('button', { name: 'Abrir a aula de 12/01/2026' }))

    expect(await screen.findByText(/ainda não foram salvas/)).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Aula de hoje' })).toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: 'Sair sem salvar' }))
    expect(await screen.findByRole('heading', { name: 'Aula de 12/01/2026' })).toBeInTheDocument()
  })

  it('não oferece chamada num dia sem alunos', async () => {
    abrir({ dias: { [ANTIGA]: { alunos: [] } } }, `/?data=${ANTIGA}`)

    expect(await screen.findByText('Nenhum aluno para a chamada de 12/01/2026.')).toBeInTheDocument()
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

  it('o calendário marca os dias de aula da disciplina na turma', async () => {
    abrir()

    await userEvent.click(await screen.findByRole('button', { name: /Dia da aula/ }))
    const calendario = screen.getByRole('dialog', { name: 'Escolher data' })

    expect(within(calendario).getByText('Dias de aula de Matemática no 1A: Seg e Qua.')).toBeInTheDocument()
    // Toda segunda e quarta do mês ganham a marca; terça não.
    const segundas = calendario.querySelectorAll('td[data-day]')
    const marcadas = [...segundas].filter((dia) => dia.className.includes('diaDestacado'))
    expect(marcadas.length).toBeGreaterThanOrEqual(8)
    expect(marcadas.every((dia) => [1, 3].includes(new Date(`${dia.getAttribute('data-day')}T12:00`).getDay()))).toBe(
      true,
    )
    // Só dá para escolher dia de aula: a terça desta semana fica desabilitada.
    expect(within(calendario).getByRole('button', { name: /24 de novembro de 2026/i })).toBeDisabled()
    expect(within(calendario).getByRole('button', { name: /23 de novembro de 2026/i })).toBeEnabled()
  })

  it('num dia sem aula da disciplina, não oferece chamada nova e leva à última aula', async () => {
    abrir({}, '/?turma=1&disciplina=10&data=2026-11-24')

    expect(await screen.findByText('Não há aula de Matemática no 1A em 24/11/2026.')).toBeInTheDocument()
    expect(screen.getByText('A chamada é feita nos dias de aula da grade: Seg e Qua.')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Salvar chamada' })).not.toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: 'Ir para a última aula (25/11/2026)' }))

    expect(await screen.findByRole('heading', { name: 'Aula de hoje' })).toBeInTheDocument()
  })

  it('num dia sem aula, a aula já registrada continua abrindo e corrigindo, mas não dá para criar outra', async () => {
    const sabado = '2026-11-21'
    const registrada = detalhe(resumo('sab', sabado), [NA_CHAMADA(ANA, true), NA_CHAMADA(BIA, true)])
    abrir({ dias: { [sabado]: { aulas: [registrada] } }, aulas: [registrada] }, `/?turma=1&disciplina=10&data=${sabado}`)

    expect(await screen.findByRole('heading', { name: 'Aula de 21/11/2026' })).toBeInTheDocument()
    expect(await screen.findByRole('button', { name: 'Salvar alterações' })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /outra aula/i })).not.toBeInTheDocument()
  })

  it('sem dia na URL e hoje sem aula, abre no último dia de aula', async () => {
    // Quinta, 26/11: a última aula de Matemática foi na quarta, 25/11.
    vi.setSystemTime(new Date('2026-11-26T15:00:00Z'))
    try {
      const chamadas = abrir()

      expect(await screen.findByRole('heading', { name: 'Aula de 25/11/2026' })).toBeInTheDocument()
      expect(chamadas.map((c) => c.caminho)).toContain(
        '/portal/professor/aulas/chamada?turmaId=1&disciplinaId=10&data=2026-11-25',
      )
    } finally {
      vi.setSystemTime(new Date('2026-11-25T15:00:00Z'))
    }
  })

  it('sem horário na grade, explica que a chamada depende da secretaria', async () => {
    abrir({}, '/?turma=1&disciplina=11')

    expect(await screen.findByText('A grade do 1A ainda não tem horário de Física.')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Salvar chamada' })).not.toBeInTheDocument()
  })

  it('sem horário na grade, o calendário avisa e não marca dia nenhum', async () => {
    abrir({}, '/?turma=1&disciplina=11')

    await userEvent.click(await screen.findByRole('button', { name: /Dia da aula/ }))
    const calendario = screen.getByRole('dialog', { name: 'Escolher data' })

    expect(within(calendario).getByText('A grade do 1A ainda não tem horário de Física.')).toBeInTheDocument()
    expect(calendario.querySelector('[class*="diaDestacado"]')).toBeNull()
  })

  it('o histórico agrupa por mês, com o resumo e as faltas de cada aula', async () => {
    abrir({
      historico: [
        resumo('nov23', '2026-11-23', { conteudo: 'Frações equivalentes', presentes: 2, faltas: 0 }),
        resumo('nov18', '2026-11-18', { conteudo: 'Frações', presentes: 1, faltas: 1 }),
        resumo('out21', '2026-10-21', { conteudo: 'Logaritmos', presentes: 2, faltas: 0 }),
      ],
    })
    const historico = await screen.findByRole('region', { name: 'Histórico de aulas' })

    expect(await within(historico).findByText('3 aulas · frequência média 83%')).toBeInTheDocument()
    expect(within(historico).getByRole('heading', { name: 'Novembro 2026 · 2 aulas' })).toBeInTheDocument()
    const dia23 = within(historico).getByRole('button', { name: 'Abrir a aula de 23/11/2026' })
    expect(dia23).toHaveTextContent('23Seg')
    expect(dia23).toHaveAccessibleDescription('Frações equivalentesTodos presentes')
    expect(within(historico).getByRole('button', { name: 'Abrir a aula de 18/11/2026' })).toHaveTextContent('1 falta')
    // Outubro só vem ao pedir, mês a mês.
    expect(within(historico).queryByRole('heading', { name: /Outubro/ })).not.toBeInTheDocument()

    await userEvent.click(within(historico).getByRole('button', { name: /Carregar outubro/ }))

    expect(await within(historico).findByRole('heading', { name: 'Outubro 2026 · 1 aula' })).toBeInTheDocument()
    expect(within(historico).getByRole('button', { name: 'Abrir a aula de 21/10/2026' })).toBeInTheDocument()
    expect(within(historico).queryByRole('button', { name: /Carregar/ })).not.toBeInTheDocument()
  })

  it('filtra o histórico pelo mês atual e mostra o vazio do filtro', async () => {
    const chamadas = abrir({ historico: [resumo('antiga', ANTIGA)] })
    expect(await screen.findByRole('button', { name: 'Abrir a aula de 12/01/2026' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Todas' })).toHaveAttribute('aria-pressed', 'true')

    await userEvent.click(screen.getByRole('button', { name: 'Este mês' }))

    expect(await screen.findByText('Nenhuma aula registrada neste mês.')).toBeInTheDocument()
    const consulta = new URLSearchParams(
      chamadas.filter((c) => c.caminho.startsWith('/portal/professor/aulas/meses?')).at(-1)?.caminho.split('?')[1],
    )
    expect(consulta.get('de')).toBe(`${HOJE.slice(0, 7)}-01`)
    expect(consulta.get('ate')).toBe(HOJE)
    expect(screen.getByRole('button', { name: 'Este mês' })).toHaveAttribute('aria-pressed', 'true')
  })

  it('busca as aulas pelo conteúdo', async () => {
    const chamadas = abrir({
      historico: [
        resumo('log', '2026-11-18', { conteudo: 'Introdução a logaritmos' }),
        resumo('frac', '2026-11-23', { conteudo: 'Frações' }),
      ],
    })
    await screen.findByRole('button', { name: 'Abrir a aula de 23/11/2026' })

    await userEvent.click(screen.getByRole('button', { name: 'Buscar' }))
    await userEvent.type(screen.getByRole('searchbox', { name: 'Buscar no conteúdo das aulas' }), 'LOGARITMO')

    expect(await screen.findByRole('heading', { name: 'Novembro 2026 · 1 aula' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Abrir a aula de 18/11/2026' })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Abrir a aula de 23/11/2026' })).not.toBeInTheDocument()
    expect(chamadas.some((c) => c.caminho.includes('busca=LOGARITMO'))).toBe(true)

    await userEvent.clear(screen.getByRole('searchbox', { name: 'Buscar no conteúdo das aulas' }))
    await userEvent.type(screen.getByRole('searchbox', { name: 'Buscar no conteúdo das aulas' }), 'trigonometria')
    expect(await screen.findByText('Nenhuma aula com "trigonometria" no conteúdo.')).toBeInTheDocument()
  })
})
