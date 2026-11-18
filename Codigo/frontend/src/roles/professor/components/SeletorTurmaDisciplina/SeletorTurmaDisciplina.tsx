import { Select } from '../../../../components/formulario/Select/Select'
import type { TurmaDisciplina } from '../../hooks/useTurmaDisciplina'
import styles from './SeletorTurmaDisciplina.module.css'

type SeletorTurmaDisciplinaProps = {
  opcoes: TurmaDisciplina[]
  selecionada: TurmaDisciplina | null
  onChange: (chave: string) => void
}

/**
 * Dropdown em pílula com cada "Turma · Disciplina" que o professor leciona,
 * com o rótulo "Turmas" ao lado dizendo para que ele serve.
 */
export function SeletorTurmaDisciplina({ opcoes, selecionada, onChange }: SeletorTurmaDisciplinaProps) {
  return (
    <div className={styles.grupo}>
      {/* O nome do campo para o leitor de tela é o mesmo do rótulo (aria-label): aqui ele só aparece. */}
      <span className={styles.rotulo} aria-hidden="true">
        Turmas
      </span>
      <Select
        opcoes={opcoes.map((o) => ({
          valor: o.chave,
          rotulo: `${o.turma.nome} · ${o.disciplina.nome}${o.turma.ativa ? '' : ' (encerrada)'}`,
        }))}
        valor={selecionada?.chave ?? ''}
        onChange={onChange}
        aria-label="Turmas"
        className={styles.seletor}
      />
    </div>
  )
}
