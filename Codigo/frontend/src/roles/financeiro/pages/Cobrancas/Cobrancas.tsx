import { useCallback, useEffect, useState } from 'react'
import { useLocation, useNavigate, useSearchParams } from 'react-router'
import { Button } from '../../../../components/Button/Button'
import { ModalComprovante } from '../../../../components/Comprovante/ModalComprovante'
import { ConfirmarExclusao } from '../../../../components/ConfirmarExclusao/ConfirmarExclusao'
import { Icon } from '../../../../components/Icon/Icon'
import { Paginacao } from '../../../../components/Paginacao/Paginacao'
import { paginacaoDoBackend, usePaginaDoBackend } from '../../../../hooks/usePaginacao'
import { useRotasFinanceiras } from '../../../../routes/areas'
import { type EstadoCobrancaCriada } from '../../../../routes/paths'
import { ApiError } from '../../../../services/api'
import { buscarComprovanteDaCobranca, cancelarCobranca, listarCobrancas } from '../../../../services/cobrancaService'
import {
  atalhosSandboxHabilitados,
  mensagemErroSandbox,
  simularPagamentoNoSandbox,
} from '../../../../services/sandboxService'
import type { Cobranca, FiltroCobranca, PaginaCobrancas, StatusCobranca } from '../../../../types/cobranca'
import { CobrancaCard } from '../../components/CobrancaCard/CobrancaCard'
import { CobrancaDetalhe, type MensagemSandbox } from '../../components/CobrancaDetalhe/CobrancaDetalhe'
import { ModalEditarCobranca } from '../../components/ModalEditarCobranca/ModalEditarCobranca'
import { FinanceiroLayout } from '../../layouts/FinanceiroLayout'
import styles from './Cobrancas.module.css'

const FILTROS: FiltroCobranca[] = ['TODAS', 'PENDENTE', 'EM_ATRASO', 'PAGA', 'CANCELADA']

const ROTULOS_FILTRO: Record<FiltroCobranca, string> = {
  TODAS: 'Todas',
  PENDENTE: 'A pagar',
  EM_ATRASO: 'Em atraso',
  PAGA: 'Pagas',
  CANCELADA: 'Canceladas',
}

/** Recusas do cancelamento, pelo `codigo` do backend. */
function mensagemErroCancelar(erro: unknown): string {
  if (erro instanceof ApiError) {
    if (erro.codigo === 'COBRANCA_JA_PAGA') return 'A cobrança já foi paga e não pode ser cancelada.'
    if (erro.codigo === 'PAGAMENTO_EM_PROCESSAMENTO') {
      return 'O pagamento desta cobrança está sendo gerado agora. Tente de novo em instantes.'
    }
    if (erro.codigo === 'PROVEDOR_INDISPONIVEL' || erro.codigo === 'PROVEDOR_NAO_CONFIGURADO') {
      return 'O Asaas não respondeu e a cobrança continua ativa. Tente de novo em instantes.'
    }
    return erro.detalhe ?? erro.message
  }
  return 'Erro inesperado ao cancelar a cobrança.'
}

/** Espera entre a última tecla da busca e a consulta ao backend. */
const ESPERA_BUSCA_MS = 300

/** O ?status= da URL (atalho do Início), ou Todas. */
function filtroDaUrl(status: string | null): FiltroCobranca {
  return FILTROS.find((filtro) => filtro === status) ?? 'TODAS'
}

/** A consulta que produziu cada resposta: página (a partir de 0), tamanho, filtro e nome buscado. */
function chaveDa(pagina: number, tamanho: number, filtro: FiltroCobranca, nome: string): string {
  return `${pagina}|${tamanho}|${filtro}|${nome}`
}

/** Última resposta do backend e a consulta que a produziu. */
type Carregado = { chave: string; dados: PaginaCobrancas | null; erro: string | null }

export function Cobrancas() {
  const navigate = useNavigate()
  const rotas = useRotasFinanceiras()
  const location = useLocation()
  const [searchParams] = useSearchParams()
  // Volta da Nova cobrança com a confirmação do que foi criado.
  const [sucesso] = useState(() => (location.state as EstadoCobrancaCriada | null)?.cobrancaCriada ?? null)
  const [carregado, setCarregado] = useState<Carregado | null>(null)
  const [idAberto, setIdAberto] = useState<string | null>(null)
  const [termoBusca, setTermoBusca] = useState('')
  // O termo vai ao backend só quando a digitação para.
  const [nomeBuscado, setNomeBuscado] = useState('')
  const [filtroStatus, setFiltroStatus] = useState<FiltroCobranca>(() => filtroDaUrl(searchParams.get('status')))
  const { pagina, setPagina, porPagina, irPara, mudarPorPagina, topo } = usePaginaDoBackend()
  const [editando, setEditando] = useState<Cobranca | null>(null)
  const [cancelando, setCancelando] = useState<Cobranca | null>(null)
  const [comprovanteDe, setComprovanteDe] = useState<string | null>(null)
  const [aviso, setAviso] = useState<string | null>(null)
  const [simulando, setSimulando] = useState(false)
  const [mensagemSandbox, setMensagemSandbox] = useState<MensagemSandbox | null>(null)
  // Cada incremento busca a página de novo, sem voltar ao "Carregando".
  const [recarga, setRecarga] = useState(0)

  // Busca nova: espera a digitação parar e volta à primeira página.
  useEffect(() => {
    const nome = termoBusca.trim()
    if (nome === nomeBuscado) return
    const espera = window.setTimeout(() => {
      setNomeBuscado(nome)
      setPagina(0)
    }, ESPERA_BUSCA_MS)
    return () => window.clearTimeout(espera)
  }, [termoBusca, nomeBuscado, setPagina])

  useEffect(() => {
    // Consulta nova cancela a anterior: resposta atrasada não sobrescreve a atual.
    const controller = new AbortController()
    const chave = chaveDa(pagina, porPagina, filtroStatus, nomeBuscado)
    listarCobrancas(
      {
        pagina,
        tamanho: porPagina,
        status: filtroStatus === 'TODAS' ? undefined : filtroStatus,
        nome: nomeBuscado || undefined,
      },
      controller.signal,
    )
      .then((dados) => {
        // A lista encolheu desde a última leitura (ex.: cobrança que mudou de situação): vai para a última página.
        if (dados.conteudo.length === 0 && dados.pagina > 0) {
          setPagina(Math.max(0, dados.totalPaginas - 1))
          return
        }
        setCarregado({ chave, dados, erro: null })
      })
      .catch((e: unknown) => {
        if (controller.signal.aborted) return
        const erro = e instanceof ApiError ? (e.detalhe ?? e.message) : 'Não foi possível carregar as cobranças.'
        setCarregado((anterior) => ({ chave, dados: anterior?.dados ?? null, erro }))
      })

    return () => controller.abort()
  }, [pagina, porPagina, filtroStatus, nomeBuscado, recarga, setPagina])

  // Descarta o estado já lido, para a confirmação não voltar ao recarregar a página.
  useEffect(() => {
    if (location.state) navigate({ pathname: location.pathname, search: location.search }, { replace: true, state: null })
  }, [location.state, location.pathname, location.search, navigate])

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

  const buscarComprovante = useCallback(() => buscarComprovanteDaCobranca(comprovanteDe ?? ''), [comprovanteDe])

  /** Depois de editar ou cancelar: confirma e lê a página de novo. */
  function concluir(mensagem: string) {
    setAviso(mensagem)
    setRecarga((n) => n + 1)
  }

  /** A seta do card abre os detalhes; clicar de novo fecha. Só um aberto por vez. */
  function alternar(id: string) {
    setIdAberto((atual) => (atual === id ? null : id))
    setMensagemSandbox(null)
  }

  /** Filtro novo: volta à primeira página. */
  function filtrar(filtro: FiltroCobranca) {
    setFiltroStatus(filtro)
    setPagina(0)
  }

  const carregando = carregado === null
  const erro = carregado?.erro ?? null
  const dados = carregado?.dados ?? null
  // Até a resposta chegar, a página anterior fica na tela e a navegação espera.
  const atualizando = carregado !== null && carregado.chave !== chaveDa(pagina, porPagina, filtroStatus, nomeBuscado)
  const cobrancas = dados?.conteudo ?? []

  // Pílulas: o backend conta cada situação na escola inteira, sem o filtro de situação nem a busca.
  const quantidade = (status: StatusCobranca) => dados?.totaisPorStatus.find((t) => t.status === status)?.quantidade ?? 0
  const contadores: Record<FiltroCobranca, number> = {
    TODAS: dados?.totaisPorStatus.reduce((soma, t) => soma + t.quantidade, 0) ?? 0,
    PENDENTE: quantidade('PENDENTE'),
    EM_ATRASO: quantidade('EM_ATRASO'),
    PAGA: quantidade('PAGA'),
    CANCELADA: quantidade('CANCELADA'),
  }

  return (
    <FinanceiroLayout
      titulo="Cobranças"
      subtitulo="Clique na seta de uma cobrança para ver os detalhes."
      acao={
        <Button
          variant="primary"
          icon="plus"
          onClick={() => navigate(rotas.novaCobranca)}
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

        <div className={styles.filtrosPills} role="group" aria-label="Filtrar por status">
          {FILTROS.map((filtro) => (
            <button
              key={filtro}
              type="button"
              aria-pressed={filtroStatus === filtro}
              className={`${styles.pillBtn} ${filtroStatus === filtro ? styles.pillAtivo : ''}`}
              onClick={() => filtrar(filtro)}
            >
              {ROTULOS_FILTRO[filtro]}
              <span className={styles.badge}>{contadores[filtro]}</span>
            </button>
          ))}
        </div>
      </div>

      {(aviso ?? sucesso) && (
        <p className={styles.sucesso} role="status">
          {aviso ?? sucesso}
        </p>
      )}

      <div ref={topo} className={styles.lista} aria-busy={atualizando}>
        {carregando ? (
          <div className={styles.vazio}>
            <p role="status">Carregando cobranças…</p>
          </div>
        ) : erro ? (
          <div className={styles.vazio} role="alert">
            <Icon name="fileText" size={32} />
            <p>{erro}</p>
          </div>
        ) : cobrancas.length === 0 ? (
          <div className={styles.vazio}>
            <Icon name="fileText" size={32} />
            <p>Nenhuma cobrança encontrada para este filtro.</p>
          </div>
        ) : (
          cobrancas.map((c) => (
            <CobrancaCard key={c.id} cobranca={c} aberta={idAberto === c.id} onAlternar={() => alternar(c.id)}>
              <CobrancaDetalhe
                cobranca={c}
                onEditar={setEditando}
                onCancelar={setCancelando}
                onVerComprovante={(cobranca) => setComprovanteDe(cobranca.id)}
                onSimularPagamento={atalhosSandboxHabilitados ? simularPagamento : undefined}
                simulandoPagamento={simulando}
                mensagemSandbox={mensagemSandbox}
              />
            </CobrancaCard>
          ))
        )}
      </div>

      {dados && !erro && (
        <Paginacao
          paginacao={paginacaoDoBackend(dados, { irPara, mudarPorPagina })}
          rotulo="Páginas das cobranças"
          carregando={atualizando}
        />
      )}

      {editando && (
        <ModalEditarCobranca
          cobranca={editando}
          onFechar={() => setEditando(null)}
          onSalva={(cobranca) => {
            setEditando(null)
            concluir(`Cobrança "${cobranca.descricao}" atualizada.`)
          }}
        />
      )}
      {cancelando && (
        <ConfirmarExclusao
          titulo="Cancelar cobrança"
          nome={cancelando.descricao}
          rotuloConfirmar="Cancelar cobrança"
          onFechar={() => setCancelando(null)}
          traduzirErro={mensagemErroCancelar}
          onConfirmar={async () => {
            await cancelarCobranca(cancelando.id)
            setCancelando(null)
            concluir(`Cobrança "${cancelando.descricao}" cancelada.`)
          }}
        >
          Cancelar a cobrança <strong>{cancelando.descricao}</strong> de {cancelando.alunoNome ?? 'aluno'}? Se ela já
          foi enviada ao Asaas, é removida lá primeiro. A cobrança continua no histórico como cancelada.
        </ConfirmarExclusao>
      )}
      {comprovanteDe && <ModalComprovante buscar={buscarComprovante} onFechar={() => setComprovanteDe(null)} />}
    </FinanceiroLayout>
  )
}
