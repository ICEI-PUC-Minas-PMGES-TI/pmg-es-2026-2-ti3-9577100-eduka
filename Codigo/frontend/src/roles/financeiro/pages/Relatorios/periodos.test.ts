import { describe, expect, it } from 'vitest'
import { datasDoPeriodoRapido } from './periodos'

describe('datasDoPeriodoRapido', () => {
  it('este mês vai do primeiro ao último dia', () => {
    expect(datasDoPeriodoRapido('MES', '2026-02-15')).toEqual({ de: '2026-02-01', ate: '2026-02-28' })
  })

  it('o bimestre é o mês atual e o anterior', () => {
    expect(datasDoPeriodoRapido('BIMESTRE', '2026-09-30')).toEqual({ de: '2026-08-01', ate: '2026-09-30' })
  })

  it('em janeiro, o bimestre começa em dezembro do ano anterior', () => {
    expect(datasDoPeriodoRapido('BIMESTRE', '2026-01-10')).toEqual({ de: '2025-12-01', ate: '2026-01-31' })
  })

  it('o semestre é janeiro a junho ou julho a dezembro', () => {
    expect(datasDoPeriodoRapido('SEMESTRE', '2026-06-30')).toEqual({ de: '2026-01-01', ate: '2026-06-30' })
    expect(datasDoPeriodoRapido('SEMESTRE', '2026-07-01')).toEqual({ de: '2026-07-01', ate: '2026-12-31' })
  })

  it('este ano vai de janeiro a dezembro', () => {
    expect(datasDoPeriodoRapido('ANO', '2026-11-06')).toEqual({ de: '2026-01-01', ate: '2026-12-31' })
  })
})
