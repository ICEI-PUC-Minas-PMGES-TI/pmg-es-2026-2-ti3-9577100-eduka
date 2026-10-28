import { useEffect, useMemo, useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router'
import { Button } from '../../../../components/Button/Button'
import { Icon } from '../../../../components/Icon/Icon'
import { paths } from '../../../../routes/paths'
import {
  listarCobrancas,
  registrarPagamento,
} from '../../../../services/cobrancaService'
import {
  COBRANCAS_MOCK_INICIAIS,
  type Cobranca,
  type DadosRegistroPagamento,
  type FiltroCobranca,
} from '../../../../types/cobranca'
import { CobrancaCard } from '../../components/CobrancaCard/CobrancaCard'
import { CobrancaDetalhe } from '../../components/CobrancaDetalhe/CobrancaDetalhe'
import { ModalBoleto } from '../../components/ModalBoleto/ModalBoleto'
import { FinanceiroLayout } from '../../layouts/FinanceiroLayout'
import styles from './Cobrancas.module.css'

const FILTROS: FiltroCobranca[] = ['TODAS', 'A_PAGAR', 'EM_ATRASO', 'PAGA']

/** O ?status= da URL (atalho do Início), ou Todas. */
function filtroDaUrl(status: string | null): FiltroCobranca {
  return FILTROS.find((filtro) => filtro === status) ?? 'TODAS'
}

export function Cobrancas() {
  const navigate = useNavigate()
  const [searchParams] = useSearchParams()
  const [cobrancas, setCobrancas] = useState<Cobranca[]>(COBRANCAS_MOCK_INICIAIS)
  const [idSelecionado, setIdSelecionado] = useState<string>(
    COBRANCAS_MOCK_INICIAIS[0]?.id || '',
  )
  const [termoBusca, setTermoBusca] = useState('')
  const [filtroStatus, setFiltroStatus] = useState<FiltroCobranca>(() => filtroDaUrl(searchParams.get('status')))
  const [boletoModalCobranca, setBoletoModalCobranca] = useState<Cobranca | null>(null)

  useEffect(() => {
    const controller = new AbortController()
    listarCobrancas(controller.signal)
      .then((dados) => {
        setCobrancas(dados)
      })
      .catch(() => {
        // Fallback garantido
      })

    return () => controller.abort()
  }, [])

  // Contadores dinâmicos para as pílulas de filtro
  const contadores = useMemo(() => {
    return {
      todas: cobrancas.length,
      aPagar: cobrancas.filter((c) => c.status === 'A_PAGAR').length,
      emAtraso: cobrancas.filter((c) => c.status === 'EM_ATRASO').length,
      pagas: cobrancas.filter((c) => c.status === 'PAGA').length,
    }
  }, [cobrancas])

  // Filtragem combinada de busca por texto e status
  const cobrancasFiltradas = useMemo(() => {
    const busca = termoBusca.trim().toLowerCase()
    return cobrancas.filter((c) => {
      const matchBusca = !busca || c.alunoNome.toLowerCase().includes(busca)
      if (!matchBusca) return false

      if (filtroStatus === 'TODAS') return true
      return c.status === filtroStatus
    })
  }, [cobrancas, termoBusca, filtroStatus])

  // Item selecionado derivado diretamente da renderização
  const cobrancaSelecionada = useMemo(() => {
    return (
      cobrancasFiltradas.find((c) => c.id === idSelecionado) ??
      cobrancasFiltradas[0] ??
      cobrancas.find((c) => c.id === idSelecionado) ??
      null
    )
  }, [cobrancasFiltradas, cobrancas, idSelecionado])

  async function handleRegistrarPagamento(id: string, dados?: DadosRegistroPagamento) {
    const atualizada = await registrarPagamento(id, dados)
    setCobrancas((prev) => prev.map((c) => (c.id === id ? atualizada : c)))
  }

  return (
    <FinanceiroLayout
      titulo="Cobranças"
      subtitulo="Clique em uma cobrança para ver os detalhes ou registrar o pagamento."
      acao={
        <Button
          variant="primary"
          icon="plus"
          onClick={() => navigate(paths.financeiro.novaCobranca)}
        >
          Nova cobrança
        </Button>
      }
    >
      {/* Barra de Busca e Filtros com Contadores */}
      <div className={styles.toolbar}>
        <div className={styles.busca}>
          <span className={styles.buscaIcone} aria-hidden="true">
            <Icon name="search" size={18} />
          </span>
          <input
            type="search"
            className={styles.buscaInput}
            placeholder="Procurar pelo nome do aluno"
            aria-label="Procurar pelo nome do aluno"
            value={termoBusca}
            onChange={(e) => setTermoBusca(e.target.value)}
          />
        </div>

        <div className={styles.filtrosPills} role="tablist" aria-label="Filtrar por status">
          <button
            type="button"
            role="tab"
            aria-selected={filtroStatus === 'TODAS'}
            className={`${styles.pillBtn} ${filtroStatus === 'TODAS' ? styles.pillAtivo : ''}`}
            onClick={() => setFiltroStatus('TODAS')}
          >
            Todas
            <span className={styles.badge}>{contadores.todas}</span>
          </button>
          <button
            type="button"
            role="tab"
            aria-selected={filtroStatus === 'A_PAGAR'}
            className={`${styles.pillBtn} ${filtroStatus === 'A_PAGAR' ? styles.pillAtivo : ''}`}
            onClick={() => setFiltroStatus('A_PAGAR')}
          >
            A pagar
            <span className={styles.badge}>{contadores.aPagar}</span>
          </button>
          <button
            type="button"
            role="tab"
            aria-selected={filtroStatus === 'EM_ATRASO'}
            className={`${styles.pillBtn} ${filtroStatus === 'EM_ATRASO' ? styles.pillAtivo : ''}`}
            onClick={() => setFiltroStatus('EM_ATRASO')}
          >
            Atrasadas
            <span className={styles.badge}>{contadores.emAtraso}</span>
          </button>
          <button
            type="button"
            role="tab"
            aria-selected={filtroStatus === 'PAGA'}
            className={`${styles.pillBtn} ${filtroStatus === 'PAGA' ? styles.pillAtivo : ''}`}
            onClick={() => setFiltroStatus('PAGA')}
          >
            Pagas
            <span className={styles.badge}>{contadores.pagas}</span>
          </button>
        </div>
      </div>

      {/* Conteúdo Master-Detail em Split Screen */}
      <div className={styles.conteudoSplit}>
        <div className={styles.listaCobrancas}>
          {cobrancasFiltradas.length === 0 ? (
            <div className={styles.vazio}>
              <Icon name="fileText" size={32} />
              <p>Nenhuma cobrança encontrada para este filtro.</p>
            </div>
          ) : (
            cobrancasFiltradas.map((c) => (
              <CobrancaCard
                key={c.id}
                cobranca={c}
                selecionada={cobrancaSelecionada?.id === c.id}
                onClick={() => setIdSelecionado(c.id)}
              />
            ))
          )}
        </div>

        {cobrancaSelecionada && (
          <CobrancaDetalhe
            cobranca={cobrancaSelecionada}
            onVerBoleto={(c) => setBoletoModalCobranca(c)}
            onRegistrarPagamento={handleRegistrarPagamento}
          />
        )}
      </div>

      {/* Modais */}
      <ModalBoleto
        cobranca={boletoModalCobranca}
        onFechar={() => setBoletoModalCobranca(null)}
      />
    </FinanceiroLayout>
  )
}
