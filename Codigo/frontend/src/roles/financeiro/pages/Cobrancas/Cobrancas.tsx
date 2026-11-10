import { useCallback, useEffect, useMemo, useState } from 'react'
import { useLocation, useNavigate, useSearchParams } from 'react-router'
import { Button } from '../../../../components/Button/Button'
import { ModalComprovante } from '../../../../components/Comprovante/ModalComprovante'
import { ConfirmarExclusao } from '../../../../components/ConfirmarExclusao/ConfirmarExclusao'
import { Icon } from '../../../../components/Icon/Icon'
import { Paginacao } from '../../../../components/Paginacao/Paginacao'
import { useCarregamento } from '../../../../hooks/useCarregamento'
import { useListaPaginada } from '../../../../hooks/usePaginacao'
import { useTermoAdiado } from '../../../../hooks/useTermoAdiado'
import { useRotasFinanceiras } from '../../../../routes/areas'
import { type EstadoCobrancaCriada } from '../../../../routes/paths'
import { buscarAluno } from '../../../../services/alunoService'
import { ApiError } from '../../../../services/api'
import { buscarComprovanteDaCobranca, cancelarCobranca, listarCobrancas } from '../../../../services/cobrancaService'
import {
  atalhosSandboxHabilitados,
  mensagemErroSandbox,
  simularPagamentoNoSandbox,
} from '../../../../services/sandboxService'
import type { Aluno } from '../../../../types/aluno'
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

/** O ?status= da URL (atalho do Início), ou Todas. */
function filtroDaUrl(status: string | null): FiltroCobranca {
  return FILTROS.find((filtro) => filtro === status) ?? 'TODAS'
}

export function Cobrancas() {
  const navigate = useNavigate()
  const rotas = useRotasFinanceiras()
  const location = useLocation()
  const [searchParams, setSearchParams] = useSearchParams()
  // ?aluno={id}: vem da aba Financeiro do aluno (IU18) e mostra só as cobranças dele.
  const alunoId = searchParams.get('aluno')
  const buscarAlunoFiltrado = useMemo(
    () => (alunoId ? (signal: AbortSignal) => buscarAluno(alunoId, signal) : null),
    [alunoId],
  )
  // Só o nome para o aviso do filtro: se não carregar, a lista filtrada aparece do mesmo jeito.
  const alunoFiltrado = useCarregamento<Aluno | null>(buscarAlunoFiltrado, null, 'O aluno não existe mais.')
  // Volta da Nova cobrança com a confirmação do que foi criado.
  const [sucesso] = useState(() => (location.state as EstadoCobrancaCriada | null)?.cobrancaCriada ?? null)
  const [idAberto, setIdAberto] = useState<string | null>(null)
  const [termoBusca, setTermoBusca] = useState('')
  // O termo vai ao backend só quando a digitação para.
  const nomeBuscado = useTermoAdiado(termoBusca)
  const [filtroStatus, setFiltroStatus] = useState<FiltroCobranca>(() => filtroDaUrl(searchParams.get('status')))
  const [editando, setEditando] = useState<Cobranca | null>(null)
  const [cancelando, setCancelando] = useState<Cobranca | null>(null)
  const [comprovanteDe, setComprovanteDe] = useState<string | null>(null)
  const [aviso, setAviso] = useState<string | null>(null)
  const [simulando, setSimulando] = useState(false)
  const [mensagemSandbox, setMensagemSandbox] = useState<MensagemSandbox | null>(null)

  // Busca ou filtro novo troca a função, e a lista volta à primeira página.
  const buscarCobrancas = useCallback(
    (pagina: number, tamanho: number, signal: AbortSignal) =>
      listarCobrancas(
        {
          pagina,
          tamanho,
          status: filtroStatus === 'TODAS' ? undefined : filtroStatus,
          nome: nomeBuscado || undefined,
          alunoId: alunoId ?? undefined,
        },
        signal,
      ),
    [filtroStatus, nomeBuscado, alunoId],
  )
  const { dados, paginacao, carregando, atualizando, erro, recarregar, topo } = useListaPaginada<PaginaCobrancas>(
    buscarCobrancas,
    'Não foi possível carregar as cobranças.',
  )

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
        window.setTimeout(recarregar, espera)
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
    recarregar()
  }

  /** Tira o ?aluno= e volta a listar as cobranças de todos os alunos. */
  function verTodosOsAlunos() {
    setSearchParams(
      (atuais) => {
        const proximos = new URLSearchParams(atuais)
        proximos.delete('aluno')
        return proximos
      },
      { replace: true },
    )
  }

  /** A seta do card abre os detalhes; clicar de novo fecha. Só um aberto por vez. */
  function alternar(id: string) {
    setIdAberto((atual) => (atual === id ? null : id))
    setMensagemSandbox(null)
  }

  const cobrancas = paginacao?.itensDaPagina ?? []

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
          // Filtrada por um aluno, a nova cobrança já abre com ele escolhido.
          onClick={() => navigate(alunoId ? `${rotas.novaCobranca}?aluno=${alunoId}` : rotas.novaCobranca)}
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
              onClick={() => setFiltroStatus(filtro)}
            >
              {ROTULOS_FILTRO[filtro]}
              <span className={styles.badge}>{contadores[filtro]}</span>
            </button>
          ))}
        </div>
      </div>

      {alunoId && (
        <div className={styles.filtroAluno}>
          <span>
            Só as cobranças de <strong>{alunoFiltrado.dados?.nome ?? 'um aluno'}</strong>
            {alunoFiltrado.dados && ` · Matrícula ${alunoFiltrado.dados.matricula}`}
          </span>
          <Button size="sm" icon="close" onClick={verTodosOsAlunos}>
            Ver de todos os alunos
          </Button>
        </div>
      )}

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
            <p>
              {alunoId
                ? 'Nenhuma cobrança deste aluno para este filtro.'
                : 'Nenhuma cobrança encontrada para este filtro.'}
            </p>
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

      {paginacao && !erro && (
        <Paginacao paginacao={paginacao} rotulo="Páginas das cobranças" carregando={atualizando} />
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
