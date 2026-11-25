import type { ReactNode, Ref } from 'react'
import { Badge } from '../../../../../components/ui/Badge/Badge'
import { Button } from '../../../../../components/ui/Button/Button'
import { Dica } from '../../../../../components/ui/Dica/Dica'
import { MenuBotao, type ItemMenu } from '../../../../../components/ui/MenuBotao/MenuBotao'
import { Paginacao } from '../../../../../components/ui/Paginacao/Paginacao'
import type { EstadoPaginacao } from '../../../../../hooks/usePaginacao'
import { ROTULOS_VINCULO, type Professor } from '../../../../../types/pessoas/professor'
import { iniciais } from '../../../../../utils/formatters'
import styles from './ProfessoresTable.module.css'

// Disciplinas e turmas ainda não vêm no ProfessorResponse (dependem da alocação);
// as colunas ficam com um traço até o backend expor esses dados.
const SEM_DADO = '—'

/**
 * As ações raras da linha, no menu "⋯": reenviar o acesso (só a quem ainda
 * não entrou), inativar ou reativar e excluir. Ver e Editar ficam à vista.
 */
function maisAcoesDoProfessor(
  professor: Professor,
  { onReenviarAcesso, onAlternarSituacao, onExcluir }: Pick<
    ProfessoresTableProps,
    'onReenviarAcesso' | 'onAlternarSituacao' | 'onExcluir'
  >,
): ItemMenu[] {
  const itens: ItemMenu[] = []
  if (professor.ativo && professor.aguardandoPrimeiroAcesso) {
    itens.push({
      id: 'reenviar',
      rotulo: 'Reenviar acesso',
      descricao: 'Gera uma nova senha provisória e manda o e-mail de novo',
      icone: 'mail',
      onSelecionar: () => onReenviarAcesso(professor),
    })
  }
  itens.push(
    {
      id: 'alternar',
      rotulo: professor.ativo ? 'Inativar' : 'Reativar',
      descricao: professor.ativo ? 'Afastamento temporário, como licença' : 'Encerra o afastamento',
      icone: professor.ativo ? 'pause' : 'check',
      onSelecionar: () => onAlternarSituacao(professor),
    },
    {
      id: 'excluir',
      rotulo: 'Excluir',
      descricao: 'Quando o professor saiu da escola',
      icone: 'trash',
      perigo: true,
      onSelecionar: () => onExcluir(professor),
    },
  )
  return itens
}

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
  /** Professor com o reenvio do acesso em andamento. */
  reenviandoId: string | null
  onTentarNovamente: () => void
  onVer: (professor: Professor) => void
  onEditar: (professor: Professor) => void
  onAlternarSituacao: (professor: Professor) => void
  onReenviarAcesso: (professor: Professor) => void
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
  reenviandoId,
  onTentarNovamente,
  onVer,
  onEditar,
  onAlternarSituacao,
  onReenviarAcesso,
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
                      <div className={styles.situacao}>
                        {professor.ativo ? (
                          <Badge tone="success">Ativo</Badge>
                        ) : (
                          <Badge tone="neutral">Inativo</Badge>
                        )}
                        {professor.ativo && professor.aguardandoPrimeiroAcesso && (
                          <Dica texto="Ainda não definiu a senha. O acesso vale por alguns dias; depois, reenvie.">
                            <Badge tone="warning">Aguardando acesso</Badge>
                          </Dica>
                        )}
                      </div>
                    </td>
                    <td className={styles.colAcoes}>
                      <div className={styles.acoes}>
                        <Button size="sm" icon="eye" onClick={() => onVer(professor)}>
                          Ver
                        </Button>
                        <Button size="sm" icon="edit" onClick={() => onEditar(professor)}>
                          Editar
                        </Button>
                        <MenuBotao
                          somenteIcone
                          icon="more"
                          size="sm"
                          itens={maisAcoesDoProfessor(professor, { onReenviarAcesso, onAlternarSituacao, onExcluir })}
                          // Enquanto reenvia, inativa ou reativa este professor, o menu dele espera.
                          disabled={reenviandoId === professor.id || alternandoId === professor.id}
                        >
                          {`Mais ações para ${professor.nome}`}
                        </MenuBotao>
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
