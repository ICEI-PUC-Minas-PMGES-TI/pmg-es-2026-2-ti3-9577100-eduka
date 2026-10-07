import { Select } from '../../../../components/Select/Select'
import type { TurmaDisciplina } from '../../hooks/useTurmaDisciplina'
import styles from './SeletorTurmaDisciplina.module.css'

type SeletorTurmaDisciplinaProps = {
  opcoes: TurmaDisciplina[]
  selecionada: TurmaDisciplina | null
  onChange: (chave: string) => void
}

/** Dropdown em pílula com cada "Turma · Disciplina" que o professor leciona. */
export function SeletorTurmaDisciplina({ opcoes, selecionada, onChange }: SeletorTurmaDisciplinaProps) {
  return (
    <Select
      opcoes={opcoes.map((o) => ({
        valor: o.chave,
        rotulo: `${o.turma.nome} · ${o.disciplina.nome}${o.turma.ativa ? '' : ' (encerrada)'}`,
      }))}
      valor={selecionada?.chave ?? ''}
      onChange={onChange}
      aria-label="Turma e disciplina"
      className={styles.seletor}
    />
  )
}
