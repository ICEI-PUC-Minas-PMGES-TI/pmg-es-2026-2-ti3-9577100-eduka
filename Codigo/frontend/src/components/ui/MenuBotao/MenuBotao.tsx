import { useEffect, useId, useState, type KeyboardEvent } from 'react'
import { POPOVER, usePopoverFlutuante } from '../../../hooks/usePopoverFlutuante'
import { Button } from '../Button/Button'
import { Icon, type IconName } from '../Icon/Icon'
import styles from './MenuBotao.module.css'

export type ItemMenu = {
  id: string
  rotulo: string
  /** Uma linha curta embaixo do rótulo, dizendo para que serve. */
  descricao?: string
  icone?: IconName
  /** Ação destrutiva (ex.: excluir): em vermelho, para não ser escolhida por engano. */
  perigo?: boolean
  onSelecionar: () => void
}

type MenuBotaoProps = {
  /**
   * Texto do botão; a setinha ao lado indica que abre um menu. Com
   * `somenteIcone`, não aparece: vira o nome acessível do botão e do menu.
   */
  children: string
  itens: ItemMenu[]
  icon?: IconName
  variant?: 'primary' | 'outline'
  size?: 'sm' | 'md'
  /**
   * Só o ícone, sem texto nem setinha (ex.: "⋯" com as ações raras de uma
   * linha da tabela). O texto passa a ser lido só pelo leitor de tela.
   */
  somenteIcone?: boolean
  disabled?: boolean
}

/**
 * Botão que abre uma lista de ações (ex.: "Exportar" → CSV ou planilha), no
 * mesmo vidro das listas do Select. Segue o padrão "menu button" da WAI-ARIA:
 * Enter, Espaço ou seta para baixo abrem já na primeira ação; setas, Home e
 * End andam; Enter escolhe; Esc e Tab fecham. Abre pela borda direita quando
 * o botão está na metade direita da tela, para não sair do cabeçalho.
 */
export function MenuBotao({
  children,
  itens,
  icon,
  variant = 'outline',
  size = 'md',
  somenteIcone = false,
  disabled,
}: MenuBotaoProps) {
  const [abertoPedido, setAberto] = useState(false)
  // Desabilitado com o menu aberto (ex.: a ação começou a gerar o arquivo), o menu some.
  const aberto = abertoPedido && !disabled
  const [destacado, setDestacado] = useState(0)
  const { ancoraRef, popoverRef, estilo, portal } = usePopoverFlutuante<HTMLButtonElement, HTMLUListElement>(
    aberto,
    () => setAberto(false),
    { alturaMinimaAbaixo: 160, alinhamento: 'auto' },
  )
  const idBase = useId()
  const menuId = `${idBase}-menu`

  useEffect(() => {
    if (aberto) popoverRef.current?.focus({ preventScroll: true })
  }, [aberto, popoverRef])

  function abrir(indice = 0) {
    setDestacado(indice)
    setAberto(true)
  }

  function fechar() {
    setAberto(false)
    ancoraRef.current?.focus()
  }

  function escolher(indice: number) {
    fechar()
    itens[indice].onSelecionar()
  }

  function handleTeclaBotao(event: KeyboardEvent<HTMLButtonElement>) {
    if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
      event.preventDefault()
      abrir(event.key === 'ArrowUp' ? itens.length - 1 : 0)
    }
  }

  function handleTeclaMenu(event: KeyboardEvent<HTMLUListElement>) {
    switch (event.key) {
      case 'ArrowDown':
        event.preventDefault()
        setDestacado((i) => (i + 1) % itens.length)
        break
      case 'ArrowUp':
        event.preventDefault()
        setDestacado((i) => (i - 1 + itens.length) % itens.length)
        break
      case 'Home':
        event.preventDefault()
        setDestacado(0)
        break
      case 'End':
        event.preventDefault()
        setDestacado(itens.length - 1)
        break
      case 'Enter':
      case ' ':
        event.preventDefault()
        escolher(destacado)
        break
      case 'Escape':
        // Sem isso o Esc também fecharia o <dialog> de um modal em volta.
        event.preventDefault()
        event.stopPropagation()
        fechar()
        break
      case 'Tab':
        setAberto(false)
        break
    }
  }

  return (
    <>
      <Button
        ref={ancoraRef}
        variant={variant}
        size={size}
        icon={icon}
        disabled={disabled}
        aria-label={somenteIcone ? children : undefined}
        aria-haspopup="menu"
        aria-expanded={aberto}
        aria-controls={aberto ? menuId : undefined}
        onClick={() => (aberto ? setAberto(false) : abrir())}
        onKeyDown={handleTeclaBotao}
      >
        {somenteIcone ? null : (
          <>
            {children}
            <Icon name="chevronDown" size={16} className={`${styles.seta} ${aberto ? styles.setaAberta : ''}`} />
          </>
        )}
      </Button>

      {portal(
        <ul
          ref={popoverRef}
          {...POPOVER}
          id={menuId}
          role="menu"
          aria-label={children}
          tabIndex={-1}
          aria-activedescendant={`${idBase}-item-${destacado}`}
          className={styles.menu}
          style={estilo}
          onKeyDown={handleTeclaMenu}
        >
          {itens.map((item, indice) => (
            <li
              key={item.id}
              id={`${idBase}-item-${indice}`}
              role="menuitem"
              className={[styles.item, indice === destacado && styles.itemDestacado, item.perigo && styles.itemPerigo]
                .filter(Boolean)
                .join(' ')}
              onMouseEnter={() => setDestacado(indice)}
              // Evita que o menu perca o foco antes do clique.
              onMouseDown={(e) => e.preventDefault()}
              onClick={() => escolher(indice)}
            >
              {item.icone && (
                <span className={styles.icone} aria-hidden="true">
                  <Icon name={item.icone} size={18} />
                </span>
              )}
              <span className={styles.texto}>
                <strong>{item.rotulo}</strong>
                {item.descricao && <span>{item.descricao}</span>}
              </span>
            </li>
          ))}
        </ul>,
      )}
    </>
  )
}
