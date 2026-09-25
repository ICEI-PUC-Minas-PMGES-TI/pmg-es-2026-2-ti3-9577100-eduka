import { useState, type ReactNode } from 'react'
import { Badge } from '../../../../components/Badge/Badge'
import { Button } from '../../../../components/Button/Button'
import { ROTULOS_PLANO, type Escola } from '../../../../types/escola'
import { formatarCnpjCpf } from '../../../../utils/formatters'
import { FiltroPlano, type ValorFiltroPlano } from '../FiltroPlano/FiltroPlano'
import styles from './EscolasTable.module.css'

type Filtro = 'todas' | 'ativas'

function correspondePlano(escola: Escola, filtro: ValorFiltroPlano): boolean {
  if (filtro === 'todos') return true
  if (filtro === 'sem-plano') return escola.plano === null
  return escola.plano === filtro
}

type EscolasTableProps = {
  escolas: Escola[]
  carregando: boolean
  erro: string | null
  mensagemVazia: string
  /** Escola com PATCH em andamento, para travar os botões da linha. */
  alternandoId: string | null
  onTentarNovamente: () => void
  onEditar: (escola: Escola) => void
  onAlternarSituacao: (escola: Escola) => void
}

export function EscolasTable({
  escolas,
  carregando,
  erro,
  mensagemVazia,
  alternandoId,
  onTentarNovamente,
  onEditar,
  onAlternarSituacao,
}: EscolasTableProps) {
  const [filtro, setFiltro] = useState<Filtro>('todas')
  const [filtroPlano, setFiltroPlano] = useState<ValorFiltroPlano>('todos')

  // Os contadores de "Todas" e "Ativas" já refletem o plano escolhido.
  const doPlano = escolas.filter((e) => correspondePlano(e, filtroPlano))
  const ativas = doPlano.filter((e) => e.ativo)
  const visiveis = filtro === 'ativas' ? ativas : doPlano
  // Há escolas, mas os filtros da tabela esconderam todas.
  const vazioPorFiltro = escolas.length > 0 && (filtro !== 'todas' || filtroPlano !== 'todos')

  return (
    <section className={styles.card}>
      <div className={styles.toolbar}>
        <h2 className={styles.title}>Escolas cadastradas</h2>
        <div className={styles.filters} role="group" aria-label="Filtrar escolas">
          <FiltroPlano valor={filtroPlano} onChange={setFiltroPlano} />
          <FiltroBotao ativo={filtro === 'todas'} onClick={() => setFiltro('todas')}>
            Todas ({doPlano.length})
          </FiltroBotao>
          <FiltroBotao ativo={filtro === 'ativas'} onClick={() => setFiltro('ativas')}>
            Ativas ({ativas.length})
          </FiltroBotao>
        </div>
      </div>

      <div className={styles.scroll}>
        <table className={styles.table}>
          <thead>
            <tr>
              <th>Escola</th>
              <th>CNPJ</th>
              <th>Plano</th>
              <th>Situação</th>
              <th className={styles.actionsCol}>Ações</th>
            </tr>
          </thead>
          <tbody>
            {carregando && <LinhaMensagem>Carregando escolas…</LinhaMensagem>}
            {!carregando && erro && (
              <LinhaMensagem>
                <span>{erro}</span>
                <Button size="sm" onClick={onTentarNovamente}>
                  Tentar novamente
                </Button>
              </LinhaMensagem>
            )}
            {!carregando && !erro && visiveis.length === 0 && (
              <LinhaMensagem>
                {vazioPorFiltro ? 'Nenhuma escola com os filtros selecionados.' : mensagemVazia}
              </LinhaMensagem>
            )}
            {!carregando && !erro && visiveis.map((escola) => (
              <tr key={escola.id}>
                <td className={styles.nome}>{escola.nome}</td>
                <td className={styles.cnpj}>{escola.cnpjCpf ? formatarCnpjCpf(escola.cnpjCpf) : '—'}</td>
                <td>{escola.plano ? ROTULOS_PLANO[escola.plano] : '—'}</td>
                <td>
                  {escola.ativo ? (
                    <Badge tone="success" icon="check">
                      Ativa
                    </Badge>
                  ) : (
                    <Badge tone="danger">Suspensa</Badge>
                  )}
                </td>
                <td className={styles.actionsCol}>
                  <div className={styles.actions}>
                    <Button size="sm" icon="edit" onClick={() => onEditar(escola)}>
                      Editar
                    </Button>
                    <Button
                      size="sm"
                      className={styles.alternar}
                      icon={escola.ativo ? 'pause' : 'check'}
                      disabled={alternandoId === escola.id}
                      onClick={() => onAlternarSituacao(escola)}
                    >
                      {escola.ativo ? 'Suspender' : 'Reativar'}
                    </Button>
                  </div>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </section>
  )
}

function LinhaMensagem({ children }: { children: ReactNode }) {
  return (
    <tr>
      <td colSpan={5} className={styles.empty}>
        <div className={styles.emptyContent}>{children}</div>
      </td>
    </tr>
  )
}

type FiltroBotaoProps = {
  ativo: boolean
  onClick: () => void
  children: ReactNode
}

function FiltroBotao({ ativo, onClick, children }: FiltroBotaoProps) {
  return (
    <button
      type="button"
      className={`${styles.filter} ${ativo ? styles.filterActive : ''}`}
      aria-pressed={ativo}
      onClick={onClick}
    >
      {children}
    </button>
  )
}
