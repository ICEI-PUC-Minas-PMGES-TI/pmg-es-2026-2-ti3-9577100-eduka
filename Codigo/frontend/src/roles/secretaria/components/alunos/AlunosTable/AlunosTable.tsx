import type { ReactNode, Ref } from 'react'
import { Badge } from '../../../../../components/ui/Badge/Badge'
import { Button } from '../../../../../components/ui/Button/Button'
import { Dica } from '../../../../../components/ui/Dica/Dica'
import { Paginacao } from '../../../../../components/ui/Paginacao/Paginacao'
import type { EstadoPaginacao } from '../../../../../hooks/usePaginacao'
import type { AlunoDaLista, SituacaoNaEscola } from '../../../../../types/pessoas/aluno'
import { iniciais } from '../../../../../utils/formatters'
import styles from './AlunosTable.module.css'

const SEM_DADO = '—'

const BADGE_SITUACAO: Record<SituacaoNaEscola, { rotulo: string; tom: 'success' | 'neutral' | 'danger' }> = {
  MATRICULADO: { rotulo: 'Matriculado', tom: 'success' },
  TRANCADO: { rotulo: 'Trancado', tom: 'neutral' },
  SEM_TURMA: { rotulo: 'Sem turma', tom: 'danger' },
  INATIVO: { rotulo: 'Inativo', tom: 'neutral' },
  ANONIMIZADO: { rotulo: 'Anonimizado', tom: 'neutral' },
}

type AlunosTableProps = {
  /** A página de alunos; null até a primeira resposta do backend. */
  paginacao: EstadoPaginacao<AlunoDaLista> | null
  /** Recebe o topo da tabela, para onde a troca de página rola. */
  topo: Ref<HTMLElement>
  carregando: boolean
  /** Outra página, busca ou filtro a caminho: a lista atual fica e a navegação espera. */
  atualizando: boolean
  erro: string | null
  /** Mensagem quando a página fica vazia. */
  mensagemVazia: string
  /** Aluno com reenvio de acesso em andamento. */
  reenviandoId: string | null
  /** Aluno sendo inativado ou reativado. */
  alternandoId?: string | null
  onTentarNovamente: () => void
  onVer: (aluno: AlunoDaLista) => void
  /** Sem ela, a linha não mostra Editar (perfil só de consulta, como o FINANCEIRO). */
  onEditar?: (aluno: AlunoDaLista) => void
  /** Sem ela, a linha não mostra Reenviar acesso. */
  onReenviarAcesso?: (aluno: AlunoDaLista) => void
  /** Sem ela, a linha não mostra Inativar nem Reativar. */
  onAlternarSituacao?: (aluno: AlunoDaLista) => void
}

/** Tabela de alunos num card de vidro, com os estados de carregando, erro e vazio. */
export function AlunosTable({
  paginacao,
  topo,
  carregando,
  atualizando,
  erro,
  mensagemVazia,
  reenviandoId,
  alternandoId = null,
  onTentarNovamente,
  onVer,
  onEditar,
  onReenviarAcesso,
  onAlternarSituacao,
}: AlunosTableProps) {
  const alunos = !erro && paginacao ? paginacao.itensDaPagina : []
  return (
    <>
      <section ref={topo} className={styles.card} aria-label="Alunos cadastrados" aria-busy={atualizando}>
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
              {alunos.map((aluno) => {
                const badge = BADGE_SITUACAO[aluno.situacao]
                // Sem dado pessoal, não há o que editar nem acesso para reenviar.
                const anonimizado = aluno.situacao === 'ANONIMIZADO'
                // Inativo não entra no portal: reenviar o acesso não adianta até reativar.
                const inativo = aluno.situacao === 'INATIVO'
                return (
                  <tr key={aluno.id} className={inativo ? styles.inativo : undefined}>
                    <td className={styles.colPrincipal}>
                      <div className={styles.aluno}>
                        <span className={styles.avatar} aria-hidden="true">
                          {iniciais(aluno.nome)}
                        </span>
                        <strong className={styles.nome}>{aluno.nome}</strong>
                      </div>
                    </td>
                    <td data-rotulo="Matrícula">{aluno.matricula}</td>
                    <td data-rotulo="Turma" className={aluno.situacao === 'MATRICULADO' ? undefined : styles.secundario}>
                      {aluno.turmaNome ?? SEM_DADO}
                    </td>
                    <td data-rotulo="Situação">
                      <Badge tone={badge.tom}>{badge.rotulo}</Badge>
                    </td>
                    <td className={styles.colAcoes}>
                      <div className={styles.acoes}>
                        <Button size="sm" icon="eye" onClick={() => onVer(aluno)}>
                          Ver
                        </Button>
                        {!anonimizado && onEditar && (
                          <Button size="sm" icon="edit" onClick={() => onEditar(aluno)}>
                            Editar
                          </Button>
                        )}
                        {!anonimizado && !inativo && onReenviarAcesso && (
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
                        )}
                        {!anonimizado && onAlternarSituacao && (
                          <Dica
                            texto={
                              inativo
                                ? 'Devolve o acesso ao portal com a senha que o aluno já tinha'
                                : 'Bloqueia o acesso ao portal sem apagar os dados do aluno'
                            }
                          >
                            <Button
                              size="sm"
                              className={styles.alternar}
                              icon={inativo ? 'check' : 'pause'}
                              disabled={alternandoId === aluno.id}
                              aria-label={`${inativo ? 'Reativar' : 'Inativar'} ${aluno.nome}`}
                              onClick={() => onAlternarSituacao(aluno)}
                            >
                              {inativo ? 'Reativar' : 'Inativar'}
                            </Button>
                          </Dica>
                        )}
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
            <span role="alert">{erro}</span>
            <Button size="sm" onClick={onTentarNovamente}>
              Tentar novamente
            </Button>
          </Mensagem>
        )}
        {!carregando && !erro && alunos.length === 0 && <Mensagem>{mensagemVazia}</Mensagem>}
      </section>
      {paginacao && !erro && (
        <Paginacao paginacao={paginacao} rotulo="Páginas dos alunos" carregando={atualizando} />
      )}
    </>
  )
}

function Mensagem({ children }: { children: ReactNode }) {
  return <div className={styles.mensagem}>{children}</div>
}
