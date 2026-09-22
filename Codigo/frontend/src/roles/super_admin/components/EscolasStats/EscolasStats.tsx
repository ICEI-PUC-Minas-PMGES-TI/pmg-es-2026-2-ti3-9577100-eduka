import { Badge } from '../../../../components/Badge/Badge'
import { StatCard } from '../../../../components/StatCard/StatCard'
import type { Escola } from '../../../../types/escola'
import styles from './EscolasStats.module.css'

type EscolasStatsProps = {
  escolas: Escola[]
}

export function EscolasStats({ escolas }: EscolasStatsProps) {
  const total = escolas.length
  const ativas = escolas.filter((e) => e.ativo).length
  const suspensas = total - ativas

  return (
    <section className={styles.grid} aria-label="Resumo das escolas">
      <StatCard label="Total de escolas" value={total} />
      <StatCard
        label="Escolas ativas"
        value={ativas}
        badge={<Badge tone="success">Funcionando</Badge>}
      />
      <StatCard
        label="Assinaturas suspensas"
        value={suspensas}
        badge={suspensas > 0 ? <Badge tone="danger">Atenção</Badge> : undefined}
      />
    </section>
  )
}
