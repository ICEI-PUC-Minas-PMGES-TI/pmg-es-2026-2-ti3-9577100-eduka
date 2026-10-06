import { useEffect, useId, useRef, useState, type FormEvent, type ReactNode } from 'react'
import { Button } from '../../../../components/Button/Button'
import { Icon } from '../../../../components/Icon/Icon'
import { ApiError } from '../../../../services/api'
import styles from './PainelSecretaria.module.css'

type ModalCadastroProps = {
  titulo: string
  rotuloSalvar: string
  onFechar: () => void
  /** Valida os campos; devolve false (e marca os erros) para não enviar. */
  validar: () => boolean
  /** Envia o cadastro; deve lançar ApiError em caso de falha. */
  onEnviar: () => Promise<void>
  children: ReactNode
}

function mensagemDeEnvio(erro: unknown): string {
  if (erro instanceof ApiError) {
    if (erro.status === 400) return 'Os dados foram recusados pelo servidor. Revise os campos e tente novamente.'
    if (erro.status === 404) return 'Algum item escolhido não existe mais. Recarregue a página e tente de novo.'
    return erro.message
  }
  return 'Erro inesperado ao salvar.'
}

/**
 * Diálogo de cadastro do painel acadêmico: cabeçalho, corpo com os campos
 * (children) e rodapé com Cancelar/Salvar. Cuida do envio e do erro do
 * servidor; os campos e a validação ficam com cada formulário. Montado só
 * enquanto está aberto, então cada abertura começa limpa.
 */
export function ModalCadastro({ titulo, rotuloSalvar, onFechar, validar, onEnviar, children }: ModalCadastroProps) {
  const dialogRef = useRef<HTMLDialogElement>(null)
  const tituloId = useId()
  const [salvando, setSalvando] = useState(false)
  const [erroEnvio, setErroEnvio] = useState<string | null>(null)

  useEffect(() => {
    const dialog = dialogRef.current
    dialog?.showModal()
    return () => dialog?.close()
  }, [])

  function fechar() {
    if (!salvando) onFechar()
  }

  async function handleSubmit(evento: FormEvent<HTMLFormElement>) {
    evento.preventDefault()
    setErroEnvio(null)

    if (!validar()) {
      // Os erros aparecem no próximo render; aí o foco vai ao primeiro inválido.
      requestAnimationFrame(() =>
        dialogRef.current?.querySelector<HTMLElement>('[aria-invalid="true"]')?.focus(),
      )
      return
    }

    setSalvando(true)
    try {
      await onEnviar()
    } catch (e) {
      setErroEnvio(mensagemDeEnvio(e))
      setSalvando(false)
    }
  }

  return (
    <dialog
      ref={dialogRef}
      className={styles.dialog}
      aria-labelledby={tituloId}
      onCancel={(e) => {
        e.preventDefault()
        fechar()
      }}
    >
      <form className={styles.form} noValidate onSubmit={handleSubmit}>
        <header className={styles.dialogHeader}>
          <h2 id={tituloId} className={styles.dialogTitulo}>
            {titulo}
          </h2>
          <button type="button" className={styles.fechar} aria-label="Fechar" disabled={salvando} onClick={fechar}>
            <Icon name="close" size={16} />
          </button>
        </header>

        <div className={styles.dialogCorpo}>
          <div className={styles.grid}>{children}</div>

          {erroEnvio && (
            <p className={styles.alertaForm} role="alert">
              {erroEnvio}
            </p>
          )}
        </div>

        <footer className={styles.dialogRodape}>
          <Button disabled={salvando} onClick={fechar}>
            Cancelar
          </Button>
          <Button type="submit" variant="accent" disabled={salvando}>
            {salvando ? 'Salvando…' : rotuloSalvar}
          </Button>
        </footer>
      </form>
    </dialog>
  )
}
