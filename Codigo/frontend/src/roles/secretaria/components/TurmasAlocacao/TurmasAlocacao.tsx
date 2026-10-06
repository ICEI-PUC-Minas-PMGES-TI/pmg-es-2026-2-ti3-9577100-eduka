import { Icon } from '../../../../components/Icon/Icon'
import { ROTULOS_TURNO, type Turma } from '../../../../types/alocacao'
import styles from './TurmasAlocacao.module.css'

type TurmasAlocacaoProps = {
  /** Já na ordem em que aparecem; a lista é agrupada por período letivo. */
  turmas: Turma[]
  turmaSelecionadaId: number | null
  /** Quantas disciplinas de cada turma já têm professor gravado. */
  alocadasPorTurma: Map<number, number>
  /** Turmas com escolhas ainda não salvas. */
  turmasComRascunho: Set<number>
  onSelecionar: (turma: Turma) => void
}

function agruparPorPeriodo(turmas: Turma[]): { periodo: string; turmas: Turma[] }[] {
  const grupos = new Map<string, Turma[]>()
  for (const turma of turmas) {
    grupos.set(turma.periodoLetivoNome, [...(grupos.get(turma.periodoLetivoNome) ?? []), turma])
  }
  return [...grupos]
    .map(([periodo, doPeriodo]) => ({ periodo, turmas: doPeriodo }))
    .sort((a, b) => b.periodo.localeCompare(a.periodo, 'pt-BR', { numeric: true }))
}

/** Cartões das turmas, com o quanto da grade de cada uma já tem professor. */
export function TurmasAlocacao({
  turmas,
  turmaSelecionadaId,
  alocadasPorTurma,
  turmasComRascunho,
  onSelecionar,
}: TurmasAlocacaoProps) {
  return (
    <div className={styles.grupos}>
      {agruparPorPeriodo(turmas).map((grupo) => (
        <section key={grupo.periodo} className={styles.grupo} aria-label={`Turmas de ${grupo.periodo}`}>
          <h2 className={styles.titulo}>
            Turmas de {grupo.periodo}
            <span className={styles.quantidade}>{grupo.turmas.length}</span>
          </h2>
          {grupo.turmas.map((turma) => {
            const total = turma.disciplinas.length
            const alocadas = alocadasPorTurma.get(turma.id) ?? 0
            const completa = total > 0 && alocadas === total
            const selecionada = turma.id === turmaSelecionadaId
            return (
              <button
                key={turma.id}
                type="button"
                className={`${styles.cartao} ${selecionada ? styles.selecionado : ''}`}
                aria-pressed={selecionada}
                onClick={() => onSelecionar(turma)}
              >
                <span className={styles.linha}>
                  <span className={styles.info}>
                    <strong className={styles.nome}>
                      {turma.nome}
                      {turmasComRascunho.has(turma.id) && (
                        <span className={styles.rascunho} title="Tem alterações não salvas" />
                      )}
                    </strong>
                    <span className={styles.detalhes}>
                      {[turma.cursoNome, ROTULOS_TURNO[turma.turno]].filter(Boolean).join(' · ')}
                    </span>
                  </span>
                  <span className={`${styles.contador} ${completa ? styles.completa : ''}`}>
                    {completa && <Icon name="check" size={13} strokeWidth={3} />}
                    <strong>{alocadas}</strong>/{total}
                  </span>
                </span>
                <span className={styles.trilho} aria-hidden="true">
                  <span
                    className={`${styles.barra} ${completa ? styles.barraCompleta : ''}`}
                    style={{ width: `${total > 0 ? (alocadas / total) * 100 : 0}%` }}
                  />
                </span>
                <span className={styles.oculto}>
                  {alocadas} de {total} disciplinas com professor
                </span>
              </button>
            )
          })}
        </section>
      ))}
    </div>
  )
}
