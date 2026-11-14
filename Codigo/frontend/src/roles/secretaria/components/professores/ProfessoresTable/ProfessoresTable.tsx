import type { ReactNode, Ref } from 'react'
import { Badge } from '../../../../../components/ui/Badge/Badge'
import { Button } from '../../../../../components/ui/Button/Button'
import { Dica } from '../../../../../components/ui/Dica/Dica'
import { Paginacao } from '../../../../../components/ui/Paginacao/Paginacao'
import type { EstadoPaginacao } from '../../../../../hooks/usePaginacao'
import { ROTULOS_VINCULO, type Professor } from '../../../../../types/pessoas/professor'
import { iniciais } from '../../../../../utils/formatters'
import styles from './ProfessoresTable.module.css'

// Disciplinas e turmas ainda não vêm no ProfessorResponse (dependem da alocação);
// as colunas ficam com um traço até o backend expor esses dados.
const SEM_DADO = '—'

type ProfessoresTableProps = {
  /** A página de professores; null até a primeira resposta do backend. */
  paginacao: EstadoPaginacao<Professor> | null
  /** Recebe o topo da tabela, para onde a troca de página rola. */
  topo: Ref<HTMLElement>
  carregando: boolean
  /** Outra página ou busca a caminho: a lista atual fica e a navegação espera. */
  atualizando: boolean
  erro: string | null
  /** Mensagem quando a página fica vazia. */
  mensagemVazia: string
  /** Professor com inativação/reativação em andamento. */
  alternandoId: string | null
  onTentarNovamente: () => void
  onVer: (professor: Professor) => void
  onEditar: (professor: Professor) => void
  onAlternarSituacao: (professor: Professor) => void
  onExcluir: (professor: Professor) => void
}

/** Tabela de professores num card de vidro, com os estados de carregando, erro e vazio. */
export function ProfessoresTable({
  paginacao,
  topo,
  carregando,
  atualizando,
  erro,
  mensagemVazia,
  alternandoId,
  onTentarNovamente,
  onVer,
  onEditar,
  onAlternarSituacao,
  onExcluir,
}: ProfessoresTableProps) {
  const professores = !erro && paginacao ? paginacao.itensDaPagina : []
  return (
    <>
      <section ref={topo} className={styles.card} aria-label="Professores cadastrados" aria-busy={atualizando}>
        <div className={styles.rolagem}>
          <table className={styles.tabela}>
            <thead>
              <tr>
                <th scope="col">Professor</th>
                <th scope="col">Disciplinas</th>
                <th scope="col">Vínculo</th>
                <th scope="col">Turmas</th>
                <th scope="col">Situação</th>
                <th scope="col" className={styles.colAcoes}>
                  Ações
                </th>
              </tr>
            </thead>
            <tbody>
              {professores.map((professor) => (
                  <tr key={professor.id} className={professor.ativo ? '' : styles.inativo}>
                    <td className={styles.colPrincipal}>
                      <div className={styles.professor}>
                        <span className={styles.avatar} aria-hidden="true">
                          {iniciais(professor.nome)}
                        </span>
                        <div className={styles.info}>
                          <strong className={styles.nome}>{professor.nome}</strong>
                          {professor.email && <span className={styles.email}>{professor.email}</span>}
                        </div>
                      </div>
                    </td>
                    <td className={`${styles.secundario} ${styles.semDado}`}>{SEM_DADO}</td>
                    <td data-rotulo="Vínculo">{professor.vinculo ? ROTULOS_VINCULO[professor.vinculo] : SEM_DADO}</td>
                    <td className={styles.semDado}>{SEM_DADO}</td>
                    <td data-rotulo="Situação">
                      {professor.ativo ? (
                        <Badge tone="success">Ativo</Badge>
                      ) : (
                        <Badge tone="neutral">Inativo</Badge>
                      )}
                    </td>
                    <td className={styles.colAcoes}>
                      <div className={styles.acoes}>
                        <Button size="sm" icon="eye" onClick={() => onVer(professor)}>
                          Ver
                        </Button>
                        <Button size="sm" icon="edit" onClick={() => onEditar(professor)}>
                          Editar
                        </Button>
                        <Dica texto={professor.ativo ? 'Afastamento temporário, como licença' : null}>
                          <Button
                            size="sm"
                            className={styles.alternar}
                            icon={professor.ativo ? 'pause' : 'check'}
                            disabled={alternandoId === professor.id}
                            onClick={() => onAlternarSituacao(professor)}
                          >
                            {professor.ativo ? 'Inativar' : 'Reativar'}
                          </Button>
                        </Dica>
                        <Dica texto="Excluir (saiu da escola)">
                          <Button
                            size="sm"
                            variant="dangerOutline"
                            icon="trash"
                            aria-label={`Excluir ${professor.nome}`}
                            onClick={() => onExcluir(professor)}
                          />
                        </Dica>
                      </div>
                    </td>
                  </tr>
                ))}
            </tbody>
          </table>
        </div>

        {carregando && <Mensagem>Carregando professores…</Mensagem>}
        {!carregando && erro && (
          <Mensagem>
            <span role="alert">{erro}</span>
            <Button size="sm" onClick={onTentarNovamente}>
              Tentar novamente
            </Button>
          </Mensagem>
        )}
        {!carregando && !erro && professores.length === 0 && <Mensagem>{mensagemVazia}</Mensagem>}
      </section>
      {paginacao && !erro && (
        <Paginacao paginacao={paginacao} rotulo="Páginas dos professores" carregando={atualizando} />
      )}
    </>
  )
}

function Mensagem({ children }: { children: ReactNode }) {
  return <div className={styles.mensagem}>{children}</div>
}
