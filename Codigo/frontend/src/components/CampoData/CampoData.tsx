import { useEffect, useId, useState, type ChangeEvent, type KeyboardEvent } from 'react'
import { DayPicker, type DropdownProps } from 'react-day-picker'
import { ptBR } from 'react-day-picker/locale'
import 'react-day-picker/style.css'
import { POPOVER, usePopoverFlutuante } from '../../hooks/usePopoverFlutuante'
import { Icon } from '../Icon/Icon'
import { Select } from '../Select/Select'
import styles from './CampoData.module.css'
import { formatarIso as formatar, paraData, paraIso } from './datas'

type CampoDataProps = {
  /** Data no formato ISO (AAAA-MM-DD), ou '' quando vazia. */
  valor: string
  /** Dispensável quando somenteLeitura. */
  onChange?: (valor: string) => void
  /** Primeira data permitida, em ISO. */
  min?: string
  /** Última data permitida, em ISO. */
  max?: string
  /** Mês em que o calendário abre quando vazio, em ISO; sem ele, abre no mês de min. */
  mesInicial?: string
  /** Só exibe a data, sem abrir o calendário (ex.: início da vigência). */
  somenteLeitura?: boolean
  placeholder?: string
  /** Classe do campo; o Campo passa a dele para ficar igual aos inputs. */
  className?: string
  id?: string
  /** Mantido para os formulários focarem o campo inválido por [name]. */
  name?: string
  required?: boolean
  'aria-invalid'?: boolean
  'aria-describedby'?: string
}

/** Anos oferecidos antes e depois da data de referência na seleção de ano. */
const ANOS_NA_LISTA = 10

/**
 * Seleção de mês e ano do calendário com o nosso Select, no lugar do <select>
 * nativo da biblioteca. O DayPicker só lê `event.target.value` no onChange.
 */
export function DropdownCalendario({ options = [], value, onChange, 'aria-label': ariaLabel }: DropdownProps) {
  return (
    <Select
      className={styles.dropdown}
      aria-label={ariaLabel}
      opcoes={options
        .filter((opcao) => !opcao.disabled)
        // Os meses vêm em minúsculas ("janeiro").
        .map((opcao) => ({ valor: String(opcao.value), rotulo: opcao.label.charAt(0).toUpperCase() + opcao.label.slice(1) }))}
      valor={String(value)}
      onChange={(valor) => onChange?.({ target: { value: valor } } as ChangeEvent<HTMLSelectElement>)}
    />
  )
}

/**
 * Campo de data com calendário no visual da página, no lugar do
 * <input type="date">, cujo calendário é desenhado pelo navegador.
 */
export function CampoData({
  valor,
  onChange,
  min,
  max,
  mesInicial,
  somenteLeitura = false,
  placeholder = 'dd/mm/aaaa',
  className,
  id,
  name,
  required,
  'aria-invalid': ariaInvalid,
  'aria-describedby': ariaDescribedby,
}: CampoDataProps) {
  const [aberto, setAberto] = useState(false)
  const { ancoraRef, popoverRef, estilo, portal } = usePopoverFlutuante<HTMLButtonElement, HTMLDivElement>(
    aberto,
    () => setAberto(false),
    { alturaMinimaAbaixo: 360, alinhamento: 'auto' },
  )
  const dialogoId = useId()

  const selecionada = paraData(valor)
  const minima = min ? paraData(min) : undefined
  const maxima = max ? paraData(max) : undefined
  // Faixa da seleção de ano: da data mínima (ou 10 anos antes) até 10 anos
  // depois da data escolhida ou de hoje, o que for mais tarde.
  const hoje = new Date()
  const referencia = selecionada && selecionada > hoje ? selecionada : hoje
  const primeiroMes = minima ?? new Date(referencia.getFullYear() - ANOS_NA_LISTA, 0, 1)
  const ultimoMes = maxima ?? new Date(referencia.getFullYear() + ANOS_NA_LISTA, 11, 1)

  // Fecha ao sair do calendário com Tab.
  useEffect(() => {
    if (!aberto) return
    const popover = popoverRef.current
    function aoSairDoFoco(event: FocusEvent) {
      const destino = event.relatedTarget as Node | null
      if (destino && !popover?.contains(destino) && !ancoraRef.current?.contains(destino)) {
        setAberto(false)
      }
    }
    popover?.addEventListener('focusout', aoSairDoFoco)
    return () => popover?.removeEventListener('focusout', aoSairDoFoco)
  }, [aberto, ancoraRef, popoverRef])

  if (somenteLeitura) {
    return (
      <input
        id={id}
        name={name}
        className={className}
        readOnly
        tabIndex={-1}
        value={formatar(valor)}
        placeholder="—"
        aria-describedby={ariaDescribedby}
      />
    )
  }

  function fechar() {
    setAberto(false)
    ancoraRef.current?.focus()
  }

  function handleTecla(event: KeyboardEvent<HTMLDivElement>) {
    if (event.key === 'Escape') {
      // Sem isso o Esc também fecharia o <dialog> de um modal.
      event.preventDefault()
      event.stopPropagation()
      fechar()
    }
  }

  return (
    <>
      <button
        ref={ancoraRef}
        type="button"
        id={id}
        name={name}
        className={[styles.gatilho, className].filter(Boolean).join(' ')}
        aria-haspopup="dialog"
        aria-expanded={aberto}
        aria-controls={aberto ? dialogoId : undefined}
        aria-required={required || undefined}
        aria-invalid={ariaInvalid || undefined}
        aria-describedby={ariaDescribedby}
        onClick={() => setAberto((a) => !a)}
      >
        <span className={valor ? undefined : styles.placeholder}>{valor ? formatar(valor) : placeholder}</span>
        <Icon name="calendar" size={16} className={styles.icone} />
      </button>

      {portal(
        <div
          ref={popoverRef}
          {...POPOVER}
          id={dialogoId}
          role="dialog"
          aria-label="Escolher data"
          className={styles.popover}
          style={estilo}
          onKeyDown={handleTecla}
        >
          <DayPicker
            mode="single"
            locale={ptBR}
            autoFocus
            selected={selecionada}
            defaultMonth={selecionada ?? (mesInicial ? paraData(mesInicial) : minima)}
            disabled={[...(minima ? [{ before: minima }] : []), ...(maxima ? [{ after: maxima }] : [])]}
            captionLayout="dropdown"
            startMonth={primeiroMes}
            endMonth={ultimoMes}
            showOutsideDays
            fixedWeeks
            className={styles.calendario}
            components={{
              Chevron: ({ orientation }) => (
                <Icon name={orientation === 'left' ? 'chevronLeft' : 'chevronRight'} size={18} />
              ),
              Dropdown: DropdownCalendario,
            }}
            onSelect={(data) => {
              if (data) onChange?.(paraIso(data))
              fechar()
            }}
          />
        </div>
      )}
    </>
  )
}
