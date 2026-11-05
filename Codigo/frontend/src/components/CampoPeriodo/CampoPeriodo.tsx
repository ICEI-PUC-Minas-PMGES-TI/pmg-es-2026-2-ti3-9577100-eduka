import { useEffect, useId, useState, type KeyboardEvent } from 'react'
import { DayPicker, type DateRange } from 'react-day-picker'
import { ptBR } from 'react-day-picker/locale'
import 'react-day-picker/style.css'
import { POPOVER, usePopoverFlutuante } from '../../hooks/usePopoverFlutuante'
import { Button } from '../Button/Button'
import { DropdownCalendario } from '../CampoData/CampoData'
import estilosCalendario from '../CampoData/CampoData.module.css'
import { paraData, paraIso, rotuloDoPeriodo } from '../CampoData/datas'
import { Icon } from '../Icon/Icon'
import styles from './CampoPeriodo.module.css'

/** Período em ISO (AAAA-MM-DD), com as duas pontas. */
export type Periodo = { de: string; ate: string }

type CampoPeriodoProps = {
  /** Período aplicado; null quando ainda não há. */
  valor: Periodo | null
  onChange: (periodo: Periodo) => void
  /** Texto do botão sem período aplicado. */
  placeholder: string
  /** Última data que pode ser escolhida, em ISO. */
  max?: string
  /** Mês que o calendário abre sem período aplicado, em ISO; sem ele, o da data máxima. */
  mesInicial?: string
  /** Classe do botão: quem usa decide o visual (ex.: pílula de filtro). */
  className?: string
  'aria-pressed'?: boolean
}

/** Anos oferecidos para trás na seleção de ano. */
const ANOS_NA_LISTA = 10

/**
 * Botão que abre um calendário para escolher um intervalo de datas, no visual
 * do CampoData. O período só vale ao clicar em "Aplicar": escolher as pontas
 * não dispara nada sozinho.
 */
export function CampoPeriodo({
  valor,
  onChange,
  placeholder,
  max,
  mesInicial,
  className,
  'aria-pressed': ariaPressed,
}: CampoPeriodoProps) {
  const [aberto, setAberto] = useState(false)
  const [rascunho, setRascunho] = useState<DateRange | undefined>()
  const { ancoraRef, popoverRef, estilo, portal } = usePopoverFlutuante<HTMLButtonElement, HTMLDivElement>(
    aberto,
    () => setAberto(false),
    { alturaMinimaAbaixo: 400, alinhamento: 'auto' },
  )
  const dialogoId = useId()

  const maxima = max ? paraData(max) : undefined
  const ultimoMes = maxima ?? new Date()
  const primeiroMes = new Date(ultimoMes.getFullYear() - ANOS_NA_LISTA, 0, 1)

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

  function abrir() {
    // Reabre com o período aplicado, para ajustar uma ponta sem refazer a outra.
    setRascunho(valor ? { from: paraData(valor.de), to: paraData(valor.ate) } : undefined)
    setAberto(true)
  }

  function fechar() {
    setAberto(false)
    ancoraRef.current?.focus()
  }

  function aplicar() {
    if (!rascunho?.from) return
    onChange({ de: paraIso(rascunho.from), ate: paraIso(rascunho.to ?? rascunho.from) })
    fechar()
  }

  function handleTecla(event: KeyboardEvent<HTMLDivElement>) {
    if (event.key === 'Escape') {
      // Sem isso o Esc também fecharia o <dialog> de um modal.
      event.preventDefault()
      event.stopPropagation()
      fechar()
    }
  }

  const escolhido = rascunho?.from
    ? rotuloDoPeriodo({ de: paraIso(rascunho.from), ate: paraIso(rascunho.to ?? rascunho.from) })
    : null

  return (
    <>
      <button
        ref={ancoraRef}
        type="button"
        className={[styles.gatilho, className].filter(Boolean).join(' ')}
        aria-haspopup="dialog"
        aria-expanded={aberto}
        aria-controls={aberto ? dialogoId : undefined}
        aria-pressed={ariaPressed}
        onClick={() => (aberto ? setAberto(false) : abrir())}
      >
        <Icon name="calendar" size={15} />
        <span>{valor ? rotuloDoPeriodo(valor) : placeholder}</span>
      </button>

      {portal(
        <div
          ref={popoverRef}
          {...POPOVER}
          id={dialogoId}
          role="dialog"
          aria-label="Escolher período"
          className={estilosCalendario.popover}
          style={estilo}
          onKeyDown={handleTecla}
        >
          <DayPicker
            mode="range"
            locale={ptBR}
            autoFocus
            selected={rascunho}
            onSelect={setRascunho}
            defaultMonth={rascunho?.from ?? (mesInicial ? paraData(mesInicial) : undefined) ?? ultimoMes}
            disabled={maxima ? { after: maxima } : undefined}
            captionLayout="dropdown"
            startMonth={primeiroMes}
            endMonth={ultimoMes}
            showOutsideDays
            fixedWeeks
            className={`${estilosCalendario.calendario} ${styles.intervalo}`}
            components={{
              Chevron: ({ orientation }) => (
                <Icon name={orientation === 'left' ? 'chevronLeft' : 'chevronRight'} size={18} />
              ),
              Dropdown: DropdownCalendario,
            }}
          />
          <div className={styles.rodape}>
            <span className={escolhido ? styles.escolhido : styles.dica} aria-live="polite">
              {escolhido ?? 'Escolha o primeiro e o último dia.'}
            </span>
            <Button variant="primary" size="sm" disabled={!rascunho?.from} onClick={aplicar}>
              Aplicar
            </Button>
          </div>
        </div>
      )}
    </>
  )
}
