import { useState, type ReactNode } from 'react'
import { Badge } from '../../../../components/Badge/Badge'
import { Button } from '../../../../components/Button/Button'
import { Icon } from '../../../../components/Icon/Icon'
import { Paginacao } from '../../../../components/Paginacao/Paginacao'
import type { OpcaoSelect } from '../../../../components/Select/Select'
import { usePaginacao } from '../../../../hooks/usePaginacao'
import { OPCOES_PLANO, ROTULOS_PLANO, type Escola, type PlanoEscola } from '../../../../types/escola'
import { formatarCnpjCpf, iniciais, somenteDigitos } from '../../../../utils/formatters'
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
  onVer: (escola: Escola) => void
  onEditar: (escola: Escola) => void
  onFaturas: (escola: Escola) => void
  onAlternarSituacao: (escola: Escola) => void
}

/** Lista de escolas em cards de vidro, com busca e filtros por situação e plano. */
export function EscolasTable({
  escolas,
  carregando,
  erro,
  alternandoId,
  onTentarNovamente,
  onVer,
  onEditar,
  onFaturas,
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
  const { paginacao, topo } = usePaginacao<Escola, HTMLUListElement>(visiveis, {
    reiniciarCom: `${busca}|${filtroPlano}|${filtroSituacao}`,
  })

  const opcoesSituacao: { valor: FiltroSituacao; rotulo: string }[] = [
    { valor: 'todas', rotulo: `Todas (${filtradas.length})` },
    { valor: 'ativas', rotulo: `Ativas (${ativas.length})` },
    { valor: 'suspensas', rotulo: `Suspensas (${suspensas.length})` },
  ]

  let mensagemVazia = 'Nenhuma escola com os filtros selecionados.'
  if (escolas.length === 0) mensagemVazia = 'Nenhuma escola cadastrada ainda.'
  else if (busca.trim()) mensagemVazia = 'Nenhuma escola encontrada para a busca.'

  return (
    <section className={styles.secao} aria-labelledby="escolas-cadastradas">
      <h2 id="escolas-cadastradas" className={styles.title}>
        Escolas cadastradas
      </h2>

      <div className={styles.toolbar}>
        <label className={styles.busca}>
          <Icon name="search" size={17} />
          <input
            type="search"
            placeholder="Procurar por nome, CNPJ ou e-mail"
            aria-label="Buscar escola por nome, CNPJ ou e-mail"
            value={busca}
            onChange={(e) => setBusca(e.target.value)}
          />
        </label>
        <div className={styles.filters} role="group" aria-label="Filtrar por situação">
          {opcoesSituacao.map((opcao) => (
            <button
              key={opcao.valor}
              type="button"
              className={`${styles.chip} ${filtroSituacao === opcao.valor ? styles.chipAtivo : ''}`}
              aria-pressed={filtroSituacao === opcao.valor}
              onClick={() => setFiltroSituacao(opcao.valor)}
            >
              {opcao.rotulo}
            </button>
          ))}
          <FiltroDropdown
            aria-label="Filtrar por plano"
            opcoes={OPCOES_FILTRO_PLANO}
            valor={filtroPlano}
            valorPadrao="todos"
            onChange={setFiltroPlano}
          />
        </div>
      </div>

      {carregando && <Mensagem>Carregando escolas…</Mensagem>}
      {!carregando && erro && (
        <Mensagem>
          <span>{erro}</span>
          <Button size="sm" onClick={onTentarNovamente}>
            Tentar novamente
          </Button>
        </Mensagem>
      )}
      {!carregando && !erro && visiveis.length === 0 && <Mensagem>{mensagemVazia}</Mensagem>}

      {!carregando && !erro && visiveis.length > 0 && (
        <ul ref={topo} className={styles.lista}>
          {paginacao.itensDaPagina.map((escola) => (
            <li key={escola.id} className={`${styles.linha} ${escola.ativo ? '' : styles.suspensa}`}>
              <span className={styles.avatar} aria-hidden="true">
                {iniciais(escola.nome)}
              </span>
              <div className={styles.info}>
                <strong className={styles.nome}>{escola.nome}</strong>
                <span className={styles.meta}>
                  <span className={styles.cnpj}>
                    {escola.cnpjCpf ? formatarCnpjCpf(escola.cnpjCpf) : 'Sem CNPJ'}
                  </span>
                  {' · '}
                  {escola.plano ? `Plano ${ROTULOS_PLANO[escola.plano]}` : 'Sem plano'}
                  {escola.email && ` · ${escola.email}`}
                </span>
              </div>
              {escola.ativo ? (
                <Badge tone="success" icon="check">
                  Ativa
                </Badge>
              ) : (
                <Badge tone="danger">Suspensa</Badge>
              )}
              <div className={styles.actions}>
                <Button size="sm" icon="eye" onClick={() => onVer(escola)}>
                  Ver
                </Button>
                <Button size="sm" icon="edit" onClick={() => onEditar(escola)}>
                  Editar
                </Button>
                <Button size="sm" icon="dollarSign" onClick={() => onFaturas(escola)}>
                  Faturas
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
            </li>
          ))}
        </ul>
      )}
      {!carregando && !erro && <Paginacao paginacao={paginacao} rotulo="Páginas das escolas" />}
    </section>
  )
}

function Mensagem({ children }: { children: ReactNode }) {
  return <div className={styles.mensagem}>{children}</div>
}
