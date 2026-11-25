import { useNavigate } from 'react-router'
import { Modal } from '../../../../../components/modal/Modal/Modal'
import { Button } from '../../../../../components/ui/Button/Button'
import { Icon } from '../../../../../components/ui/Icon/Icon'
import { useDetalhe } from '../../../../../hooks/useDetalhe'
import { useAreaMaster } from '../../../../../routes/areas'
import { paths } from '../../../../../routes/paths'
import { listarAlocacoesDoProfessor } from '../../../../../services/academico/alocacaoService'
import { ROTULOS_TURNO, type Alocacao } from '../../../../../types/academico/alocacao'
import type { Professor } from '../../../../../types/pessoas/professor'
import { plural } from '../../../../../utils/formatters'
import styles from './AlocacoesProfessorModal.module.css'

export type VisaoAlocacoes = 'disciplinas' | 'turmas'

type AlocacoesProfessorModalProps = {
  professor: Professor
  visao: VisaoAlocacoes
  onFechar: () => void
}

type TurmaAgrupada = { id: number; nome: string; periodo: string; turno: Alocacao['turno']; disciplinas: string[] }
type DisciplinaAgrupada = { id: number; nome: string; turmas: { id: number; rotulo: string }[] }

/** Período mais recente primeiro ("2026/2" antes de "2026/1"); no mesmo período, pelo nome da turma. */
function porPeriodoETurma(a: { periodo: string; nome: string }, b: { periodo: string; nome: string }): number {
  return b.periodo.localeCompare(a.periodo, 'pt-BR', { numeric: true }) || a.nome.localeCompare(b.nome, 'pt-BR')
}

function agruparPorTurma(alocacoes: Alocacao[]): TurmaAgrupada[] {
  const turmas = new Map<number, TurmaAgrupada>()
  for (const a of alocacoes) {
    const turma = turmas.get(a.turmaId) ?? {
      id: a.turmaId,
      nome: a.turmaNome,
      periodo: a.periodoLetivoNome,
      turno: a.turno,
      disciplinas: [],
    }
    turma.disciplinas.push(a.disciplinaNome)
    turmas.set(a.turmaId, turma)
  }
  return [...turmas.values()]
    .map((t) => ({ ...t, disciplinas: [...t.disciplinas].sort((x, y) => x.localeCompare(y, 'pt-BR')) }))
    .sort(porPeriodoETurma)
}

/** Turmas já ordenadas (período mais recente primeiro) separadas por período, na mesma ordem. */
function porPeriodo(turmas: TurmaAgrupada[]): [string, TurmaAgrupada[]][] {
  const grupos = new Map<string, TurmaAgrupada[]>()
  for (const t of turmas) grupos.set(t.periodo, [...(grupos.get(t.periodo) ?? []), t])
  return [...grupos]
}

function agruparPorDisciplina(alocacoes: Alocacao[]): DisciplinaAgrupada[] {
  const disciplinas = new Map<number, DisciplinaAgrupada & { ordem: { periodo: string; nome: string; id: number }[] }>()
  for (const a of alocacoes) {
    const disciplina = disciplinas.get(a.disciplinaId) ?? { id: a.disciplinaId, nome: a.disciplinaNome, turmas: [], ordem: [] }
    disciplina.ordem.push({ periodo: a.periodoLetivoNome, nome: a.turmaNome, id: a.turmaId })
    disciplinas.set(a.disciplinaId, disciplina)
  }
  return [...disciplinas.values()]
    .map((d) => ({
      id: d.id,
      nome: d.nome,
      turmas: [...d.ordem].sort(porPeriodoETurma).map((t) => ({ id: t.id, rotulo: `${t.nome} · ${t.periodo}` })),
    }))
    .sort((a, b) => a.nome.localeCompare(b.nome, 'pt-BR'))
}

/**
 * Onde o professor dá aula, a partir das alocações (GET /professores/{id}/alocacoes):
 * por disciplina (em quais turmas leciona cada uma) ou por turma (quais
 * disciplinas leciona em cada uma). Busca ao abrir, então mostra a situação de agora.
 */
export function AlocacoesProfessorModal({ professor, visao, onFechar }: AlocacoesProfessorModalProps) {
  const navigate = useNavigate()
  const areaMaster = useAreaMaster()
  const { dados: alocacoes, carregando, erro } = useDetalhe(listarAlocacoesDoProfessor, professor.id)
  const turmas = alocacoes ? agruparPorTurma(alocacoes) : []
  const disciplinas = alocacoes ? agruparPorDisciplina(alocacoes) : []
  const titulo = visao === 'disciplinas' ? `Disciplinas de ${professor.nome}` : `Turmas de ${professor.nome}`
  const resumo = alocacoes
    ? `${disciplinas.length} ${plural(disciplinas.length, 'disciplina', 'disciplinas')} em ${turmas.length} ${plural(turmas.length, 'turma', 'turmas')}`
    : undefined

  function irParaAlocacoes() {
    onFechar()
    navigate(areaMaster ? paths.master.alocacoes : paths.secretaria.alocacoes)
  }

  return (
    <Modal
      titulo={titulo}
      subtitulo={resumo}
      onFechar={onFechar}
      rodape={
        <>
          <Button icon="settings" onClick={irParaAlocacoes}>
            Gerenciar alocações
          </Button>
          <Button variant="primary" onClick={onFechar}>
            Fechar
          </Button>
        </>
      }
    >
      {carregando && (
        <p className={styles.estado} role="status">
          Carregando as alocações…
        </p>
      )}

      {!carregando && erro && (
        <p className={styles.estado} role="alert">
          {erro}
        </p>
      )}

      {!carregando && !erro && alocacoes && alocacoes.length === 0 && (
        <div className={styles.estado}>
          <Icon name="graduationCap" size={22} />
          <p>
            {professor.nome} ainda não está alocado em nenhuma turma. Em <strong>Gerenciar alocações</strong>, escolha
            a turma e a disciplina.
          </p>
        </div>
      )}

      {!carregando && !erro && alocacoes && alocacoes.length > 0 && visao === 'disciplinas' && (
        <ul className={styles.lista} aria-label="Disciplinas do professor">
          {disciplinas.map((d) => (
            <li key={d.id} className={styles.item}>
              <div className={styles.itemTopo}>
                <span className={styles.itemIcone} aria-hidden="true">
                  <Icon name="book" size={16} />
                </span>
                <div className={styles.itemTitulo}>
                  <strong>{d.nome}</strong>
                  <span>
                    Em {d.turmas.length} {plural(d.turmas.length, 'turma', 'turmas')}
                  </span>
                </div>
              </div>
              <ul className={styles.chips} aria-label={`Turmas de ${d.nome}`}>
                {d.turmas.map((t) => (
                  <li key={t.id}>{t.rotulo}</li>
                ))}
              </ul>
            </li>
          ))}
        </ul>
      )}

      {!carregando && !erro && alocacoes && alocacoes.length > 0 && visao === 'turmas' && (
        <div className={styles.periodos}>
          {porPeriodo(turmas).map(([periodo, doPeriodo]) => (
            <section key={periodo} className={styles.periodo} aria-label={`Período ${periodo}`}>
              <h3 className={styles.periodoTitulo}>
                Período {periodo}
                <span>
                  {doPeriodo.length} {plural(doPeriodo.length, 'turma', 'turmas')}
                </span>
              </h3>
              <ul className={styles.lista} aria-label={`Turmas do professor em ${periodo}`}>
                {doPeriodo.map((t) => (
                  <li key={t.id} className={styles.item}>
                    <div className={styles.itemTopo}>
                      <span className={styles.itemIcone} aria-hidden="true">
                        <Icon name="users" size={16} />
                      </span>
                      <div className={styles.itemTitulo}>
                        <strong>{t.nome}</strong>
                        <span>{ROTULOS_TURNO[t.turno]}</span>
                      </div>
                    </div>
                    <ul className={styles.chips} aria-label={`Disciplinas em ${t.nome}`}>
                      {t.disciplinas.map((nome) => (
                        <li key={nome}>{nome}</li>
                      ))}
                    </ul>
                  </li>
                ))}
              </ul>
            </section>
          ))}
        </div>
      )}
    </Modal>
  )
}
