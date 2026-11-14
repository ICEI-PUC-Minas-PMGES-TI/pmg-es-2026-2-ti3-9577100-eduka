import { DIAS_SEMANA, formatarHora, horaCurta } from '../../../../types/academico/horario'
import type { TurmaDoProfessor } from '../../../../types/academico/turmaProfessor'
import styles from './GradeSemanal.module.css'

type Aula = { turma: string; disciplina: string }

/** Aulas do professor por hora (linha) e dia (coluna), com as turmas ativas dele. */
function montarGrade(turmas: TurmaDoProfessor[]) {
  const aulas = new Map<string, Aula[]>()
  const horas = new Set<string>()
  let temSabado = false
  for (const turma of turmas.filter((t) => t.ativa)) {
    for (const disciplina of turma.disciplinas) {
      for (const horario of disciplina.horarios) {
        const hora = horaCurta(horario.horaInicio)
        horas.add(hora)
        if (horario.diaSemana === 'SABADO') temSabado = true
        const chave = `${hora}|${horario.diaSemana}`
        aulas.set(chave, [...(aulas.get(chave) ?? []), { turma: turma.nome, disciplina: disciplina.nome }])
      }
    }
  }
  const dias = DIAS_SEMANA.filter((d) => d.dia !== 'SABADO' || temSabado)
  return { horas: [...horas].sort(), dias, aulas }
}

/** Meu horário da semana (IU13): a grade com as aulas do professor por dia e horário. */
export function GradeSemanal({ turmas }: { turmas: TurmaDoProfessor[] }) {
  const { horas, dias, aulas } = montarGrade(turmas)

  return (
    <section className={styles.cartao} aria-labelledby="titulo-horario">
      <h2 id="titulo-horario" className={styles.titulo}>
        Meu horário da semana
      </h2>
      {horas.length === 0 ? (
        <p className={styles.vazio}>
          A secretaria ainda não definiu o horário das suas aulas. Quando definir, ele aparece aqui.
        </p>
      ) : (
        <div className={styles.rolagem}>
          <table className={styles.grade}>
            <thead>
              <tr>
                <th scope="col">
                  <span className={styles.somenteLeitor}>Horário</span>
                </th>
                {dias.map((d) => (
                  <th key={d.dia} scope="col">
                    {d.longo}
                  </th>
                ))}
              </tr>
            </thead>
            <tbody>
              {horas.map((hora) => (
                <tr key={hora}>
                  <th scope="row" className={styles.hora}>
                    {formatarHora(hora)}
                  </th>
                  {dias.map((d) => (
                    <td key={d.dia} data-rotulo={d.longo}>
                      {(aulas.get(`${hora}|${d.dia}`) ?? []).map((aula) => (
                        <span key={`${aula.turma}|${aula.disciplina}`} className={styles.aula}>
                          <strong>{aula.turma}</strong>
                          <small>{aula.disciplina}</small>
                        </span>
                      ))}
                    </td>
                  ))}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </section>
  )
}
