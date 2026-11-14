import { paraIso } from '../../../../components/formulario/CampoData/datas'
import type { Periodo } from '../../../../components/formulario/CampoPeriodo/CampoPeriodo'

export type PeriodoRapido = 'MES' | 'BIMESTRE' | 'SEMESTRE' | 'ANO'

export const PERIODOS_RAPIDOS: { valor: PeriodoRapido; rotulo: string }[] = [
  { valor: 'MES', rotulo: 'Este mês' },
  { valor: 'BIMESTRE', rotulo: 'Este bimestre' },
  { valor: 'SEMESTRE', rotulo: 'Este semestre' },
  { valor: 'ANO', rotulo: 'Este ano' },
]

/**
 * Datas de um período rápido, do primeiro ao último dia dos meses dele (`hoje`
 * em ISO). O bimestre é o mês atual e o anterior, como no protótipo da IU19, e
 * nunca abre quase vazio; o semestre é janeiro a junho ou julho a dezembro.
 */
export function datasDoPeriodoRapido(rapido: PeriodoRapido, hoje: string): Periodo {
  const [ano, mes] = hoje.split('-').map(Number)
  const semestre = mes <= 6 ? { inicio: 1, fim: 6 } : { inicio: 7, fim: 12 }
  const meses = {
    MES: { inicio: mes, fim: mes },
    BIMESTRE: { inicio: mes - 1, fim: mes },
    SEMESTRE: semestre,
    ANO: { inicio: 1, fim: 12 },
  }[rapido]
  // O Date acerta a virada de ano (mês 0 é dezembro do ano anterior) e o dia 0
  // do mês seguinte é o último deste.
  return {
    de: paraIso(new Date(ano, meses.inicio - 1, 1)),
    ate: paraIso(new Date(ano, meses.fim, 0)),
  }
}
