import type { ReactNode } from 'react'
import { Badge } from '../../../../components/Badge/Badge'
import { Button } from '../../../../components/Button/Button'
import { ROTULOS_VINCULO, type Professor } from '../../../../types/professor'
import { iniciais } from '../../../../utils/formatters'
import styles from './ProfessoresTable.module.css'

// Disciplinas e turmas ainda não vêm no ProfessorResponse (dependem da alocação);
// as colunas ficam com um traço até o backend expor esses dados.
const SEM_DADO = '—'

type ProfessoresTableProps = {
  professores: Professor[]
  carregando: boolean
  erro: string | null
  /** Mensagem quando a lista filtrada fica vazia. */
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
  professores,
  carregando,
  erro,
  mensagemVazia,
  alternandoId,
  onTentarNovamente,
  onVer,
  onEditar,
  onAlternarSituacao,
  onExcluir,
}: ProfessoresTableProps) {
  return (
    <section className={styles.card} aria-label="Professores cadastrados">
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
            {!carregando &&
              !erro &&
              professores.map((professor) => (
                <tr key={professor.id} className={professor.ativo ? '' : styles.inativo}>
                  <td>
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
                  <td className={styles.secundario}>{SEM_DADO}</td>
                  <td>{professor.vinculo ? ROTULOS_VINCULO[professor.vinculo] : SEM_DADO}</td>
                  <td>{SEM_DADO}</td>
                  <td>
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
                      <Button
                        size="sm"
                        className={styles.alternar}
                        icon={professor.ativo ? 'pause' : 'check'}
                        disabled={alternandoId === professor.id}
                        title={professor.ativo ? 'Afastamento temporário, como licença' : undefined}
                        onClick={() => onAlternarSituacao(professor)}
                      >
                        {professor.ativo ? 'Inativar' : 'Reativar'}
                      </Button>
                      <Button
                        size="sm"
                        className={styles.excluir}
                        icon="trash"
                        aria-label={`Excluir ${professor.nome}`}
                        title="Excluir (saiu da escola)"
                        onClick={() => onExcluir(professor)}
                      />
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
          <span>{erro}</span>
          <Button size="sm" onClick={onTentarNovamente}>
            Tentar novamente
          </Button>
        </Mensagem>
      )}
      {!carregando && !erro && professores.length === 0 && <Mensagem>{mensagemVazia}</Mensagem>}
    </section>
  )
}

function Mensagem({ children }: { children: ReactNode }) {
  return <div className={styles.mensagem}>{children}</div>
}
