import { describe, expect, it } from 'vitest'
import { descreverUltimaAtividade, diasSemAtividade } from './ultimaAtividade'

// 5/11/2026 às 15:00 em São Paulo (UTC-3).
const AGORA = new Date('2026-11-05T18:00:00Z')

describe('última atividade', () => {
  it('nunca houve atividade', () => {
    expect(descreverUltimaAtividade(null, AGORA)).toBe('Nenhuma ainda')
    expect(diasSemAtividade(null, AGORA)).toBeNull()
  })

  it('hoje e ontem contam pelo dia no fuso da escola, com a hora', () => {
    expect(descreverUltimaAtividade('2026-11-05T12:30:00Z', AGORA)).toBe('Hoje às 09:30')
    // 01:30 UTC do dia 5 ainda é dia 4 em São Paulo.
    expect(descreverUltimaAtividade('2026-11-05T01:30:00Z', AGORA)).toBe('Ontem às 22:30')
  })

  it('até um mês, em dias; depois, a data', () => {
    expect(descreverUltimaAtividade('2026-10-31T15:00:00Z', AGORA)).toBe('Há 5 dias')
    expect(diasSemAtividade('2026-10-31T15:00:00Z', AGORA)).toBe(5)
    expect(descreverUltimaAtividade('2026-08-12T15:00:00Z', AGORA)).toBe('12/08/2026')
  })
})
