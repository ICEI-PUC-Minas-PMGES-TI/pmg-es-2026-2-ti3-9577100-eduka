import { Badge } from '../../../../components/Badge/Badge'
import { Icon } from '../../../../components/Icon/Icon'
import { ROTULOS_TURNO, type TurmaDoProfessor } from '../../../../types/turmaProfessor'
import styles from './TurmaCard.module.css'

type TurmaCardProps = {
  turma: TurmaDoProfessor
}

/** Uma turma do professor, com as disciplinas que ele leciona nela. */
export function TurmaCard({ turma }: TurmaCardProps) {
  const quantidade = turma.disciplinas.length

  return (
    <article className={`${styles.card} ${turma.ativa ? '' : styles.inativa}`}>
      <header className={styles.topo}>
        <div className={styles.titulo}>
          <h2 className={styles.nome}>{turma.nome}</h2>
          {turma.codigo && <span className={styles.codigo}>{turma.codigo}</span>}
        </div>
        {turma.ativa ? <Badge tone="success">Ativa</Badge> : <Badge tone="neutral">Encerrada</Badge>}
      </header>

      <dl className={styles.detalhes}>
        <div>
          <dt>Curso</dt>
          <dd>{turma.cursoNome}</dd>
        </div>
        <div>
          <dt>Período</dt>
          <dd>{turma.periodoLetivoNome}</dd>
        </div>
        <div>
          <dt>Turno</dt>
          <dd>{ROTULOS_TURNO[turma.turno] ?? turma.turno}</dd>
        </div>
      </dl>

      <div className={styles.disciplinas}>
        <h3 className={styles.rotulo}>
          {quantidade === 1 ? 'Sua disciplina' : `Suas disciplinas (${quantidade})`}
        </h3>
        <ul>
          {turma.disciplinas.map((disciplina) => (
            <li key={disciplina.id} className={styles.disciplina}>
              <span className={styles.icone} aria-hidden="true">
                <Icon name="book" size={15} />
              </span>
              <span className={styles.disciplinaNome}>
                {disciplina.nome}
                {disciplina.codigo && <small> · {disciplina.codigo}</small>}
              </span>
              <span className={styles.carga}>{disciplina.cargaHoraria} h</span>
            </li>
          ))}
        </ul>
      </div>
    </article>
  )
}
