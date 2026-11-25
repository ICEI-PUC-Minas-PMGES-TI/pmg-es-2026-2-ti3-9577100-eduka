import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { gravarSessao } from '../../../../services/acesso/modulosSessao'
import { cortarPagina, parametros, simularApi, type ChamadaApi } from '../../../../test/api'
import { logarComo } from '../../../../test/sessao'
import type {
  AlunoDaLista,
  FaixaAtraso,
  FinanceiroDoAluno,
  ResumoAlunos,
  SituacaoFinanceira,
  SituacaoNaEscola,
} from '../../../../types/pessoas/aluno'
import { Alunos } from './Alunos'

function financeiro(situacao: SituacaoFinanceira, extra: Partial<FinanceiroDoAluno> = {}): FinanceiroDoAluno {
  return {
    situacao,
    valorEmAberto: 0,
    valorEmAtraso: 0,
    diasDeAtraso: null,
    faixaAtraso: null,
    proximoVencimento: null,
    venceEm7Dias: false,
    valorVenceEm7Dias: 0,
    mensalidadeNoMes: true,
    ...extra,
  }
}

function aluno(id: string, nome: string, fin: FinanceiroDoAluno, situacao: SituacaoNaEscola = 'MATRICULADO') {
  return { id, nome, matricula: `2026-${id}`, genero: null, situacao, turmaId: 1, turmaNome: '1A', financeiro: fin }
}

const ALUNOS: AlunoDaLista[] = [
  aluno(
    'ana',
    'Ana Souza',
    financeiro('EM_ATRASO', {
      valorEmAberto: 300,
      valorEmAtraso: 300,
      diasDeAtraso: 75,
      faixaAtraso: 'MAIS_DE_60_DIAS',
    }),
  ),
  aluno(
    'bia',
    'Bia Lima',
    financeiro('A_VENCER', {
      valorEmAberto: 500,
      proximoVencimento: '2026-12-02',
      venceEm7Dias: true,
      valorVenceEm7Dias: 500,
    }),
  ),
  aluno('caio', 'Caio Dias', financeiro('SEM_COBRANCA', { mensalidadeNoMes: false }), 'SEM_TURMA'),
]

/**
 * Faz o papel do GET /alunos/pagina com o financeiro: filtra pela situação,
 * pela situação financeira, faixa de atraso, vencimento e mensalidade, ordena
 * pelo valor em aberto e manda o resumo financeiro da escola inteira. Sem
 * `comFinanceiro`, responde como para a SECRETARIA: sem o financeiro.
 */
function abrir(url: string, comFinanceiro = true): ChamadaApi[] {
  const chamadas = simularApi(({ caminho }) => {
    if (!caminho.startsWith('/alunos/pagina?')) return undefined
    const p = parametros(caminho)
    let filtrados = ALUNOS.filter(
      (a) =>
        (!p.situacao || a.situacao === p.situacao) &&
        (!p.situacaoFinanceira || a.financeiro?.situacao === p.situacaoFinanceira) &&
        (!p.faixaAtraso || a.financeiro?.faixaAtraso === p.faixaAtraso) &&
        (!p.venceEm7Dias || a.financeiro?.venceEm7Dias) &&
        (!p.semMensalidadeNoMes || !a.financeiro?.mensalidadeNoMes),
    )
    if (p.ordenarPor === 'EM_ABERTO') {
      const sinal = p.direcao === 'ASC' ? 1 : -1
      filtrados = [...filtrados].sort(
        (a, b) => sinal * ((a.financeiro?.valorEmAberto ?? 0) - (b.financeiro?.valorEmAberto ?? 0)),
      )
    }
    const contar = (s: SituacaoFinanceira) => ALUNOS.filter((a) => a.financeiro?.situacao === s).length
    const faixa = (f: FaixaAtraso) => ALUNOS.filter((a) => a.financeiro?.faixaAtraso === f).length
    const resumo: ResumoAlunos = {
      total: ALUNOS.length,
      porSituacao: { MATRICULADO: 2, TRANCADO: 0, SEM_TURMA: 1, INATIVO: 0, ANONIMIZADO: 0 },
      financeiro: comFinanceiro
        ? {
            porSituacao: {
              EM_ATRASO: contar('EM_ATRASO'),
              A_VENCER: contar('A_VENCER'),
              EM_DIA: contar('EM_DIA'),
              SEM_COBRANCA: contar('SEM_COBRANCA'),
            },
            porFaixaAtraso: {
              ATE_30_DIAS: faixa('ATE_30_DIAS'),
              DE_31_A_60_DIAS: faixa('DE_31_A_60_DIAS'),
              MAIS_DE_60_DIAS: faixa('MAIS_DE_60_DIAS'),
            },
            valorEmAtraso: 300,
            venceEm7Dias: 1,
            valorVenceEm7Dias: 500,
            semMensalidadeNoMes: 1,
          }
        : null,
    }
    const conteudo = comFinanceiro ? filtrados : filtrados.map((a) => ({ ...a, financeiro: null }))
    return { corpo: { ...cortarPagina(conteudo, caminho), resumo } }
  })
  render(
    <MemoryRouter initialEntries={[url]}>
      <Alunos />
    </MemoryRouter>,
  )
  return chamadas
}

function ultimaConsulta(chamadas: ChamadaApi[]) {
  const consultas = chamadas.filter((c) => c.caminho.startsWith('/alunos/pagina?'))
  return parametros(consultas[consultas.length - 1].caminho)
}

const grupo = (nome: string) => screen.getByRole('group', { name: nome })

describe('Alunos: o financeiro de cada aluno', () => {
  beforeEach(() => {
    Element.prototype.scrollIntoView = vi.fn()
  })

  afterEach(() => {
    localStorage.clear()
  })

  it('o financeiro vê a coluna Em aberto, as pílulas financeiras e os cards financeiros', async () => {
    logarComo('FINANCEIRO')
    abrir('/financeiro/alunos')

    const linhaDaAna = (await screen.findByText('Ana Souza')).closest('tr') as HTMLElement
    expect(within(linhaDaAna).getByText(/R\$\s*300,00/)).toBeInTheDocument()
    expect(within(linhaDaAna).getByText('Em atraso')).toBeInTheDocument()
    expect(within(linhaDaAna).getByText('há 75 dias')).toBeInTheDocument()
    const linhaDaBia = screen.getByText('Bia Lima').closest('tr') as HTMLElement
    expect(within(linhaDaBia).getByText('vence 02/12')).toBeInTheDocument()

    const cards = screen.getByRole('region', { name: 'Resumo financeiro dos alunos' })
    expect(cards).toHaveTextContent(/Em atraso\s*1/)
    expect(cards).toHaveTextContent(/Vencem em 7 dias\s*1/)
    // O FINANCEIRO não escolhe a visão: só o MASTER tem o seletor.
    expect(screen.queryByRole('group', { name: 'Resumo' })).not.toBeInTheDocument()
    expect(within(grupo('Filtrar pela situação financeira')).getByRole('button', { name: /^em atraso/i }))
      .toHaveTextContent('1')
  })

  it('filtra pela situação financeira, com a faixa de atraso só dentro de Em atraso', async () => {
    logarComo('FINANCEIRO')
    const chamadas = abrir('/financeiro/alunos')
    await screen.findByText('Ana Souza')
    expect(screen.queryByRole('group', { name: 'Filtrar pelo tempo de atraso' })).not.toBeInTheDocument()

    await userEvent.click(within(grupo('Filtrar pela situação financeira')).getByRole('button', { name: /^em atraso/i }))
    expect(ultimaConsulta(chamadas)).toEqual({ pagina: '0', tamanho: '10', situacaoFinanceira: 'EM_ATRASO' })

    await userEvent.click(within(grupo('Filtrar pelo tempo de atraso')).getByRole('button', { name: /^até 30 dias/i }))
    expect(await screen.findByText('Nenhum aluno com esses filtros.')).toBeInTheDocument()
    expect(ultimaConsulta(chamadas)).toMatchObject({ situacaoFinanceira: 'EM_ATRASO', faixaAtraso: 'ATE_30_DIAS' })

    // Trocar a situação financeira tira a faixa de atraso.
    await userEvent.click(within(grupo('Filtrar pela situação financeira')).getByRole('button', { name: /^a vencer/i }))
    expect(await screen.findByText('Bia Lima')).toBeInTheDocument()
    expect(ultimaConsulta(chamadas)).toEqual({ pagina: '0', tamanho: '10', situacaoFinanceira: 'A_VENCER' })
  })

  it('combina o filtro da escola com o financeiro e os demais filtros, e limpa todos', async () => {
    logarComo('FINANCEIRO')
    const chamadas = abrir('/financeiro/alunos')
    await screen.findByText('Ana Souza')

    await userEvent.click(within(grupo('Filtrar por situação')).getByRole('button', { name: /^sem turma/i }))
    await userEvent.click(within(grupo('Mais filtros financeiros')).getByRole('button', { name: /sem mensalidade/i }))
    expect(ultimaConsulta(chamadas)).toEqual({
      pagina: '0',
      tamanho: '10',
      situacao: 'SEM_TURMA',
      semMensalidadeNoMes: 'true',
    })
    expect(await screen.findByText('Caio Dias')).toBeInTheDocument()
    expect(screen.queryByText('Ana Souza')).not.toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: 'Limpar filtros' }))
    expect(ultimaConsulta(chamadas)).toEqual({ pagina: '0', tamanho: '10' })
    expect(await screen.findByText('Ana Souza')).toBeInTheDocument()
  })

  it('chega com o filtro da URL aplicado, como no atalho do Início', async () => {
    logarComo('FINANCEIRO')
    const chamadas = abrir('/financeiro/alunos?financeiro=EM_ATRASO&atraso=MAIS_DE_60_DIAS')

    expect(await screen.findByText('Ana Souza')).toBeInTheDocument()
    expect(screen.queryByText('Bia Lima')).not.toBeInTheDocument()
    expect(ultimaConsulta(chamadas)).toMatchObject({ situacaoFinanceira: 'EM_ATRASO', faixaAtraso: 'MAIS_DE_60_DIAS' })
    expect(within(grupo('Filtrar pelo tempo de atraso')).getByRole('button', { name: /^mais de 60 dias/i }))
      .toHaveAttribute('aria-pressed', 'true')
  })

  it('o card Em atraso leva à lista filtrada', async () => {
    logarComo('FINANCEIRO')
    const chamadas = abrir('/financeiro/alunos')
    await screen.findByText('Bia Lima')

    await userEvent.click(screen.getByRole('button', { name: 'Ver alunos em atraso' }))

    expect(ultimaConsulta(chamadas)).toMatchObject({ situacaoFinanceira: 'EM_ATRASO' })
    expect(await screen.findByText('Ana Souza')).toBeInTheDocument()
    expect(screen.queryByText('Bia Lima')).not.toBeInTheDocument()
  })

  it('o título Em aberto ordena do maior para o menor, do menor para o maior e volta ao nome', async () => {
    logarComo('FINANCEIRO')
    const chamadas = abrir('/financeiro/alunos')
    await screen.findByText('Ana Souza')
    const titulo = () => screen.getByRole('columnheader', { name: /em aberto/i })
    expect(titulo()).toHaveAttribute('aria-sort', 'none')

    await userEvent.click(within(titulo()).getByRole('button'))
    expect(ultimaConsulta(chamadas)).toMatchObject({ ordenarPor: 'EM_ABERTO', direcao: 'DESC' })
    expect(titulo()).toHaveAttribute('aria-sort', 'descending')
    // O nome é o primeiro destaque de cada linha, depois das iniciais.
    const nomes = () => screen.getAllByRole('row').slice(1).map((linha) => linha.querySelector('strong')?.textContent)
    await waitFor(() => expect(nomes()).toEqual(['Bia Lima', 'Ana Souza', 'Caio Dias']))

    await userEvent.click(within(titulo()).getByRole('button'))
    expect(ultimaConsulta(chamadas)).toMatchObject({ ordenarPor: 'EM_ABERTO', direcao: 'ASC' })
    expect(titulo()).toHaveAttribute('aria-sort', 'ascending')

    await userEvent.click(within(titulo()).getByRole('button'))
    expect(ultimaConsulta(chamadas)).toEqual({ pagina: '0', tamanho: '10' })
  })

  it('o MASTER escolhe entre o resumo da escola e o financeiro, e a escolha fica guardada', async () => {
    logarComo('MASTER')
    abrir('/master/alunos')
    await screen.findByText('Ana Souza')
    expect(screen.getByRole('region', { name: 'Resumo dos alunos' })).toBeInTheDocument()

    await userEvent.click(within(grupo('Resumo')).getByRole('button', { name: 'Financeiro' }))

    expect(screen.getByRole('region', { name: 'Resumo financeiro dos alunos' })).toBeInTheDocument()
    expect(screen.queryByRole('region', { name: 'Resumo dos alunos' })).not.toBeInTheDocument()
    expect(localStorage.getItem('eduka.alunos.resumo')).toBe('FINANCEIRO')
    // O MASTER continua com as ações de cadastro.
    expect(screen.getByRole('button', { name: /novo aluno/i })).toBeInTheDocument()
  })

  it('o MASTER abre no último resumo que escolheu', async () => {
    localStorage.setItem('eduka.alunos.resumo', 'FINANCEIRO')
    logarComo('MASTER')
    abrir('/master/alunos')

    expect(await screen.findByRole('region', { name: 'Resumo financeiro dos alunos' })).toBeInTheDocument()
    expect(within(grupo('Resumo')).getByRole('button', { name: 'Financeiro' })).toHaveAttribute('aria-pressed', 'true')
  })

  it('sem a gestão financeira contratada, o MASTER não vê nem pede o financeiro', async () => {
    logarComo('MASTER')
    gravarSessao('gestor@escola.com', ['PORTAL_ALUNO'])
    const chamadas = abrir('/master/alunos?financeiro=EM_ATRASO&ordem=EM_ABERTO', false)

    expect(await screen.findByText('Ana Souza')).toBeInTheDocument()
    expect(ultimaConsulta(chamadas)).toEqual({ pagina: '0', tamanho: '10' })
    expect(screen.queryByRole('columnheader', { name: /em aberto/i })).not.toBeInTheDocument()
    expect(screen.queryByRole('group', { name: 'Filtrar pela situação financeira' })).not.toBeInTheDocument()
    expect(screen.queryByRole('group', { name: 'Resumo' })).not.toBeInTheDocument()
  })

  it('a secretaria não vê o financeiro, mesmo com os filtros financeiros na URL', async () => {
    logarComo('SECRETARIA')
    const chamadas = abrir('/secretaria/alunos?situacao=SEM_TURMA&financeiro=EM_ATRASO&vence7=1&ordem=EM_ABERTO', false)

    expect(await screen.findByText('Caio Dias')).toBeInTheDocument()
    expect(ultimaConsulta(chamadas)).toEqual({ pagina: '0', tamanho: '10', situacao: 'SEM_TURMA' })
    expect(screen.queryByRole('columnheader', { name: /em aberto/i })).not.toBeInTheDocument()
    expect(screen.queryByRole('group', { name: 'Mais filtros financeiros' })).not.toBeInTheDocument()
    expect(screen.getByRole('region', { name: 'Resumo dos alunos' })).toBeInTheDocument()
  })
})
