import { describe, expect, it } from 'vitest'
import type { AvaliacaoDoAluno, TipoAvaliacao } from '../../../../types/portal/portalAluno'
import { agruparPorDia, contarPorTipo, diaPorExtenso, diasAte, quando } from './agenda'

function avaliacao(id: string, data: string, tipo: TipoAvaliacao = 'PROVA'): AvaliacaoDoAluno {
  return {
    id,
    turmaId: 1,
    turmaNome: '1A',
    disciplinaId: 1,
    disciplinaNome: 'Matemática',
    tipo,
    titulo: id,
    descricao: null,
    data,
    valor: 10,
    nota: null,
    observacao: null,
    bimestre: 4,
  }
}

describe('agenda do aluno', () => {
  it('conta os dias entre datas ISO, atravessando mês e ano', () => {
    expect(diasAte('2026-11-16', '2026-11-16')).toBe(0)
    expect(diasAte('2026-11-16', '2026-11-27')).toBe(11)
    expect(diasAte('2026-12-30', '2027-01-02')).toBe(3)
    expect(diasAte('2026-11-16', '2026-11-12')).toBe(-4)
  })

  it('diz quanto falta (ou quanto passou) em palavras', () => {
    expect(quando(0)).toBe('Hoje')
    expect(quando(1)).toBe('Amanhã')
    expect(quando(11)).toBe('Em 11 dias')
    expect(quando(-1)).toBe('Ontem')
    expect(quando(-5)).toBe('Há 5 dias')
  })

  it('escreve o dia por extenso sem o fuso mudar a data', () => {
    expect(diaPorExtenso('2026-11-27')).toBe('sexta-feira, 27 de novembro')
  })

  it('agrupa as avaliações seguidas do mesmo dia', () => {
    const dias = agruparPorDia([
      avaliacao('a', '2026-11-27'),
      avaliacao('b', '2026-11-27'),
      avaliacao('c', '2026-11-30'),
    ])
    expect(dias.map((d) => [d.data, d.avaliacoes.map((a) => a.id)])).toEqual([
      ['2026-11-27', ['a', 'b']],
      ['2026-11-30', ['c']],
    ])
  })

  it('conta por tipo só os tipos que aparecem, no singular e no plural', () => {
    expect(contarPorTipo([avaliacao('a', '2026-11-27'), avaliacao('b', '2026-11-27', 'TAREFA')])).toBe(
      '1 prova · 1 tarefa',
    )
    expect(
      contarPorTipo([avaliacao('a', '2026-11-27', 'TRABALHO'), avaliacao('b', '2026-11-27', 'TRABALHO')]),
    ).toBe('2 trabalhos')
  })
})
