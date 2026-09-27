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
      <StatCard label="Total de escolas" value={total} descricao="Cadastradas na plataforma" destaque />
      <StatCard label="Escolas ativas" value={ativas} descricao="Funcionando normalmente" />
      <StatCard
        label="Assinaturas suspensas"
        value={suspensas}
        descricao={suspensas > 0 ? 'Pedem atenção' : 'Nenhuma no momento'}
        alerta={suspensas > 0}
      />
    </section>
  )
}
