import { Button } from '../../../../components/Button/Button'
import { Icon } from '../../../../components/Icon/Icon'
import { ROTULOS_TURNO, type DisciplinaDaTurma, type Turma } from '../../../../types/alocacao'
import type { EscolhasDaTurma } from '../../hooks/useAlocacoes'
import { SeletorProfessor, type OpcaoProfessor } from '../SeletorProfessor/SeletorProfessor'
import { descreverHorarios, type HorarioAula } from '../../../../types/horario'
import styles from './GradeAlocacao.module.css'

type GradeAlocacaoProps = {
  turma: Turma
  /** Disciplinas da grade, já na ordem em que aparecem. */
  disciplinas: DisciplinaDaTurma[]
  /** Professores que podem ser escolhidos em qualquer disciplina (os ativos). */
  professores: OpcaoProfessor[]
  /** Professor gravado em cada disciplina; ausente é "sem professor". */
  gravadas: Map<number, OpcaoProfessor>
  /** O que está marcado na tela agora, gravado ou não. */
  escolhas: EscolhasDaTurma
  /** Quantas disciplinas estão com escolha diferente da gravada. */
  mudancas: number
  salvando: boolean
  /** Horário das aulas da turma (IU13), de todas as disciplinas. */
  horarios: HorarioAula[]
  onEditarHorario: (disciplina: DisciplinaDaTurma) => void
  onEscolher: (disciplinaId: number, professorId: string) => void
  onDesfazer: () => void
  onSalvar: () => void
}

/**
 * Painel da turma aberta: uma linha por disciplina, com o professor dela ao
 * lado. Nada é gravado até "Salvar alocação".
 */
export function GradeAlocacao({
  turma,
  disciplinas,
  professores,
  gravadas,
  escolhas,
  mudancas,
  salvando,
  horarios,
  onEditarHorario,
  onEscolher,
  onDesfazer,
  onSalvar,
}: GradeAlocacaoProps) {
  const comProfessor = disciplinas.filter((d) => escolhas[d.id]).length
  const completa = disciplinas.length > 0 && comProfessor === disciplinas.length
  const percentual = disciplinas.length > 0 ? (comProfessor / disciplinas.length) * 100 : 0

  return (
    <section className={styles.painel} aria-label={`Professores da turma ${turma.nome}`}>
      <header className={styles.cabecalho}>
        <div className={styles.identidade}>
          <h2 className={styles.turma}>{turma.nome}</h2>
          <p className={styles.contexto}>
            {[turma.cursoNome, ROTULOS_TURNO[turma.turno], turma.periodoLetivoNome].filter(Boolean).join(' · ')}
          </p>
        </div>
        {disciplinas.length > 0 && (
          <div className={styles.progresso}>
            <span className={`${styles.contagem} ${completa ? styles.completa : ''}`}>
              {completa && <Icon name="check" size={14} strokeWidth={3} />}
              <strong>{comProfessor}</strong> de {disciplinas.length} disciplinas com professor
            </span>
            <span className={styles.trilho} aria-hidden="true">
              <span
                className={`${styles.barra} ${completa ? styles.barraCompleta : ''}`}
                style={{ width: `${percentual}%` }}
              />
            </span>
          </div>
        )}
      </header>

      {disciplinas.length === 0 && (
        <p className={styles.vazio}>Esta turma ainda não tem disciplinas na grade.</p>
      )}

      <ul className={styles.disciplinas}>
        {disciplinas.map((disciplina) => {
          const escolhido = escolhas[disciplina.id] ?? ''
          const gravada = gravadas.get(disciplina.id)
          // Professor inativado depois de alocado continua na lista desta
          // disciplina, para dar para ver quem está nela e trocar.
          const opcoes =
            gravada && !professores.some((p) => p.id === gravada.id) ? [...professores, gravada] : professores
          const alterada = escolhido !== (gravada?.id ?? '')

          return (
            <li key={disciplina.id} className={styles.disciplina}>
              <span
                className={`${styles.marcador} ${escolhido ? styles.marcadorOk : ''}`}
                aria-hidden="true"
              >
                <Icon name={escolhido ? 'check' : 'book'} size={16} strokeWidth={escolhido ? 3 : 2} />
              </span>
              <div className={styles.info}>
                <h3 className={styles.nome}>{disciplina.nome}</h3>
                <span className={styles.detalhes}>
                  {[disciplina.codigo, disciplina.cargaHoraria != null ? `${disciplina.cargaHoraria}h` : null]
                    .filter(Boolean)
                    .join(' · ')}
                  {alterada && <span className={styles.alterada}>Alterada</span>}
                </span>
                <span className={styles.horario}>
                  {descreverHorarios(horarios.filter((h) => h.disciplinaId === disciplina.id)) ?? 'Sem horário'}
                  <button
                    type="button"
                    className={styles.editarHorario}
                    aria-label={`Horário de ${disciplina.nome}`}
                    onClick={() => onEditarHorario(disciplina)}
                  >
                    Definir horário
                  </button>
                </span>
              </div>
              <div className={styles.seletor}>
                <SeletorProfessor
                  professores={opcoes}
                  valor={escolhido}
                  onChange={(professorId) => onEscolher(disciplina.id, professorId)}
                  disabled={salvando}
                  aria-label={`Professor de ${disciplina.nome}`}
                />
              </div>
            </li>
          )
        })}
      </ul>

      {disciplinas.length > 0 && (
        <footer className={styles.rodape}>
          <span className={`${styles.situacao} ${mudancas > 0 ? styles.pendente : ''}`} aria-live="polite">
            {mudancas === 0 && 'Tudo salvo'}
            {mudancas === 1 && '1 alteração não salva'}
            {mudancas > 1 && `${mudancas} alterações não salvas`}
          </span>
          <div className={styles.acoes}>
            <Button disabled={mudancas === 0 || salvando} onClick={onDesfazer}>
              Desfazer
            </Button>
            <Button variant="primary" icon="check" disabled={mudancas === 0 || salvando} onClick={onSalvar}>
              {salvando ? 'Salvando…' : 'Salvar alocação'}
            </Button>
          </div>
        </footer>
      )}
    </section>
  )
}
