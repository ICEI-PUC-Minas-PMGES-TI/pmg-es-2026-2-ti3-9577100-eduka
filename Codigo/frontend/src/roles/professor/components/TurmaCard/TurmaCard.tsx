import { Badge } from '../../../../components/Badge/Badge'
import { Button } from '../../../../components/Button/Button'
import { Icon } from '../../../../components/Icon/Icon'
import type { AvaliacaoResumo } from '../../../../types/dashboard'
import { descreverHorarios } from '../../../../types/horario'
import { ROTULOS_TURNO, type TurmaDoProfessor } from '../../../../types/turmaProfessor'
import { plural } from '../../../../utils/formatters'
import styles from './TurmaCard.module.css'

type TurmaCardProps = {
  turma: TurmaDoProfessor
  /** Avaliações desta turma já realizadas com aluno sem nota. */
  pendentes?: AvaliacaoResumo[]
  /** Lançar notas (IU14) já na turma; vai para a primeira disciplina dele nela. */
  onLancarNotas: (turma: TurmaDoProfessor) => void
  /** Abre o cadastro de avaliação com esta turma já escolhida. */
  onCriarAvaliacao: (turma: TurmaDoProfessor) => void
  /** Ver alunos (IU13). */
  onVerAlunos: (turma: TurmaDoProfessor) => void
}

/** Uma turma do professor, com as disciplinas que ele leciona nela e o horário de cada uma. */
export function TurmaCard({ turma, pendentes = [], onLancarNotas, onCriarAvaliacao, onVerAlunos }: TurmaCardProps) {
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
          {turma.disciplinas.map((disciplina) => {
            const aLancar = pendentes.filter((p) => p.disciplinaId === disciplina.id).length
            return (
              <li key={disciplina.id} className={styles.disciplina}>
                <span className={styles.icone} aria-hidden="true">
                  <Icon name="book" size={15} />
                </span>
                <span className={styles.disciplinaNome}>
                  {disciplina.nome}
                  {disciplina.codigo && <small> · {disciplina.codigo}</small>}
                  <small className={styles.horario}>{descreverHorarios(disciplina.horarios) ?? 'Horário a definir'}</small>
                  {aLancar > 0 && (
                    <small className={styles.pendente}>
                      {aLancar} {plural(aLancar, 'avaliação com nota a lançar', 'avaliações com nota a lançar')}
                    </small>
                  )}
                </span>
                <span className={styles.carga}>{disciplina.cargaHoraria} h</span>
              </li>
            )
          })}
        </ul>
      </div>

      <div className={styles.acoes}>
        <Button size="sm" variant="primary" icon="edit" onClick={() => onLancarNotas(turma)}>
          Lançar notas
        </Button>
        <Button size="sm" icon="plus" onClick={() => onCriarAvaliacao(turma)}>
          Criar avaliação
        </Button>
        <Button size="sm" icon="users" onClick={() => onVerAlunos(turma)}>
          Ver alunos
        </Button>
      </div>
    </article>
  )
}
