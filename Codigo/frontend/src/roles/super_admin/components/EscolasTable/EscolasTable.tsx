import { useState, type ReactNode } from 'react'
import { Badge } from '../../../../components/Badge/Badge'
import { Button } from '../../../../components/Button/Button'
import { Icon } from '../../../../components/Icon/Icon'
import type { OpcaoSelect } from '../../../../components/Select/Select'
import { OPCOES_PLANO, ROTULOS_PLANO, type Escola, type PlanoEscola } from '../../../../types/escola'
import { formatarCnpjCpf, somenteDigitos } from '../../../../utils/formatters'
import { FiltroDropdown } from '../FiltroDropdown/FiltroDropdown'
import styles from './EscolasTable.module.css'

type FiltroSituacao = 'todas' | 'ativas' | 'suspensas'

// 'sem-plano' pega escolas cadastradas antes do plano existir.
type FiltroPlano = 'todos' | PlanoEscola | 'sem-plano'

const OPCOES_FILTRO_PLANO: OpcaoSelect<FiltroPlano>[] = [
  { valor: 'todos', rotulo: 'Todos os planos' },
  ...OPCOES_PLANO,
  { valor: 'sem-plano', rotulo: 'Sem plano' },
]

function correspondeBusca(escola: Escola, busca: string): boolean {
  const termo = busca.trim().toLowerCase()
  if (!termo) return true

  const digitos = somenteDigitos(termo)
  return (
    escola.nome.toLowerCase().includes(termo) ||
    (escola.email?.toLowerCase().includes(termo) ?? false) ||
    (digitos.length > 0 && (escola.cnpjCpf?.includes(digitos) ?? false))
  )
}

function correspondePlano(escola: Escola, filtro: FiltroPlano): boolean {
  if (filtro === 'todos') return true
  if (filtro === 'sem-plano') return escola.plano === null
  return escola.plano === filtro
}

type EscolasTableProps = {
  escolas: Escola[]
  carregando: boolean
  erro: string | null
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
  alternandoId,
  onTentarNovamente,
  onEditar,
  onAlternarSituacao,
}: EscolasTableProps) {
  const [busca, setBusca] = useState('')
  const [filtroPlano, setFiltroPlano] = useState<FiltroPlano>('todos')
  const [filtroSituacao, setFiltroSituacao] = useState<FiltroSituacao>('todas')

  // Busca e plano primeiro, para os contadores de situação refletirem os dois.
  const filtradas = escolas.filter((e) => correspondeBusca(e, busca) && correspondePlano(e, filtroPlano))
  const ativas = filtradas.filter((e) => e.ativo)
  const suspensas = filtradas.filter((e) => !e.ativo)
  const visiveis = filtroSituacao === 'ativas' ? ativas : filtroSituacao === 'suspensas' ? suspensas : filtradas

  const opcoesSituacao: OpcaoSelect<FiltroSituacao>[] = [
    { valor: 'todas', rotulo: `Todas as situações (${filtradas.length})` },
    { valor: 'ativas', rotulo: `Ativas (${ativas.length})` },
    { valor: 'suspensas', rotulo: `Suspensas (${suspensas.length})` },
  ]

  let mensagemVazia = 'Nenhuma escola com os filtros selecionados.'
  if (escolas.length === 0) mensagemVazia = 'Nenhuma escola cadastrada ainda.'
  else if (busca.trim()) mensagemVazia = 'Nenhuma escola encontrada para a busca.'

  return (
    <section className={styles.card}>
      <div className={styles.toolbar}>
        <h2 className={styles.title}>Escolas cadastradas</h2>
        <div className={styles.controles}>
          <label className={styles.busca}>
            <Icon name="search" size={15} />
            <input
              type="search"
              placeholder="Buscar por nome, CNPJ ou e-mail"
              aria-label="Buscar escola por nome, CNPJ ou e-mail"
              value={busca}
              onChange={(e) => setBusca(e.target.value)}
            />
          </label>
          <div className={styles.filters} role="group" aria-label="Filtrar escolas">
            <FiltroDropdown
              aria-label="Filtrar por plano"
              opcoes={OPCOES_FILTRO_PLANO}
              valor={filtroPlano}
              valorPadrao="todos"
              onChange={setFiltroPlano}
            />
            <FiltroDropdown
              aria-label="Filtrar por situação"
              opcoes={opcoesSituacao}
              valor={filtroSituacao}
              valorPadrao="todas"
              onChange={setFiltroSituacao}
            />
          </div>
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
              <LinhaMensagem>{mensagemVazia}</LinhaMensagem>
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
