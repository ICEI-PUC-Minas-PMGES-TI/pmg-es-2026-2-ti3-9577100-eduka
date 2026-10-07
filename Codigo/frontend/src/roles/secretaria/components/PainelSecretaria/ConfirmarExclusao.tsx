import { useEffect, useId, useRef, useState } from 'react'
import { Button } from '../../../../components/Button/Button'
import { Icon } from '../../../../components/Icon/Icon'
import { mensagemErroAcademico } from './erros'
import styles from './PainelSecretaria.module.css'

type ConfirmarExclusaoProps = {
  /** Ex.: "Excluir curso". */
  titulo: string
  /** Nome do registro, em destaque na pergunta. */
  nome: string
  onFechar: () => void
  /** Exclui; deve lançar ApiError em caso de falha (ex.: 409 em uso). */
  onConfirmar: () => Promise<void>
}

/**
 * Pergunta antes de excluir um registro do painel acadêmico. Se o backend
 * recusar (registro em uso), mostra o motivo e continua aberto.
 */
export function ConfirmarExclusao({ titulo, nome, onFechar, onConfirmar }: ConfirmarExclusaoProps) {
  const dialogRef = useRef<HTMLDialogElement>(null)
  const tituloId = useId()
  const [excluindo, setExcluindo] = useState(false)
  const [erro, setErro] = useState<string | null>(null)

  useEffect(() => {
    const dialog = dialogRef.current
    dialog?.showModal()
    return () => dialog?.close()
  }, [])

  function fechar() {
    if (!excluindo) onFechar()
  }

  async function confirmar() {
    setErro(null)
    setExcluindo(true)
    try {
      await onConfirmar()
    } catch (e) {
      setErro(mensagemErroAcademico(e))
      setExcluindo(false)
    }
  }

  return (
    <dialog
      ref={dialogRef}
      className={`${styles.dialog} ${styles.dialogPequeno}`}
      aria-labelledby={tituloId}
      onCancel={(e) => {
        e.preventDefault()
        fechar()
      }}
    >
      <div className={styles.form}>
        <header className={styles.dialogHeader}>
          <h2 id={tituloId} className={styles.dialogTitulo}>
            {titulo}
          </h2>
          <button type="button" className={styles.fechar} aria-label="Fechar" disabled={excluindo} onClick={fechar}>
            <Icon name="close" size={16} />
          </button>
        </header>

        <div className={styles.dialogCorpo}>
          <p>
            Excluir <strong>{nome}</strong>? Esta ação não pode ser desfeita.
          </p>
          {erro && (
            <p className={styles.alertaForm} role="alert">
              {erro}
            </p>
          )}
        </div>

        <footer className={styles.dialogRodape}>
          <Button disabled={excluindo} onClick={fechar}>
            Cancelar
          </Button>
          <Button className={styles.botaoPerigo} icon="trash" disabled={excluindo} onClick={confirmar}>
            {excluindo ? 'Excluindo…' : 'Excluir'}
          </Button>
        </footer>
      </div>
    </dialog>
  )
}
