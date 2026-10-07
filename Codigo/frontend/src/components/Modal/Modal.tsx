import { useEffect, useId, useRef, type FormEvent, type ReactNode, type RefObject } from 'react'
import { Icon } from '../Icon/Icon'
import styles from './Modal.module.css'

const LARGURAS = { pequeno: styles.pequeno, medio: styles.medio, grande: styles.grande }

type ModalProps = {
  titulo: string
  /** Linha abaixo do título (ex.: código, situação). */
  subtitulo?: ReactNode
  /** pequeno: confirmações; medio: cadastros curtos; grande: formulários longos e detalhes. */
  tamanho?: keyof typeof LARGURAS
  /** Enquanto salva: o X e o Esc não fecham. */
  ocupado?: boolean
  onFechar: () => void
  /** Com onEnviar, o conteúdo vira um <form> e o botão submit do rodapé envia. */
  onEnviar?: (evento: FormEvent<HTMLFormElement>) => void
  /** Botões do rodapé, da esquerda para a direita (o principal por último). */
  rodape: ReactNode
  /** Para o formulário achar e focar o primeiro campo inválido. */
  dialogRef?: RefObject<HTMLDialogElement | null>
  children: ReactNode
}

/**
 * Casca de vidro de todos os diálogos: título, botão de fechar, corpo com
 * rolagem própria e rodapé. Montado só enquanto está aberto, então cada
 * abertura começa limpa.
 */
export function Modal({
  titulo,
  subtitulo,
  tamanho = 'medio',
  ocupado = false,
  onFechar,
  onEnviar,
  rodape,
  dialogRef,
  children,
}: ModalProps) {
  const refInterno = useRef<HTMLDialogElement>(null)
  const ref = dialogRef ?? refInterno
  const tituloId = useId()

  useEffect(() => {
    const dialog = ref.current
    dialog?.showModal()
    return () => dialog?.close()
  }, [ref])

  function fechar() {
    if (!ocupado) onFechar()
  }

  const conteudo = (
    <>
      <header className={styles.header}>
        <div className={styles.cabecalho}>
          <h2 id={tituloId} className={styles.titulo}>
            {titulo}
          </h2>
          {subtitulo && <div className={styles.subtitulo}>{subtitulo}</div>}
        </div>
        <button type="button" className={styles.fechar} aria-label="Fechar" disabled={ocupado} onClick={fechar}>
          <Icon name="close" size={16} />
        </button>
      </header>

      <div className={styles.corpo}>{children}</div>

      <footer className={styles.rodape}>{rodape}</footer>
    </>
  )

  return (
    <dialog
      ref={ref}
      className={`${styles.dialog} ${LARGURAS[tamanho]}`}
      aria-labelledby={tituloId}
      onCancel={(e) => {
        e.preventDefault()
        fechar()
      }}
    >
      {onEnviar ? (
        <form className={styles.conteudo} noValidate onSubmit={onEnviar}>
          {conteudo}
        </form>
      ) : (
        <div className={styles.conteudo}>{conteudo}</div>
      )}
    </dialog>
  )
}

/** Texto discreto no corpo do diálogo (ex.: "Carregando…"). */
export function ModalAviso({ children }: { children: ReactNode }) {
  return <p className={styles.aviso}>{children}</p>
}

/** Erro de carga ou de envio, em vermelho no corpo do diálogo. */
export function ModalAlerta({ children }: { children: ReactNode }) {
  return (
    <p className={styles.alerta} role="alert">
      {children}
    </p>
  )
}
