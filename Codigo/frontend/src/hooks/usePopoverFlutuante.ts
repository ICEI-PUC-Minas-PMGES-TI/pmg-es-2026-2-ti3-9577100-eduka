import { useEffect, useLayoutEffect, useRef, useState, type CSSProperties } from 'react'

const ESPACO = 6
const MARGEM_TELA = 8

type Opcoes = {
  /** Espaço mínimo abaixo da âncora; com menos que isso (e mais espaço acima), abre para cima. */
  alturaMinimaAbaixo: number
  /** Limita a altura do popover (listas com rolagem). Sem ele, o popover usa a altura natural. */
  alturaMaxima?: number
}

/**
 * Posiciona um popover (lista do Select, calendário do CampoData) em
 * `position: fixed` a partir do elemento âncora — assim ele escapa do overflow
 * de modais e cartões — e fecha ao clicar fora dos dois.
 */
export function usePopoverFlutuante<A extends HTMLElement, P extends HTMLElement>(
  aberto: boolean,
  onFechar: () => void,
  { alturaMinimaAbaixo, alturaMaxima }: Opcoes,
) {
  const ancoraRef = useRef<A>(null)
  const popoverRef = useRef<P>(null)
  const [estilo, setEstilo] = useState<CSSProperties>({})
  const onFecharRef = useRef(onFechar)

  useEffect(() => {
    onFecharRef.current = onFechar
  })

  useLayoutEffect(() => {
    if (!aberto) return
    function posicionar() {
      const ancora = ancoraRef.current
      if (!ancora) return
      const r = ancora.getBoundingClientRect()
      const abaixo = window.innerHeight - r.bottom - ESPACO
      const acima = r.top - ESPACO
      const paraCima = abaixo < alturaMinimaAbaixo && acima > abaixo
      const disponivel = Math.max(paraCima ? acima : abaixo, 120) - MARGEM_TELA
      setEstilo({
        left: r.left,
        minWidth: r.width,
        ...(alturaMaxima !== undefined && { maxHeight: Math.min(alturaMaxima, disponivel) }),
        ...(paraCima ? { bottom: window.innerHeight - r.top + ESPACO } : { top: r.bottom + ESPACO }),
      })
    }
    posicionar()
    window.addEventListener('resize', posicionar)
    window.addEventListener('scroll', posicionar, true)
    return () => {
      window.removeEventListener('resize', posicionar)
      window.removeEventListener('scroll', posicionar, true)
    }
  }, [aberto, alturaMinimaAbaixo, alturaMaxima])

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

  return { ancoraRef, popoverRef, estilo }
}
