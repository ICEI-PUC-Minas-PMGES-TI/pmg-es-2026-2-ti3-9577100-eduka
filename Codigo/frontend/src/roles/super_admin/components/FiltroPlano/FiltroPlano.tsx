import { Select, type OpcaoSelect } from '../../../../components/Select/Select'
import { OPCOES_PLANO, type PlanoEscola } from '../../../../types/escola'
import styles from './FiltroPlano.module.css'

// 'sem-plano' pega escolas cadastradas antes do plano existir.
export type ValorFiltroPlano = 'todos' | PlanoEscola | 'sem-plano'

const OPCOES: OpcaoSelect<ValorFiltroPlano>[] = [
  { valor: 'todos', rotulo: 'Todos os planos' },
  ...OPCOES_PLANO,
  { valor: 'sem-plano', rotulo: 'Sem plano' },
]

type FiltroPlanoProps = {
  valor: ValorFiltroPlano
  onChange: (valor: ValorFiltroPlano) => void
}

export function FiltroPlano({ valor, onChange }: FiltroPlanoProps) {
  return (
    <Select
      opcoes={OPCOES}
      valor={valor}
      onChange={onChange}
      aria-label="Filtrar por plano"
      className={`${styles.gatilho} ${valor !== 'todos' ? styles.filtrando : ''}`}
    />
  )
}
