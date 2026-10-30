import { useEffect, useMemo, useRef, useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router'
import { Button } from '../../../../components/Button/Button'
import { Icon } from '../../../../components/Icon/Icon'
import { paths } from '../../../../routes/paths'
import { ApiError } from '../../../../services/api'
import { listarCobrancas } from '../../../../services/cobrancaService'
import {
  atalhosSandboxHabilitados,
  mensagemErroSandbox,
  simularPagamentoNoSandbox,
} from '../../../../services/sandboxService'
import type { Cobranca, FiltroCobranca } from '../../../../types/cobranca'
import { CobrancaCard } from '../../components/CobrancaCard/CobrancaCard'
import { CobrancaDetalhe, type MensagemSandbox } from '../../components/CobrancaDetalhe/CobrancaDetalhe'
import { ModalBoleto } from '../../components/ModalBoleto/ModalBoleto'
import { FinanceiroLayout } from '../../layouts/FinanceiroLayout'
import styles from './Cobrancas.module.css'

const FILTROS: FiltroCobranca[] = ['TODAS', 'PENDENTE', 'EM_ATRASO', 'PAGA']

const ROTULOS_FILTRO: Record<FiltroCobranca, string> = {
  TODAS: 'Todas',
  PENDENTE: 'Pendentes',
  EM_ATRASO: 'Atrasadas',
  PAGA: 'Pagas',
}

/** Cobranças por página da lista. */
const POR_PAGINA = 10

/** O ?status= da URL (atalho do Início), ou Todas. */
function filtroDaUrl(status: string | null): FiltroCobranca {
  return FILTROS.find((filtro) => filtro === status) ?? 'TODAS'
}

export function Cobrancas() {
  const navigate = useNavigate()
  const [searchParams] = useSearchParams()
  const [cobrancas, setCobrancas] = useState<Cobranca[]>([])
  const [carregando, setCarregando] = useState(true)
  const [erro, setErro] = useState<string | null>(null)
  const [idAberto, setIdAberto] = useState<string | null>(null)
  const [termoBusca, setTermoBusca] = useState('')
  const [filtroStatus, setFiltroStatus] = useState<FiltroCobranca>(() => filtroDaUrl(searchParams.get('status')))
  const [pagina, setPagina] = useState(1)
  const topoDaLista = useRef<HTMLDivElement>(null)
  const [boletoModalCobranca, setBoletoModalCobranca] = useState<Cobranca | null>(null)
  const [simulando, setSimulando] = useState(false)
  const [mensagemSandbox, setMensagemSandbox] = useState<MensagemSandbox | null>(null)
  // Cada incremento busca a lista de novo, sem voltar ao "Carregando".
  const [recarga, setRecarga] = useState(0)

  useEffect(() => {
    const controller = new AbortController()
    listarCobrancas(controller.signal)
      .then((dados) => {
        setCobrancas(dados)
      })
      .catch((e: unknown) => {
        if (controller.signal.aborted) return
        setErro(e instanceof ApiError ? (e.detalhe ?? e.message) : 'Não foi possível carregar as cobranças.')
      })
      .finally(() => {
        if (!controller.signal.aborted) setCarregando(false)
      })

    return () => controller.abort()
  }, [recarga])

  /**
   * Atalho de teste: paga a cobrança no Sandbox. Ela só vira PAGA quando o
   * aviso do provedor chega, então a lista é lida de novo algumas vezes.
   */
  async function simularPagamento(cobranca: Cobranca) {
    setSimulando(true)
    setMensagemSandbox(null)
    try {
      await simularPagamentoNoSandbox(cobranca.id)
      setMensagemSandbox({
        texto: 'Pagamento confirmado no Asaas. A cobrança vira paga quando o aviso do Asaas chegar.',
        erro: false,
      })
      for (const espera of [4000, 10000]) {
        window.setTimeout(() => setRecarga((n) => n + 1), espera)
      }
    } catch (e) {
      setMensagemSandbox({
        texto: mensagemErroSandbox(
          e,
          'Esta cobrança ainda não foi emitida no Asaas: o aluno precisa gerar o PIX ou o boleto no portal antes.',
        ),
        erro: true,
      })
    } finally {
      setSimulando(false)
    }
  }

  /** A seta do card abre os detalhes; clicar de novo fecha. Só um aberto por vez. */
  function alternar(id: string) {
    setIdAberto((atual) => (atual === id ? null : id))
    setMensagemSandbox(null)
  }

  // Busca ou filtro novo: volta à primeira página.
  function buscar(termo: string) {
    setTermoBusca(termo)
    setPagina(1)
  }

  function filtrar(filtro: FiltroCobranca) {
    setFiltroStatus(filtro)
    setPagina(1)
  }

  function irParaPagina(nova: number) {
    setPagina(nova)
    topoDaLista.current?.scrollIntoView({ behavior: 'smooth', block: 'start' })
  }

  // Contadores dinâmicos para as pílulas de filtro
  const contadores = useMemo<Record<FiltroCobranca, number>>(() => {
    return {
      TODAS: cobrancas.length,
      PENDENTE: cobrancas.filter((c) => c.status === 'PENDENTE').length,
      EM_ATRASO: cobrancas.filter((c) => c.status === 'EM_ATRASO').length,
      PAGA: cobrancas.filter((c) => c.status === 'PAGA').length,
    }
  }, [cobrancas])

  // Filtragem combinada de busca por texto e status
  const cobrancasFiltradas = useMemo(() => {
    const busca = termoBusca.trim().toLowerCase()
    return cobrancas.filter((c) => {
      const matchBusca = !busca || (c.alunoNome ?? '').toLowerCase().includes(busca)
      if (!matchBusca) return false

      if (filtroStatus === 'TODAS') return true
      return c.status === filtroStatus
    })
  }, [cobrancas, termoBusca, filtroStatus])

  // A recarga pode encolher a lista: a página nunca passa da última.
  const totalPaginas = Math.max(1, Math.ceil(cobrancasFiltradas.length / POR_PAGINA))
  const paginaAtual = Math.min(pagina, totalPaginas)
  const inicio = (paginaAtual - 1) * POR_PAGINA
  const cobrancasDaPagina = cobrancasFiltradas.slice(inicio, inicio + POR_PAGINA)

  return (
    <FinanceiroLayout
      titulo="Cobranças"
      subtitulo="Clique na seta de uma cobrança para ver os detalhes."
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
            onChange={(e) => buscar(e.target.value)}
          />
        </div>

        <div className={styles.filtrosPills} role="tablist" aria-label="Filtrar por status">
          {FILTROS.map((filtro) => (
            <button
              key={filtro}
              type="button"
              role="tab"
              aria-selected={filtroStatus === filtro}
              className={`${styles.pillBtn} ${filtroStatus === filtro ? styles.pillAtivo : ''}`}
              onClick={() => filtrar(filtro)}
            >
              {ROTULOS_FILTRO[filtro]}
              <span className={styles.badge}>{contadores[filtro]}</span>
            </button>
          ))}
        </div>
      </div>

      <div ref={topoDaLista} className={styles.lista}>
        {carregando ? (
          <div className={styles.vazio}>
            <p>Carregando cobranças…</p>
          </div>
        ) : erro ? (
          <div className={styles.vazio} role="alert">
            <Icon name="fileText" size={32} />
            <p>{erro}</p>
          </div>
        ) : cobrancasFiltradas.length === 0 ? (
          <div className={styles.vazio}>
            <Icon name="fileText" size={32} />
            <p>Nenhuma cobrança encontrada para este filtro.</p>
          </div>
        ) : (
          cobrancasDaPagina.map((c) => (
            <CobrancaCard key={c.id} cobranca={c} aberta={idAberto === c.id} onAlternar={() => alternar(c.id)}>
              <CobrancaDetalhe
                cobranca={c}
                onVerBoleto={(cobranca) => setBoletoModalCobranca(cobranca)}
                onSimularPagamento={atalhosSandboxHabilitados ? simularPagamento : undefined}
                simulandoPagamento={simulando}
                mensagemSandbox={mensagemSandbox}
              />
            </CobrancaCard>
          ))
        )}
      </div>

      {!carregando && !erro && cobrancasFiltradas.length > POR_PAGINA && (
        <nav className={styles.paginacao} aria-label="Páginas das cobranças">
          <span className={styles.paginacaoResumo}>
            {inicio + 1}–{inicio + cobrancasDaPagina.length} de {cobrancasFiltradas.length}
          </span>
          <div className={styles.paginacaoBotoes}>
            <Button
              variant="outline"
              size="sm"
              icon="chevronLeft"
              onClick={() => irParaPagina(paginaAtual - 1)}
              disabled={paginaAtual === 1}
            >
              Anterior
            </Button>
            <span className={styles.paginacaoAtual} aria-current="page">
              Página {paginaAtual} de {totalPaginas}
            </span>
            <Button
              variant="outline"
              size="sm"
              onClick={() => irParaPagina(paginaAtual + 1)}
              disabled={paginaAtual === totalPaginas}
            >
              Próxima
              <Icon name="chevronRight" size={16} />
            </Button>
          </div>
        </nav>
      )}

      {/* Modais */}
      <ModalBoleto
        cobranca={boletoModalCobranca}
        onFechar={() => setBoletoModalCobranca(null)}
      />
    </FinanceiroLayout>
  )
}
