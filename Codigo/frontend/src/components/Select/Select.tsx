import {
  useEffect,
  useId,
  useLayoutEffect,
  useRef,
  useState,
  type CSSProperties,
  type KeyboardEvent,
} from 'react'
import { Icon } from '../Icon/Icon'
import styles from './Select.module.css'

export type OpcaoSelect<T extends string> = {
  valor: T
  rotulo: string
}

type SelectProps<T extends string> = {
  opcoes: OpcaoSelect<T>[]
  valor: T
  onChange: (valor: T) => void
  /** Texto exibido quando o valor atual não corresponde a nenhuma opção (ex.: ''). */
  placeholder?: string
  /** Classe do botão; o Campo passa a dele para ficar igual aos inputs. */
  className?: string
  /** Aplicada ao botão quando há uma opção escolhida. */
  classNamePreenchido?: string
  id?: string
  /** Mantido no botão para os formulários focarem o campo inválido por [name]. */
  name?: string
  required?: boolean
  'aria-label'?: string
  'aria-invalid'?: boolean
  'aria-describedby'?: string
}

const ALTURA_MAXIMA_MENU = 264
const ESPACO_MENU = 6

/**
 * Substitui o <select> nativo, cuja lista aberta é desenhada pelo sistema
 * operacional e não segue o visual da página. Segue o padrão "select-only
 * combobox" da WAI-ARIA: botão + listbox, com setas, Home/End, Enter, Esc e
 * busca pela primeira letra.
 */
export function Select<T extends string>({
  opcoes,
  valor,
  onChange,
  placeholder = 'Selecione…',
  className,
  classNamePreenchido,
  id,
  name,
  required,
  'aria-label': ariaLabel,
  'aria-invalid': ariaInvalid,
  'aria-describedby': ariaDescribedby,
}: SelectProps<T>) {
  const [aberto, setAberto] = useState(false)
  const [destacado, setDestacado] = useState(0)
  const [posicao, setPosicao] = useState<CSSProperties>({})
  const botaoRef = useRef<HTMLButtonElement>(null)
  const listaRef = useRef<HTMLUListElement>(null)
  const busca = useRef({ texto: '', timer: 0 })
  const idBase = useId()
  const listaId = `${idBase}-lista`

  const indiceAtual = opcoes.findIndex((o) => o.valor === valor)
  const opcaoAtual = indiceAtual >= 0 ? opcoes[indiceAtual] : null

  // Posição fixa, calculada a partir do botão: escapa do overflow de modais e
  // cartões, e abre para cima quando não há espaço embaixo.
  useLayoutEffect(() => {
    if (!aberto) return
    function posicionar() {
      const botao = botaoRef.current
      if (!botao) return
      const r = botao.getBoundingClientRect()
      const abaixo = window.innerHeight - r.bottom - ESPACO_MENU
      const acima = r.top - ESPACO_MENU
      const paraCima = abaixo < Math.min(ALTURA_MAXIMA_MENU, 160) && acima > abaixo
      const disponivel = Math.max(paraCima ? acima : abaixo, 120) - 8
      setPosicao({
        left: r.left,
        minWidth: r.width,
        maxHeight: Math.min(ALTURA_MAXIMA_MENU, disponivel),
        ...(paraCima
          ? { bottom: window.innerHeight - r.top + ESPACO_MENU }
          : { top: r.bottom + ESPACO_MENU }),
      })
    }
    posicionar()
    window.addEventListener('resize', posicionar)
    window.addEventListener('scroll', posicionar, true)
    return () => {
      window.removeEventListener('resize', posicionar)
      window.removeEventListener('scroll', posicionar, true)
    }
  }, [aberto])

  // Fecha ao clicar fora.
  useEffect(() => {
    if (!aberto) return
    function aoClicar(event: MouseEvent) {
      const alvo = event.target as Node
      if (!botaoRef.current?.contains(alvo) && !listaRef.current?.contains(alvo)) {
        setAberto(false)
      }
    }
    document.addEventListener('mousedown', aoClicar)
    return () => document.removeEventListener('mousedown', aoClicar)
  }, [aberto])

  useEffect(() => {
    if (aberto) listaRef.current?.focus({ preventScroll: true })
  }, [aberto])

  // Mantém a opção destacada visível em listas com rolagem (ex.: UFs).
  useEffect(() => {
    if (!aberto) return
    document.getElementById(`${idBase}-opcao-${destacado}`)?.scrollIntoView({ block: 'nearest' })
  }, [aberto, destacado, idBase])

  useEffect(() => () => window.clearTimeout(busca.current.timer), [])

  function abrir() {
    setDestacado(Math.max(indiceAtual, 0))
    setAberto(true)
  }

  function fechar() {
    setAberto(false)
    botaoRef.current?.focus()
  }

  function escolher(indice: number) {
    onChange(opcoes[indice].valor)
    fechar()
  }

  /** Digitar letras pula para a primeira opção que começa com elas. */
  function buscarPorTexto(tecla: string): number | null {
    window.clearTimeout(busca.current.timer)
    busca.current.texto += tecla.toLowerCase()
    busca.current.timer = window.setTimeout(() => (busca.current.texto = ''), 500)
    const indice = opcoes.findIndex((o) => o.rotulo.toLowerCase().startsWith(busca.current.texto))
    return indice >= 0 ? indice : null
  }

  function handleTeclaBotao(event: KeyboardEvent<HTMLButtonElement>) {
    if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
      event.preventDefault()
      abrir()
    } else if (event.key.length === 1 && event.key !== ' ' && !event.ctrlKey && !event.metaKey) {
      const indice = buscarPorTexto(event.key)
      if (indice !== null) onChange(opcoes[indice].valor)
    }
  }

  function handleTeclaLista(event: KeyboardEvent<HTMLUListElement>) {
    switch (event.key) {
      case 'ArrowDown':
        event.preventDefault()
        setDestacado((i) => Math.min(i + 1, opcoes.length - 1))
        break
      case 'ArrowUp':
        event.preventDefault()
        setDestacado((i) => Math.max(i - 1, 0))
        break
      case 'Home':
        event.preventDefault()
        setDestacado(0)
        break
      case 'End':
        event.preventDefault()
        setDestacado(opcoes.length - 1)
        break
      case 'Enter':
      case ' ':
        event.preventDefault()
        escolher(destacado)
        break
      case 'Escape':
        // Sem isso o Esc também fecharia o <dialog> do modal.
        event.preventDefault()
        event.stopPropagation()
        fechar()
        break
      case 'Tab':
        setAberto(false)
        break
      default:
        if (event.key.length === 1 && !event.ctrlKey && !event.metaKey) {
          const indice = buscarPorTexto(event.key)
          if (indice !== null) setDestacado(indice)
        }
    }
  }

  const classesBotao = [styles.gatilho, className, opcaoAtual && classNamePreenchido]
    .filter(Boolean)
    .join(' ')

  return (
    <>
      <button
        ref={botaoRef}
        type="button"
        id={id}
        name={name}
        className={classesBotao}
        role="combobox"
        aria-haspopup="listbox"
        aria-expanded={aberto}
        aria-controls={aberto ? listaId : undefined}
        aria-label={ariaLabel}
        aria-required={required || undefined}
        aria-invalid={ariaInvalid || undefined}
        aria-describedby={ariaDescribedby}
        onClick={() => (aberto ? setAberto(false) : abrir())}
        onKeyDown={handleTeclaBotao}
      >
        <span className={opcaoAtual ? styles.valor : styles.placeholder}>
          {opcaoAtual?.rotulo ?? placeholder}
        </span>
        <Icon
          name="chevronDown"
          size={16}
          className={`${styles.seta} ${aberto ? styles.setaAberta : ''}`}
        />
      </button>

      {aberto && (
        <ul
          ref={listaRef}
          id={listaId}
          className={styles.menu}
          style={posicao}
          role="listbox"
          tabIndex={-1}
          aria-activedescendant={`${idBase}-opcao-${destacado}`}
          onKeyDown={handleTeclaLista}
        >
          {opcoes.map((opcao, indice) => {
            const selecionada = opcao.valor === valor
            return (
              <li
                key={opcao.valor}
                id={`${idBase}-opcao-${indice}`}
                role="option"
                aria-selected={selecionada}
                className={[
                  styles.opcao,
                  indice === destacado && styles.opcaoDestacada,
                  selecionada && styles.opcaoSelecionada,
                ]
                  .filter(Boolean)
                  .join(' ')}
                onMouseEnter={() => setDestacado(indice)}
                // Evita que o botão perca o foco antes do clique.
                onMouseDown={(e) => e.preventDefault()}
                onClick={() => escolher(indice)}
              >
                <span>{opcao.rotulo}</span>
                {selecionada && <Icon name="check" size={14} />}
              </li>
            )
          })}
        </ul>
      )}
    </>
  )
}
