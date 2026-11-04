import {
  cloneElement,
  useEffect,
  useId,
  useRef,
  useState,
  type FocusEvent,
  type PointerEvent,
  type ReactElement,
  type ReactNode,
} from 'react'
import { POPOVER, usePopoverFlutuante } from '../../hooks/usePopoverFlutuante'
import styles from './Dica.module.css'

type PropsDoAlvo = { 'aria-describedby'?: string; 'aria-label'?: string }

type DicaProps = {
  /** O que a dica diz; vazio (null, undefined, ''), o elemento fica sem dica. */
  texto: ReactNode
  /** Um elemento só: o botão, o ícone ou o rótulo que ganha a dica. */
  children: ReactElement<PropsDoAlvo>
  /** Classe do invólucro, quando ele precisa ocupar espaço no layout (ex.: a coluna do gráfico). */
  className?: string
  /** Só aparece se o texto do elemento estiver cortado (reticências): para rótulos que podem não caber. */
  soSeCortado?: boolean
  /**
   * Liga a dica ao elemento como descrição para o leitor de tela (padrão).
   * Desligue quando o elemento já diz o mesmo (ex.: barras com aria-label).
   */
  descrever?: boolean
}

/** Espera do mouse antes de abrir: passar por cima sem parar não acende dica. */
const ATRASO_MS = 350

function semTexto(texto: ReactNode): boolean {
  return texto === null || texto === undefined || texto === false || texto === ''
}

/**
 * Tooltip no visual do sistema, no lugar do `title` do navegador. Abre acima
 * do elemento (abaixo, se não couber), centralizado e com uma setinha; no
 * mouse depois de um instante, no foco pelo teclado na hora; fecha ao sair,
 * ao clicar ou com Esc. Funciona em botão desabilitado: o invólucro recebe o
 * mouse no lugar dele.
 */
export function Dica({ texto, children, className, soSeCortado = false, descrever = true }: DicaProps) {
  const [aberta, setAberta] = useState(false)
  const espera = useRef(0)
  const id = useId()
  const { ancoraRef, popoverRef, estilo, seta, portal } = usePopoverFlutuante<HTMLSpanElement, HTMLDivElement>(
    aberta,
    () => setAberta(false),
    { alturaMinimaAbaixo: 56, alinhamento: 'centro', lado: 'acima' },
  )
  const temTexto = !semTexto(texto)

  useEffect(() => () => window.clearTimeout(espera.current), [])

  useEffect(() => {
    if (!aberta) return
    function aoTeclar(event: KeyboardEvent) {
      if (event.key === 'Escape') setAberta(false)
    }
    document.addEventListener('keydown', aoTeclar)
    return () => document.removeEventListener('keydown', aoTeclar)
  }, [aberta])

  /** O texto do elemento passa da largura dele (está com reticências)? */
  function cortado(): boolean {
    const elemento = ancoraRef.current?.firstElementChild
    return elemento ? elemento.scrollWidth > elemento.clientWidth : false
  }

  function mostrar(depoisDe: number) {
    window.clearTimeout(espera.current)
    if (!temTexto || (soSeCortado && !cortado())) return
    espera.current = window.setTimeout(() => setAberta(true), depoisDe)
  }

  function esconder() {
    window.clearTimeout(espera.current)
    setAberta(false)
  }

  // No toque não há "passar por cima": o toque é o clique do elemento.
  function aoEntrar(event: PointerEvent) {
    if (event.pointerType !== 'touch') mostrar(ATRASO_MS)
  }

  // Só o foco do teclado abre: o foco de um clique não deve acender a dica.
  function aoFocar(event: FocusEvent) {
    if (event.target instanceof Element && event.target.matches(':focus-visible')) mostrar(0)
  }

  // Ícone cujo nome já é o texto da dica: o leitor de tela não precisa ouvir de novo.
  const repeteONome = typeof texto === 'string' && children.props['aria-label'] === texto
  const comDescricao = temTexto && descrever && !soSeCortado && !repeteONome
  const alvo = comDescricao
    ? cloneElement(children, {
        'aria-describedby': [children.props['aria-describedby'], id].filter(Boolean).join(' '),
      })
    : children

  return (
    // O invólucro existe sempre, com ou sem texto: a dica que aparece e some
    // (ex.: só com o botão desabilitado) não remonta o elemento nem tira o foco dele.
    <span
      ref={ancoraRef}
      className={[styles.alvo, className].filter(Boolean).join(' ')}
      onPointerEnter={aoEntrar}
      onPointerLeave={esconder}
      onPointerDown={esconder}
      onFocus={aoFocar}
      onBlur={esconder}
    >
      {alvo}
      {comDescricao && (
        <span id={id} className={styles.oculta}>
          {texto}
        </span>
      )}
      {portal(
        // aria-hidden: o leitor de tela já ouve o texto pela descrição ligada ao elemento.
        <div
          ref={popoverRef}
          {...POPOVER}
          role="tooltip"
          aria-hidden="true"
          className={`${styles.dica} ${seta.lado === 'acima' ? styles.acima : styles.abaixo}`}
          style={estilo}
        >
          <div className={styles.texto}>{texto}</div>
          <span className={styles.seta} style={{ left: seta.x }} />
        </div>,
      )}
    </span>
  )
}
