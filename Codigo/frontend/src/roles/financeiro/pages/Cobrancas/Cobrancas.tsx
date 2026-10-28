import { useEffect, useMemo, useState } from 'react'
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
  const [idSelecionado, setIdSelecionado] = useState<string>('')
  const [termoBusca, setTermoBusca] = useState('')
  const [filtroStatus, setFiltroStatus] = useState<FiltroCobranca>(() => filtroDaUrl(searchParams.get('status')))
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

  function selecionar(id: string) {
    setIdSelecionado(id)
    setMensagemSandbox(null)
  }

  // Contadores dinâmicos para as pílulas de filtro
  const contadores = useMemo(() => {
    return {
      todas: cobrancas.length,
      pendentes: cobrancas.filter((c) => c.status === 'PENDENTE').length,
      emAtraso: cobrancas.filter((c) => c.status === 'EM_ATRASO').length,
      pagas: cobrancas.filter((c) => c.status === 'PAGA').length,
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

  // Item selecionado derivado diretamente da renderização
  const cobrancaSelecionada = useMemo(() => {
    return (
      cobrancasFiltradas.find((c) => c.id === idSelecionado) ??
      cobrancasFiltradas[0] ??
      cobrancas.find((c) => c.id === idSelecionado) ??
      null
    )
  }, [cobrancasFiltradas, cobrancas, idSelecionado])

  return (
    <FinanceiroLayout
      titulo="Cobranças"
      subtitulo="Clique em uma cobrança para ver os detalhes."
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
            aria-selected={filtroStatus === 'PENDENTE'}
            className={`${styles.pillBtn} ${filtroStatus === 'PENDENTE' ? styles.pillAtivo : ''}`}
            onClick={() => setFiltroStatus('PENDENTE')}
          >
            Pendentes
            <span className={styles.badge}>{contadores.pendentes}</span>
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
            cobrancasFiltradas.map((c) => (
              <CobrancaCard
                key={c.id}
                cobranca={c}
                selecionada={cobrancaSelecionada?.id === c.id}
                onClick={() => selecionar(c.id)}
              />
            ))
          )}
        </div>

        {cobrancaSelecionada && (
          <CobrancaDetalhe
            cobranca={cobrancaSelecionada}
            onVerBoleto={(c) => setBoletoModalCobranca(c)}
            onSimularPagamento={atalhosSandboxHabilitados ? simularPagamento : undefined}
            simulandoPagamento={simulando}
            mensagemSandbox={mensagemSandbox}
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
