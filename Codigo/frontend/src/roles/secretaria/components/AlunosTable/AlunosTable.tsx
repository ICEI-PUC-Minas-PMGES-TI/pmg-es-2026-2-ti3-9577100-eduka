import type { ReactNode } from 'react'
import { Badge } from '../../../../components/Badge/Badge'
import { Button } from '../../../../components/Button/Button'
import type { AlunoResumo } from '../../../../types/aluno'
import { iniciais } from '../../../../utils/formatters'
import styles from './AlunosTable.module.css'

const SEM_DADO = '—'

/**
 * Situação do aluno na escola, tirada das matrículas: com matrícula ativa,
 * só com trancada, ou sem nenhuma das duas.
 */
export type SituacaoAluno = {
  tipo: 'matriculado' | 'trancado' | 'semTurma'
  /** Turma da matrícula ativa (ou da trancada). */
  turma: string | null
}

const BADGE_SITUACAO: Record<SituacaoAluno['tipo'], { rotulo: string; tom: 'success' | 'neutral' | 'danger' }> = {
  matriculado: { rotulo: 'Matriculado', tom: 'success' },
  trancado: { rotulo: 'Trancado', tom: 'neutral' },
  semTurma: { rotulo: 'Sem turma', tom: 'danger' },
}

type AlunosTableProps = {
  alunos: AlunoResumo[]
  /** Situação de cada aluno, pelo id; sem entrada, as colunas ficam com traço. */
  situacoes: Map<string, SituacaoAluno>
  carregando: boolean
  erro: string | null
  /** Mensagem quando a lista filtrada fica vazia. */
  mensagemVazia: string
  /** Aluno com reenvio de acesso em andamento. */
  reenviandoId: string | null
  onTentarNovamente: () => void
  onVer: (aluno: AlunoResumo) => void
  onEditar: (aluno: AlunoResumo) => void
  onReenviarAcesso: (aluno: AlunoResumo) => void
}

/** Tabela de alunos num card de vidro, com os estados de carregando, erro e vazio. */
export function AlunosTable({
  alunos,
  situacoes,
  carregando,
  erro,
  mensagemVazia,
  reenviandoId,
  onTentarNovamente,
  onVer,
  onEditar,
  onReenviarAcesso,
}: AlunosTableProps) {
  return (
    <section className={styles.card} aria-label="Alunos cadastrados">
      <div className={styles.rolagem}>
        <table className={styles.tabela}>
          <thead>
            <tr>
              <th scope="col">Aluno</th>
              <th scope="col">Matrícula</th>
              <th scope="col">Turma</th>
              <th scope="col">Situação</th>
              <th scope="col" className={styles.colAcoes}>
                Ações
              </th>
            </tr>
          </thead>
          <tbody>
            {!carregando &&
              !erro &&
              alunos.map((aluno) => {
                const situacao = situacoes.get(aluno.id)
                const badge = situacao && BADGE_SITUACAO[situacao.tipo]
                return (
                  <tr key={aluno.id}>
                    <td>
                      <div className={styles.aluno}>
                        <span className={styles.avatar} aria-hidden="true">
                          {iniciais(aluno.nome)}
                        </span>
                        <strong className={styles.nome}>{aluno.nome}</strong>
                      </div>
                    </td>
                    <td>{aluno.matricula}</td>
                    <td className={situacao?.tipo === 'matriculado' ? undefined : styles.secundario}>
                      {situacao?.turma ?? SEM_DADO}
                    </td>
                    <td>{badge ? <Badge tone={badge.tom}>{badge.rotulo}</Badge> : SEM_DADO}</td>
                    <td className={styles.colAcoes}>
                      <div className={styles.acoes}>
                        <Button size="sm" icon="eye" onClick={() => onVer(aluno)}>
                          Ver
                        </Button>
                        <Button size="sm" icon="edit" onClick={() => onEditar(aluno)}>
                          Editar
                        </Button>
                        <Button
                          size="sm"
                          icon="mail"
                          disabled={reenviandoId === aluno.id}
                          title="Envia uma nova senha provisória para o e-mail do aluno"
                          onClick={() => onReenviarAcesso(aluno)}
                        >
                          Reenviar acesso
                        </Button>
                      </div>
                    </td>
                  </tr>
                )
              })}
          </tbody>
        </table>
      </div>

      {carregando && <Mensagem>Carregando alunos…</Mensagem>}
      {!carregando && erro && (
        <Mensagem>
          <span>{erro}</span>
          <Button size="sm" onClick={onTentarNovamente}>
            Tentar novamente
          </Button>
        </Mensagem>
      )}
      {!carregando && !erro && alunos.length === 0 && <Mensagem>{mensagemVazia}</Mensagem>}
    </section>
  )
}

function Mensagem({ children }: { children: ReactNode }) {
  return <div className={styles.mensagem}>{children}</div>
}
