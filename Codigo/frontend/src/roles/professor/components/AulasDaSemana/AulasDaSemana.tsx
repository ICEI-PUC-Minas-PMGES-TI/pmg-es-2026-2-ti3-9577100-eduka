import { Button } from '../../../../components/ui/Button/Button'
import { Icon } from '../../../../components/ui/Icon/Icon'
import type { AulaDaSemana } from '../../../../types/dashboard/dashboard'
import { DIAS_SEMANA, formatarHora } from '../../../../types/academico/horario'
import styles from './AulasDaSemana.module.css'

type AulasDaSemanaProps = {
  aulas: AulaDaSemana[]
  /** "AAAA-MM-DD" no fuso da escola. */
  hoje: string
  onFazerChamada: (aula: AulaDaSemana) => void
}

/** "2026-11-09" -> "09/11", sem Date para o fuso não mudar o dia. */
function diaMes(data: string): string {
  const [, mes, dia] = data.split('-')
  return `${dia}/${mes}`
}

/** Aula que já aconteceu (ou é hoje) e ainda não tem chamada. */
export function chamadaPendente(aula: AulaDaSemana, hoje: string): boolean {
  return !aula.chamadaFeita && aula.data <= hoje
}

/**
 * As aulas do professor nesta semana, uma coluna por dia (IU12): o que já
 * teve chamada, o que falta registrar e o que ainda vem.
 */
export function AulasDaSemana({ aulas, hoje, onFazerChamada }: AulasDaSemanaProps) {
  const temSabado = aulas.some((a) => a.diaSemana === 'SABADO')
  const dias = DIAS_SEMANA.filter((d) => d.dia !== 'SABADO' || temSabado)

  return (
    <div className={styles.semana}>
      {dias.map((dia) => {
        const doDia = aulas.filter((a) => a.diaSemana === dia.dia)
        const data = doDia[0]?.data
        const ehHoje = data === hoje
        return (
          <section
            key={dia.dia}
            className={`${styles.dia} ${ehHoje ? styles.hoje : ''}`}
            aria-label={ehHoje ? `${dia.longo}, hoje` : dia.longo}
          >
            <h3 className={styles.cabecalho}>
              {dia.longo}
              {data && <span className={styles.data}>{ehHoje ? 'Hoje' : diaMes(data)}</span>}
            </h3>
            {doDia.length === 0 ? (
              <p className={styles.vazio}>Sem aulas</p>
            ) : (
              <ul className={styles.aulas}>
                {doDia.map((aula) => (
                  <li key={`${aula.turmaId}|${aula.disciplinaId}|${aula.horaInicio}`} className={styles.aula}>
                    <span className={styles.hora}>
                      {formatarHora(aula.horaInicio)} – {formatarHora(aula.horaFim)}
                    </span>
                    <strong className={styles.turma}>{aula.turmaNome}</strong>
                    <span className={styles.disciplina}>{aula.disciplinaNome}</span>
                    {aula.chamadaFeita && (
                      <span className={styles.feita}>
                        <Icon name="check" size={13} /> Chamada feita
                      </span>
                    )}
                    {chamadaPendente(aula, hoje) && (
                      <Button
                        size="sm"
                        variant={ehHoje ? 'primary' : 'outline'}
                        icon="calendar"
                        className={styles.botao}
                        aria-label={`Fazer a chamada de ${aula.turmaNome} · ${aula.disciplinaNome}, ${dia.longo} às ${formatarHora(aula.horaInicio)}`}
                        onClick={() => onFazerChamada(aula)}
                      >
                        Fazer chamada
                      </Button>
                    )}
                  </li>
                ))}
              </ul>
            )}
          </section>
        )
      })}
    </div>
  )
}
