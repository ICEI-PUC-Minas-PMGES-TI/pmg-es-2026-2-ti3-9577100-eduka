import type { TurmaAcademica } from '../../../../types/academico'
import { ROTULOS_TURNO } from '../../../../types/alocacao'
import styles from './TurmasMatricula.module.css'

type TurmasMatriculaProps = {
  /** Já na ordem em que aparecem; a lista é agrupada por período letivo. */
  turmas: TurmaAcademica[]
  turmaSelecionadaId: number | null
  /** Matrículas ATIVAS de cada turma: são elas que ocupam vaga. */
  ativasPorTurma: Map<number, number>
  onSelecionar: (turma: TurmaAcademica) => void
}

function agruparPorPeriodo(turmas: TurmaAcademica[]): { periodo: string; turmas: TurmaAcademica[] }[] {
  const grupos = new Map<string, TurmaAcademica[]>()
  for (const turma of turmas) {
    grupos.set(turma.periodoLetivoNome, [...(grupos.get(turma.periodoLetivoNome) ?? []), turma])
  }
  return [...grupos]
    .map(([periodo, doPeriodo]) => ({ periodo, turmas: doPeriodo }))
    .sort((a, b) => b.periodo.localeCompare(a.periodo, 'pt-BR', { numeric: true }))
}

/** Cartões das turmas, com quantas vagas cada uma já tem ocupadas. */
export function TurmasMatricula({ turmas, turmaSelecionadaId, ativasPorTurma, onSelecionar }: TurmasMatriculaProps) {
  return (
    <div className={styles.grupos}>
      {agruparPorPeriodo(turmas).map((grupo) => (
        <section key={grupo.periodo} className={styles.grupo} aria-label={`Turmas de ${grupo.periodo}`}>
          <h2 className={styles.titulo}>
            Turmas de {grupo.periodo}
            <span className={styles.quantidade}>{grupo.turmas.length}</span>
          </h2>
          <div className={styles.cartoes}>
            {grupo.turmas.map((turma) => {
              const ativas = ativasPorTurma.get(turma.id) ?? 0
              const cheia = ativas >= turma.vagas
              const selecionada = turma.id === turmaSelecionadaId
              const classes = [
                styles.cartao,
                selecionada && styles.selecionado,
                !turma.ativa && styles.inativa,
              ]
                .filter(Boolean)
                .join(' ')
              return (
                <button
                  key={turma.id}
                  type="button"
                  className={classes}
                  aria-pressed={selecionada}
                  onClick={() => onSelecionar(turma)}
                >
                  <span className={styles.linha}>
                    <span className={styles.info}>
                      <strong className={styles.nome}>{turma.nome}</strong>
                      <span className={styles.detalhes}>
                        {[turma.cursoNome, ROTULOS_TURNO[turma.turno], !turma.ativa && 'Inativa']
                          .filter(Boolean)
                          .join(' · ')}
                      </span>
                    </span>
                    <span className={`${styles.contador} ${cheia ? styles.cheia : ''}`}>
                      <strong>{ativas}</strong>/{turma.vagas}
                    </span>
                  </span>
                  <span className={styles.trilho} aria-hidden="true">
                    <span
                      className={`${styles.barra} ${cheia ? styles.barraCheia : ''}`}
                      style={{ width: `${turma.vagas > 0 ? Math.min(ativas / turma.vagas, 1) * 100 : 100}%` }}
                    />
                  </span>
                  <span className={styles.oculto}>
                    {ativas} de {turma.vagas} vagas ocupadas
                  </span>
                </button>
              )
            })}
          </div>
        </section>
      ))}
    </div>
  )
}
