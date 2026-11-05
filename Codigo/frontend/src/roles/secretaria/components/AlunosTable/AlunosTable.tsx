import type { ReactNode } from 'react'
import { Badge } from '../../../../components/Badge/Badge'
import { Button } from '../../../../components/Button/Button'
import { Paginacao } from '../../../../components/Paginacao/Paginacao'
import { usePaginacao } from '../../../../hooks/usePaginacao'
import type { AlunoResumo } from '../../../../types/aluno'
import { iniciais } from '../../../../utils/formatters'
import { Dica } from '../../../../components/Dica/Dica'
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
  /** Quando muda (busca, filtro), a tabela volta para a primeira página. */
  reiniciarCom?: string
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
  reiniciarCom,
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
  const { paginacao, topo } = usePaginacao<AlunoResumo, HTMLElement>(alunos, { reiniciarCom })
  return (
    <>
      <section ref={topo} className={styles.card} aria-label="Alunos cadastrados">
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
                paginacao.itensDaPagina.map((aluno) => {
                  const situacao = situacoes.get(aluno.id)
                  const badge = situacao && BADGE_SITUACAO[situacao.tipo]
                  return (
                    <tr key={aluno.id}>
                      <td className={styles.colPrincipal}>
                        <div className={styles.aluno}>
                          <span className={styles.avatar} aria-hidden="true">
                            {iniciais(aluno.nome)}
                          </span>
                          <strong className={styles.nome}>{aluno.nome}</strong>
                        </div>
                      </td>
                      <td data-rotulo="Matrícula">{aluno.matricula}</td>
                      <td data-rotulo="Turma" className={situacao?.tipo === 'matriculado' ? undefined : styles.secundario}>
                        {situacao?.turma ?? SEM_DADO}
                      </td>
                      <td data-rotulo="Situação">{badge ? <Badge tone={badge.tom}>{badge.rotulo}</Badge> : SEM_DADO}</td>
                      <td className={styles.colAcoes}>
                        <div className={styles.acoes}>
                          <Button size="sm" icon="eye" onClick={() => onVer(aluno)}>
                            Ver
                          </Button>
                          <Button size="sm" icon="edit" onClick={() => onEditar(aluno)}>
                            Editar
                          </Button>
                          <Dica texto="Envia uma nova senha provisória para o e-mail do aluno">
                            <Button
                              size="sm"
                              icon="mail"
                              disabled={reenviandoId === aluno.id}
                              onClick={() => onReenviarAcesso(aluno)}
                            >
                              Reenviar acesso
                            </Button>
                          </Dica>
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
      {!carregando && !erro && <Paginacao paginacao={paginacao} rotulo="Páginas dos alunos" />}
    </>
  )
}

function Mensagem({ children }: { children: ReactNode }) {
  return <div className={styles.mensagem}>{children}</div>
}
