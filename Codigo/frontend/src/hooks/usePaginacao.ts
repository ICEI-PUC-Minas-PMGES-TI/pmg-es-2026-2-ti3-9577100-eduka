import { useRef, useState } from 'react'

/** Itens por página das listas, quando a tela não define outro valor. */
export const POR_PAGINA = 10

type OpcoesPaginacao = {
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
}

/**
 * Paginação feita no front, sobre a lista já carregada. A página nunca passa
 * da última, mesmo depois de uma recarga encolher a lista. Trocar de página
 * rola até o elemento que recebe o `topo` (ref).
 */
export function usePaginacao<T, E extends HTMLElement = HTMLDivElement>(
  itens: T[],
  { porPagina = POR_PAGINA, reiniciarCom }: OpcoesPaginacao = {},
) {
  const [estado, setEstado] = useState({ pagina: 1, chave: reiniciarCom })
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

  const paginacao: EstadoPaginacao<T> = {
    itensDaPagina: itens.slice(inicio, inicio + porPagina),
    pagina: paginaAtual,
    totalPaginas,
    inicio,
    total: itens.length,
    irPara,
  }
  // O ref sai separado: junto do estado, o lint trataria toda leitura do estado como leitura de ref.
  return { paginacao, topo }
}
