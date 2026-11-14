import type { PeriodoLetivo } from '../../../../../types/academico/academico'

type Fase = 'inativo' | 'planejado' | 'encerrado' | 'andamento'
type Situacao = { rotulo: string; tom: 'success' | 'neutral' | 'primary'; fase: Fase }

/** A situação sai das datas e do encerramento feito pela secretaria (IU09). */
export function situacao(periodo: PeriodoLetivo, hoje: string): Situacao {
  if (!periodo.ativo) return { rotulo: 'Inativo', tom: 'neutral', fase: 'inativo' }
  if (periodo.encerradoEm || hoje > periodo.dataFim) return { rotulo: 'Encerrado', tom: 'neutral', fase: 'encerrado' }
  if (hoje < periodo.dataInicio) return { rotulo: 'Planejado', tom: 'primary', fase: 'planejado' }
  return { rotulo: 'Em andamento', tom: 'success', fase: 'andamento' }
}
