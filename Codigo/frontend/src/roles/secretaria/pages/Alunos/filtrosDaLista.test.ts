import { describe, expect, it } from 'vitest'
import { consultaDosFiltros, gravarFiltros, lerFiltros, proximaOrdem, temFiltro } from './filtrosDaLista'

const url = (busca: string) => new URLSearchParams(busca)

describe('filtros da lista de alunos', () => {
  it('lê os filtros da URL e ignora valores desconhecidos', () => {
    expect(lerFiltros(url('situacao=MATRICULADO&financeiro=EM_ATRASO&atraso=MAIS_DE_60_DIAS&vence7=1'), true)).toEqual({
      situacao: 'MATRICULADO',
      financeiro: 'EM_ATRASO',
      faixaAtraso: 'MAIS_DE_60_DIAS',
      venceEm7Dias: true,
      semMensalidade: false,
      ordem: { campo: 'NOME' },
    })
    expect(lerFiltros(url('situacao=QUALQUER&financeiro=DEVENDO&ordem=EM_ABERTO'), true)).toMatchObject({
      situacao: null,
      financeiro: null,
      ordem: { campo: 'EM_ABERTO', direcao: 'DESC' },
    })
  })

  it('a faixa de atraso só vale com o filtro Em atraso', () => {
    expect(lerFiltros(url('financeiro=A_VENCER&atraso=ATE_30_DIAS'), true).faixaAtraso).toBeNull()
    const gravada = gravarFiltros(url('atraso=ATE_30_DIAS'), {
      ...lerFiltros(url(''), true),
      financeiro: 'EM_DIA',
      faixaAtraso: 'ATE_30_DIAS',
    })
    expect(gravada.get('atraso')).toBeNull()
  })

  it('sem o financeiro, ignora os filtros e a ordem financeiros da URL', () => {
    const filtros = lerFiltros(url('situacao=SEM_TURMA&financeiro=EM_ATRASO&vence7=1&ordem=EM_ABERTO'), false)

    expect(consultaDosFiltros(filtros)).toEqual({
      situacao: 'SEM_TURMA',
      situacaoFinanceira: undefined,
      faixaAtraso: undefined,
      venceEm7Dias: undefined,
      semMensalidadeNoMes: undefined,
    })
  })

  it('grava só os filtros aplicados e mantém os outros parâmetros', () => {
    const params = gravarFiltros(url('outro=1&financeiro=EM_DIA'), {
      situacao: null,
      financeiro: null,
      faixaAtraso: null,
      venceEm7Dias: false,
      semMensalidade: true,
      ordem: { campo: 'EM_ABERTO', direcao: 'ASC' },
    })

    expect(params.toString()).toBe('outro=1&semMensalidade=1&ordem=EM_ABERTO&direcao=ASC')
  })

  it('o título Em aberto alterna entre maior primeiro, menor primeiro e a ordem pelo nome', () => {
    expect(proximaOrdem({ campo: 'NOME' })).toEqual({ campo: 'EM_ABERTO', direcao: 'DESC' })
    expect(proximaOrdem({ campo: 'EM_ABERTO', direcao: 'DESC' })).toEqual({ campo: 'EM_ABERTO', direcao: 'ASC' })
    expect(proximaOrdem({ campo: 'EM_ABERTO', direcao: 'ASC' })).toEqual({ campo: 'NOME' })
  })

  it('a ordem não conta como filtro aplicado', () => {
    expect(temFiltro(lerFiltros(url('ordem=EM_ABERTO'), true))).toBe(false)
    expect(temFiltro(lerFiltros(url('semMensalidade=1'), true))).toBe(true)
  })
})
