import type { ReactNode } from 'react'
import { Button } from '../../../../components/Button/Button'
import type { AlunoResumo } from '../../../../types/aluno'
import { iniciais } from '../../../../utils/formatters'
import styles from './AlunosTable.module.css'

const SEM_DADO = '—'

type AlunosTableProps = {
  alunos: AlunoResumo[]
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
              <th scope="col">Gênero</th>
              <th scope="col" className={styles.colAcoes}>
                Ações
              </th>
            </tr>
          </thead>
          <tbody>
            {!carregando &&
              !erro &&
              alunos.map((aluno) => (
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
                  <td className={styles.secundario}>{aluno.genero ?? SEM_DADO}</td>
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
              ))}
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
