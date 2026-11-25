import type { ReactNode, Ref } from 'react'
import { Badge } from '../../../../../components/ui/Badge/Badge'
import { Button } from '../../../../../components/ui/Button/Button'
import { Icon } from '../../../../../components/ui/Icon/Icon'
import { MenuBotao, type ItemMenu } from '../../../../../components/ui/MenuBotao/MenuBotao'
import { Paginacao } from '../../../../../components/ui/Paginacao/Paginacao'
import type { EstadoPaginacao } from '../../../../../hooks/usePaginacao'
import {
  ROTULOS_SITUACAO_FINANCEIRA,
  type AlunoDaLista,
  type FinanceiroDoAluno,
  type SituacaoFinanceira,
  type SituacaoNaEscola,
} from '../../../../../types/pessoas/aluno'
import { formatarDiaMes, formatarMoeda, iniciais, plural } from '../../../../../utils/formatters'
import styles from './AlunosTable.module.css'

const SEM_DADO = '—'

const BADGE_SITUACAO: Record<SituacaoNaEscola, { rotulo: string; tom: 'success' | 'neutral' | 'danger' }> = {
  MATRICULADO: { rotulo: 'Matriculado', tom: 'success' },
  TRANCADO: { rotulo: 'Trancado', tom: 'neutral' },
  SEM_TURMA: { rotulo: 'Sem turma', tom: 'danger' },
  INATIVO: { rotulo: 'Inativo', tom: 'neutral' },
  ANONIMIZADO: { rotulo: 'Anonimizado', tom: 'neutral' },
}

const TOM_FINANCEIRO: Record<SituacaoFinanceira, 'danger' | 'warning' | 'success' | 'neutral'> = {
  EM_ATRASO: 'danger',
  A_VENCER: 'warning',
  EM_DIA: 'success',
  SEM_COBRANCA: 'neutral',
}

/** Detalhe abaixo do selo: há quantos dias atrasou, ou quando vence a próxima. */
function detalheFinanceiro(financeiro: FinanceiroDoAluno): string | null {
  if (financeiro.situacao === 'EM_ATRASO' && financeiro.diasDeAtraso !== null) {
    return `há ${financeiro.diasDeAtraso} ${plural(financeiro.diasDeAtraso, 'dia', 'dias')}`
  }
  if (financeiro.situacao === 'A_VENCER' && financeiro.proximoVencimento) {
    return `vence ${formatarDiaMes(financeiro.proximoVencimento)}`
  }
  return null
}

const ARIA_SORT = { ASC: 'ascending', DESC: 'descending' } as const

/**
 * As ações raras da linha, no menu "⋯": reenviar o acesso e inativar ou
 * reativar. Ficam fora da vista para a linha não virar uma fileira de botões
 * e para o Inativar não ficar a um clique distraído, colado no Editar.
 */
function maisAcoesDoAluno(
  aluno: AlunoDaLista,
  onReenviarAcesso: ((aluno: AlunoDaLista) => void) | undefined,
  onAlternarSituacao: ((aluno: AlunoDaLista) => void) | undefined,
): ItemMenu[] {
  // Sem dado pessoal, não há acesso para reenviar nem situação para trocar.
  if (aluno.situacao === 'ANONIMIZADO') return []
  const inativo = aluno.situacao === 'INATIVO'
  const itens: ItemMenu[] = []
  // Inativo não entra no portal: reenviar o acesso não adianta até reativar.
  if (!inativo && onReenviarAcesso) {
    itens.push({
      id: 'reenviar',
      rotulo: 'Reenviar acesso',
      descricao: 'Envia uma nova senha provisória para o e-mail do aluno',
      icone: 'mail',
      onSelecionar: () => onReenviarAcesso(aluno),
    })
  }
  if (onAlternarSituacao) {
    itens.push({
      id: 'alternar',
      rotulo: inativo ? 'Reativar' : 'Inativar',
      descricao: inativo
        ? 'Devolve o acesso ao portal com a senha que o aluno já tinha'
        : 'Bloqueia o acesso ao portal sem apagar os dados do aluno',
      icone: inativo ? 'check' : 'pause',
      onSelecionar: () => onAlternarSituacao(aluno),
    })
  }
  return itens
}

/** A coluna "Em aberto", para quem vê o financeiro (MASTER e FINANCEIRO). */
export type ColunaEmAberto = {
  /** Direção em que a lista está ordenada pelo valor em aberto; null se ordenada por outro campo. */
  ordem: 'ASC' | 'DESC' | null
  /** Clique no título: troca a ordem da lista. */
  onOrdenar: () => void
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
  /** Sem ela, a tabela não mostra a coluna "Em aberto" (SECRETARIA). */
  colunaEmAberto?: ColunaEmAberto
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
  colunaEmAberto,
}: AlunosTableProps) {
  const alunos = !erro && paginacao ? paginacao.itensDaPagina : []
  return (
    <>
      <section ref={topo} className={styles.card} aria-label="Alunos cadastrados" aria-busy={atualizando}>
        <div className={styles.rolagem}>
          <table className={`${styles.tabela} ${colunaEmAberto ? styles.comFinanceiro : ''}`}>
            <thead>
              <tr>
                <th scope="col">Aluno</th>
                <th scope="col">Matrícula</th>
                <th scope="col">Turma</th>
                <th scope="col">Situação</th>
                {colunaEmAberto && (
                  <th scope="col" aria-sort={colunaEmAberto.ordem ? ARIA_SORT[colunaEmAberto.ordem] : 'none'}>
                    <button
                      type="button"
                      className={styles.ordenar}
                      onClick={colunaEmAberto.onOrdenar}
                      aria-label={rotuloDaOrdem(colunaEmAberto.ordem)}
                    >
                      Em aberto
                      <span
                        className={`${styles.seta} ${colunaEmAberto.ordem === 'ASC' ? styles.setaParaCima : ''} ${
                          colunaEmAberto.ordem ? '' : styles.setaApagada
                        }`}
                        aria-hidden="true"
                      >
                        <Icon name="chevronDown" size={14} strokeWidth={2.5} />
                      </span>
                    </button>
                  </th>
                )}
                <th scope="col" className={styles.colAcoes}>
                  Ações
                </th>
              </tr>
            </thead>
            <tbody>
              {alunos.map((aluno) => {
                const badge = BADGE_SITUACAO[aluno.situacao]
                // Sem dado pessoal, não há o que editar.
                const anonimizado = aluno.situacao === 'ANONIMIZADO'
                const inativo = aluno.situacao === 'INATIVO'
                const maisAcoes = maisAcoesDoAluno(aluno, onReenviarAcesso, onAlternarSituacao)
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
                    {colunaEmAberto && (
                      <td data-rotulo="Em aberto">
                        {aluno.financeiro ? <EmAberto financeiro={aluno.financeiro} /> : SEM_DADO}
                      </td>
                    )}
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
                        {maisAcoes.length > 0 && (
                          <MenuBotao
                            somenteIcone
                            icon="more"
                            size="sm"
                            itens={maisAcoes}
                            // Enquanto reenvia, inativa ou reativa este aluno, o menu dele espera.
                            disabled={reenviandoId === aluno.id || alternandoId === aluno.id}
                          >
                            {`Mais ações para ${aluno.nome}`}
                          </MenuBotao>
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

/** O que diz o botão do título "Em aberto", para quem não vê a seta. */
function rotuloDaOrdem(ordem: ColunaEmAberto['ordem']): string {
  if (ordem === 'DESC') return 'Em aberto, do maior para o menor. Clique para ordenar do menor para o maior'
  if (ordem === 'ASC') return 'Em aberto, do menor para o maior. Clique para voltar à ordem pelo nome'
  return 'Em aberto. Clique para ordenar do maior para o menor'
}

/** Valor em aberto, com o selo da situação financeira e o detalhe (dias de atraso ou próximo vencimento). */
function EmAberto({ financeiro }: { financeiro: FinanceiroDoAluno }) {
  const detalhe = detalheFinanceiro(financeiro)
  return (
    <div className={styles.emAberto}>
      <strong className={financeiro.valorEmAberto > 0 ? undefined : styles.secundario}>
        {financeiro.valorEmAberto > 0 ? formatarMoeda(financeiro.valorEmAberto) : SEM_DADO}
      </strong>
      <span className={styles.situacaoFinanceira}>
        <Badge tone={TOM_FINANCEIRO[financeiro.situacao]}>{ROTULOS_SITUACAO_FINANCEIRA[financeiro.situacao]}</Badge>
        {detalhe && <span className={styles.secundario}>{detalhe}</span>}
      </span>
    </div>
  )
}

function Mensagem({ children }: { children: ReactNode }) {
  return <div className={styles.mensagem}>{children}</div>
}
