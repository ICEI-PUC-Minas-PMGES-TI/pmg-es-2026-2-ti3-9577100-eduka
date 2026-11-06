import type { UsoEscola as Uso } from '../../../../types/escola'
import { descreverUltimaAtividade, diasSemAtividade } from './ultimaAtividade'
import styles from './UsoEscola.module.css'

const MESES = ['jan', 'fev', 'mar', 'abr', 'mai', 'jun', 'jul', 'ago', 'set', 'out', 'nov', 'dez']

/** Sem atividade há mais que isso, a última atividade fica em vermelho. */
export const DIAS_PARA_ALERTA = 14

/** "2026-11" -> "nov". */
function mesCurto(mes: string): string {
  return MESES[Number(mes.split('-')[1]) - 1]
}

/**
 * Uso da escola na linha do Painel de Escolas (IU02): alunos, usuários
 * ativos, cobranças lançadas no mês e a última atividade.
 */
export function UsoEscola({ uso, agora }: { uso: Uso | undefined; agora?: Date }) {
  // Ainda carregando, com erro (o Painel avisa) ou escola recém-cadastrada: a linha fica sem o uso.
  if (!uso) return null

  const dias = diasSemAtividade(uso.ultimaAtividade, agora)
  const parada = dias === null || dias > DIAS_PARA_ALERTA

  return (
    <dl className={styles.uso} aria-label="Uso da escola">
      <div className={styles.item}>
        <dt>Alunos</dt>
        <dd>{uso.alunos}</dd>
      </div>
      <div className={styles.item}>
        <dt>Usuários ativos</dt>
        <dd>{uso.usuariosAtivos}</dd>
      </div>
      <div className={styles.item}>
        <dt>Cobranças em {mesCurto(uso.mes)}</dt>
        <dd>{uso.cobrancasNoMes}</dd>
      </div>
      <div className={styles.item}>
        <dt>Última atividade</dt>
        <dd className={parada ? styles.parada : undefined}>{descreverUltimaAtividade(uso.ultimaAtividade, agora)}</dd>
      </div>
    </dl>
  )
}
