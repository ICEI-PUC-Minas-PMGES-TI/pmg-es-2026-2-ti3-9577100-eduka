import { useCallback, useEffect, useRef, useState } from 'react'
import { ApiError } from '../services/api'

/** Itens por página das listas, quando a tela não define outro valor. */
export const POR_PAGINA = 10

/** Quantos itens por página o usuário pode escolher. */
export const OPCOES_POR_PAGINA = [10, 20, 50]

type OpcoesPaginacao = {
  /** Itens por página ao abrir a tela; o usuário pode trocar. */
  porPagina?: number
  /**
   * Quando muda (busca, filtro, aba), a lista volta para a primeira página.
   * Sem ela, a página só é ajustada se a lista encolher. Use um valor
   * primitivo (ex.: `${busca}|${filtro}`): objeto novo a cada render
   * reiniciaria sempre.
   */
  reiniciarCom?: unknown
}

/** Estado da paginação de uma lista; vai inteiro para o componente Paginacao. */
export type EstadoPaginacao<T> = {
  itensDaPagina: T[]
  pagina: number
  totalPaginas: number
  /** Índice (base 0) do primeiro item da página na lista inteira. */
  inicio: number
  total: number
  irPara: (pagina: number) => void
  porPagina: number
  /** Troca a quantidade por página; sem ela, a Paginacao não oferece a escolha. */
  mudarPorPagina?: (porPagina: number) => void
}

/** Página (a partir de 1) onde fica o item `inicio` com `porPagina` itens em cada. */
function paginaDoItem(inicio: number, porPagina: number): number {
  return Math.floor(inicio / porPagina) + 1
}

/**
 * Paginação feita no front, sobre a lista já carregada. A página nunca passa
 * da última, mesmo depois de uma recarga encolher a lista. Trocar de página
 * rola até o elemento que recebe o `topo` (ref). Trocar a quantidade por
 * página mantém na tela o primeiro item que estava nela.
 */
export function usePaginacao<T, E extends HTMLElement = HTMLDivElement>(
  itens: T[],
  { porPagina: porPaginaInicial = POR_PAGINA, reiniciarCom }: OpcoesPaginacao = {},
) {
  const [estado, setEstado] = useState({ pagina: 1, chave: reiniciarCom })
  const [porPagina, setPorPagina] = useState(porPaginaInicial)
  const topo = useRef<E>(null)

  let pagina = estado.pagina
  if (!Object.is(estado.chave, reiniciarCom)) {
    // Ajuste durante a renderização, sem esperar um efeito: a lista nova já sai na primeira página.
    pagina = 1
    setEstado({ pagina: 1, chave: reiniciarCom })
  }

  const totalPaginas = Math.max(1, Math.ceil(itens.length / porPagina))
  const paginaAtual = Math.min(pagina, totalPaginas)
  const inicio = (paginaAtual - 1) * porPagina

  function irPara(nova: number) {
    setEstado((atual) => ({ ...atual, pagina: nova }))
    topo.current?.scrollIntoView({ behavior: 'smooth', block: 'start' })
  }

  function mudarPorPagina(novo: number) {
    setPorPagina(novo)
    setEstado((atual) => ({ ...atual, pagina: paginaDoItem(inicio, novo) }))
  }

  const paginacao: EstadoPaginacao<T> = {
    itensDaPagina: itens.slice(inicio, inicio + porPagina),
    pagina: paginaAtual,
    totalPaginas,
    inicio,
    total: itens.length,
    irPara,
    porPagina,
    mudarPorPagina,
  }
  // O ref sai separado: junto do estado, o lint trataria toda leitura do estado como leitura de ref.
  return { paginacao, topo }
}

/** Itens por página das listas dos painéis do Início. */
export const POR_PAGINA_PAINEL = 5

/**
 * Paginação de uma lista curta dentro de um painel do Início: 5 por página,
 * sem escolha de quantidade e sem rolar a tela ao trocar de página.
 */
export function usePaginasDoPainel<T>(itens: T[], reiniciarCom?: unknown): EstadoPaginacao<T> {
  return usePaginacao(itens, { porPagina: POR_PAGINA_PAINEL, reiniciarCom }).paginacao
}

/** Página que o backend já devolve cortada; `pagina` começa em 0. */
export type PaginaDoBackend<T> = {
  conteudo: T[]
  pagina: number
  tamanho: number
  totalElementos: number
  totalPaginas: number
}

/** Página com o resumo da escola (cards e contadores), como o PaginaComResumoResponse do backend. */
export type PaginaComResumo<T, R> = PaginaDoBackend<T> & { resumo: R }

/**
 * Pede ao backend a página `pagina` (a partir de 0) com `tamanho` itens.
 * Memorizada com useCallback sobre a busca e os filtros: trocar a função é
 * trocar de lista, e a lista volta para a primeira página.
 */
export type BuscarPagina<P> = (pagina: number, tamanho: number, signal: AbortSignal) => Promise<P>

/** Tipo dos itens de uma página. */
type ItemDa<P> = P extends PaginaDoBackend<infer T> ? T : never

/** Última resposta e a consulta que a produziu. */
type PaginaCarregada<P> = {
  origem: BuscarPagina<P>
  pagina: number
  tamanho: number
  versao: number
  dados: P | null
  erro: string | null
}

/**
 * Lista paginada no backend, para o componente Paginacao. Cuida do que toda
 * tela paginada precisa:
 *
 * - busca ou filtro novo (outra `buscar`) volta para a primeira página;
 * - consulta nova cancela a anterior, e resposta atrasada não sobrescreve;
 * - até a resposta chegar, a página anterior fica na tela (`atualizando`) e
 *   a navegação espera; "Carregando" só na primeira vez;
 * - se a lista encolheu e a página pedida ficou vazia, vai para a última;
 * - `recarregar` lê a mesma página de novo (depois de cadastrar ou editar).
 *
 * Trocar de página rola até o elemento que recebe o `topo` (ref).
 *
 * @param buscar       null enquanto não há o que buscar (ex.: nenhuma turma escolhida):
 *                     sem consulta, sem lista e sem "Carregando"
 * @param mensagemErro texto quando a falha não é do backend (rede, resposta inesperada)
 * @param mensagem404  texto no lugar do erro quando a API responde 404 (ex.: a turma deixou de ser do professor)
 */
export function useListaPaginada<P extends PaginaDoBackend<unknown>, E extends HTMLElement = HTMLDivElement>(
  buscar: BuscarPagina<P> | null,
  mensagemErro: string,
  mensagem404?: string,
) {
  const [estado, setEstado] = useState({ pagina: 0, origem: buscar })
  const [porPagina, setPorPagina] = useState(POR_PAGINA)
  const [versao, setVersao] = useState(0)
  const [carregada, setCarregada] = useState<PaginaCarregada<P> | null>(null)
  const topo = useRef<E>(null)

  let pagina = estado.pagina
  if (estado.origem !== buscar) {
    // Ajuste durante a renderização: a consulta nova já sai pedindo a primeira página.
    pagina = 0
    setEstado({ pagina: 0, origem: buscar })
  }

  useEffect(() => {
    if (!buscar) return
    const controller = new AbortController()
    buscar(pagina, porPagina, controller.signal)
      .then((dados) => {
        // Resposta que chegou depois de outra consulta começar não vale mais.
        if (controller.signal.aborted) return
        if (dados.conteudo.length === 0 && dados.pagina > 0) {
          setEstado((atual) =>
            atual.origem === buscar ? { ...atual, pagina: Math.max(0, dados.totalPaginas - 1) } : atual,
          )
          return
        }
        setCarregada({ origem: buscar, pagina, tamanho: porPagina, versao, dados, erro: null })
      })
      .catch((e: unknown) => {
        if (controller.signal.aborted) return
        const erro =
          e instanceof ApiError
            ? e.status === 404 && mensagem404
              ? mensagem404
              : (e.detalhe ?? e.message)
            : mensagemErro
        // A página anterior continua na tela, com o erro em cima.
        setCarregada((anterior) => ({
          origem: buscar,
          pagina,
          tamanho: porPagina,
          versao,
          dados: anterior?.dados ?? null,
          erro,
        }))
      })
    return () => controller.abort()
  }, [buscar, pagina, porPagina, versao, mensagemErro, mensagem404])

  const irPara = useCallback((nova: number) => {
    setEstado((atual) => ({ ...atual, pagina: nova - 1 }))
    topo.current?.scrollIntoView({ behavior: 'smooth', block: 'start' })
  }, [])

  /** Mantém na tela o primeiro item que estava nela. */
  const mudarPorPagina = useCallback(
    (novo: number) => {
      setEstado((atual) => ({ ...atual, pagina: paginaDoItem(pagina * porPagina, novo) - 1 }))
      setPorPagina(novo)
    },
    [pagina, porPagina],
  )

  const recarregar = useCallback(() => setVersao((v) => v + 1), [])

  const atual =
    carregada !== null &&
    carregada.origem === buscar &&
    carregada.pagina === pagina &&
    carregada.tamanho === porPagina &&
    carregada.versao === versao
  // Sem busca, a lista de uma busca anterior não vale.
  const dados = buscar ? (carregada?.dados ?? null) : null
  // Montado aqui, e não por uma função auxiliar: passar o irPara (que usa o ref) a uma função
  // durante a renderização o lint lê como acesso ao ref.
  const paginacao: EstadoPaginacao<ItemDa<P>> | null = dados
    ? {
        itensDaPagina: dados.conteudo as ItemDa<P>[],
        pagina: dados.pagina + 1,
        totalPaginas: Math.max(1, dados.totalPaginas),
        inicio: dados.pagina * dados.tamanho,
        total: dados.totalElementos,
        irPara,
        porPagina: dados.tamanho,
        mudarPorPagina,
      }
    : null

  return {
    /** Última página recebida; durante uma consulta nova, ainda a anterior. */
    dados,
    /** Estado para o componente Paginacao; null até a primeira página chegar. */
    paginacao,
    /** Nada chegou ainda: a tela mostra "Carregando". */
    carregando: buscar !== null && carregada === null,
    /** Outra página, busca ou filtro a caminho: a lista atual fica e a navegação espera. */
    atualizando: buscar !== null && carregada !== null && !atual,
    /** Falha da consulta atual. */
    erro: atual ? carregada.erro : null,
    recarregar,
    topo,
  }
}
