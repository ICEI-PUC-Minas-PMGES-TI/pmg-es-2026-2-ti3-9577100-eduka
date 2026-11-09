import { useCallback, useRef, useState } from 'react'

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

/**
 * Página pedida ao backend (a partir de 0) e quantos itens por página.
 * `irPara` recebe a página como a Paginacao mostra, a partir de 1, e rola até
 * o elemento que recebe o `topo`. Busca ou filtro novo volta com `setPagina(0)`.
 */
export function usePaginaDoBackend<E extends HTMLElement = HTMLDivElement>() {
  const [pagina, setPagina] = useState(0)
  const [porPagina, setPorPagina] = useState(POR_PAGINA)
  const topo = useRef<E>(null)

  const irPara = useCallback((nova: number) => {
    setPagina(nova - 1)
    topo.current?.scrollIntoView({ behavior: 'smooth', block: 'start' })
  }, [])

  /** Mantém na tela o primeiro item que estava nela. */
  const mudarPorPagina = useCallback(
    (novo: number) => {
      setPagina(paginaDoItem(pagina * porPagina, novo) - 1)
      setPorPagina(novo)
    },
    [pagina, porPagina],
  )

  return { pagina, setPagina, porPagina, irPara, mudarPorPagina, topo }
}

/** Página que o backend já devolve cortada; `pagina` começa em 0. */
export type PaginaDoBackend<T> = {
  conteudo: T[]
  pagina: number
  tamanho: number
  totalElementos: number
  totalPaginas: number
}

/** Estado do componente Paginacao para uma lista paginada no backend; os controles vêm do usePaginaDoBackend. */
export function paginacaoDoBackend<T>(
  pagina: PaginaDoBackend<T>,
  { irPara, mudarPorPagina }: Pick<EstadoPaginacao<T>, 'irPara' | 'mudarPorPagina'>,
): EstadoPaginacao<T> {
  return {
    itensDaPagina: pagina.conteudo,
    pagina: pagina.pagina + 1,
    totalPaginas: Math.max(1, pagina.totalPaginas),
    inicio: pagina.pagina * pagina.tamanho,
    total: pagina.totalElementos,
    irPara,
    porPagina: pagina.tamanho,
    mudarPorPagina,
  }
}
