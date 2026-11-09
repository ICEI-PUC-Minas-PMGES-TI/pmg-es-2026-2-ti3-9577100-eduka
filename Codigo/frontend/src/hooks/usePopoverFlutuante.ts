import { useEffect, useLayoutEffect, useRef, useState, type CSSProperties, type ReactNode } from 'react'
import { createPortal } from 'react-dom'

/** Marca a raiz de um popover; espalhe no elemento: `<div {...POPOVER}>`. */
export const POPOVER = { 'data-popover': '' }

const ESPACO = 6
const MARGEM_TELA = 8

type Opcoes = {
  /**
   * Espaço mínimo do lado preferido (abaixo, ou acima com `lado: 'acima'`);
   * com menos que isso, e mais espaço do outro lado, abre do outro lado.
   */
  alturaMinimaAbaixo: number
  /** Limita a altura do popover (listas com rolagem). Sem ele, o popover usa a altura natural. */
  alturaMaxima?: number
  /**
   * 'inicio' (padrão): pela borda esquerda do campo, como uma lista de opções.
   * 'auto': popover bem mais largo que o campo (o calendário), com o campo na
   * metade direita da tela, alinha pela borda direita, para não avançar além
   * do cartão em que o campo está.
   * 'centro': centralizado no campo, sem a largura mínima dele (a dica).
   */
  alinhamento?: 'inicio' | 'auto' | 'centro'
  /** De que lado abre quando cabe dos dois: 'abaixo' (padrão) ou 'acima' (a dica). */
  lado?: 'abaixo' | 'acima'
}

/** De que lado do campo o popover ficou e onde, nele, cai o centro do campo (para a setinha). */
export type Seta = { lado: 'abaixo' | 'acima'; x: number }

/**
 * Posiciona um popover (lista do Select, calendário do CampoData) em
 * `position: fixed` a partir do elemento âncora — assim ele escapa do overflow
 * de modais e cartões — e fecha ao clicar fora dos dois.
 *
 * O popover precisa sair pelo `portal`: dentro de um cartão de vidro, o
 * `backdrop-filter` vira a referência do `position: fixed` e o popover abriria
 * fora do lugar. O destino, do mais próximo ao mais distante da âncora:
 * - outro popover (ex.: o mês e o ano dentro do calendário): ficar dentro dele
 *   mantém o clique e o foco "dentro", e ele não fecha sozinho;
 * - o <dialog> aberto (no <body>, ficaria atrás do modal, na camada do topo);
 * - o <body>.
 *
 * A raiz de todo popover leva o atributo `data-popover` (POPOVER), para os
 * popovers de dentro o acharem já ao montar.
 */
export function usePopoverFlutuante<A extends HTMLElement, P extends HTMLElement>(
  aberto: boolean,
  onFechar: () => void,
  { alturaMinimaAbaixo, alturaMaxima, alinhamento = 'inicio', lado = 'abaixo' }: Opcoes,
) {
  const ancoraRef = useRef<A>(null)
  const popoverRef = useRef<P>(null)
  const [estilo, setEstilo] = useState<CSSProperties>({})
  const [seta, setSeta] = useState<Seta>({ lado, x: 0 })
  const [destino, setDestino] = useState<HTMLElement | null>(null)
  const onFecharRef = useRef(onFechar)

  useEffect(() => {
    onFecharRef.current = onFechar
  })

  // Uma vez, ao montar: o <dialog> em volta do campo não muda. Assim, ao abrir,
  // o popover já nasce no destino no mesmo render, e o foco e a medida de
  // largura de quem usa encontram ele montado.
  useLayoutEffect(() => {
    setDestino(ancoraRef.current?.closest<HTMLElement>('[data-popover], dialog') ?? document.body)
  }, [])

  useLayoutEffect(() => {
    if (!aberto || !destino) return
    function posicionar() {
      const ancora = ancoraRef.current
      if (!ancora) return
      const r = ancora.getBoundingClientRect()
      const abaixo = window.innerHeight - r.bottom - ESPACO
      const acima = r.top - ESPACO
      const paraCima =
        lado === 'acima'
          ? !(acima < alturaMinimaAbaixo && abaixo > acima)
          : abaixo < alturaMinimaAbaixo && acima > abaixo
      const disponivel = Math.max(paraCima ? acima : abaixo, 120) - MARGEM_TELA
      const centralizado = alinhamento === 'centro'
      const popoverLargura = popoverRef.current?.offsetWidth ?? 0
      const largura = centralizado ? popoverLargura : Math.max(popoverLargura, r.width)
      const pelaDireita =
        alinhamento === 'auto' && largura > r.width && r.left + r.width / 2 > window.innerWidth / 2
      const esquerda = centralizado ? r.left + (r.width - largura) / 2 : pelaDireita ? r.right - largura : r.left
      // Em qualquer alinhamento, nunca sai da tela: desliza até caber.
      const limiteDireita = window.innerWidth - largura - MARGEM_TELA
      const left = Math.max(MARGEM_TELA, Math.min(esquerda, limiteDireita))
      setSeta({ lado: paraCima ? 'acima' : 'abaixo', x: r.left + r.width / 2 - left })
      setEstilo({
        left,
        ...(!centralizado && { minWidth: r.width }),
        maxWidth: window.innerWidth - 2 * MARGEM_TELA,
        ...(alturaMaxima !== undefined && { maxHeight: Math.min(alturaMaxima, disponivel) }),
        ...(paraCima ? { bottom: window.innerHeight - r.top + ESPACO } : { top: r.bottom + ESPACO }),
      })
    }
    posicionar()
    window.addEventListener('resize', posicionar)
    window.addEventListener('scroll', posicionar, true)
    // Aberto durante a entrada animada de um modal (escala), o campo ainda não
    // estava no tamanho final: reposiciona quando a animação termina.
    document.addEventListener('animationend', posicionar, true)
    return () => {
      window.removeEventListener('resize', posicionar)
      window.removeEventListener('scroll', posicionar, true)
      document.removeEventListener('animationend', posicionar, true)
    }
  }, [aberto, destino, alturaMinimaAbaixo, alturaMaxima, alinhamento, lado])

  useEffect(() => {
    if (!aberto) return
    function aoClicar(event: MouseEvent) {
      const alvo = event.target as Node
      if (!ancoraRef.current?.contains(alvo) && !popoverRef.current?.contains(alvo)) {
        onFecharRef.current()
      }
    }
    document.addEventListener('mousedown', aoClicar)
    return () => document.removeEventListener('mousedown', aoClicar)
  }, [aberto])

  /** Desenha o popover no destino; nada enquanto fechado. */
  function portal(popover: ReactNode): ReactNode {
    return aberto && destino ? createPortal(popover, destino) : null
  }

  return { ancoraRef, popoverRef, estilo, seta, portal }
}
