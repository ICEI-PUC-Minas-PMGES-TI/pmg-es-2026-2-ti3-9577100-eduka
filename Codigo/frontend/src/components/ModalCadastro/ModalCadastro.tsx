import { useRef, useState, type FormEvent, type ReactNode } from 'react'
import { ApiError } from '../../services/api'
import { Button } from '../Button/Button'
import { Modal, ModalAlerta } from '../Modal/Modal'
import styles from './ModalCadastro.module.css'

type ModalCadastroProps = {
  titulo: string
  rotuloSalvar: string
  onFechar: () => void
  /** Valida os campos; devolve false (e marca os erros) para não enviar. */
  validar: () => boolean
  /** Envia o cadastro; deve lançar ApiError em caso de falha. */
  onEnviar: () => Promise<void>
  /** Texto do erro de envio; o padrão cobre 400 e 404 genericamente. */
  traduzirErro?: (erro: unknown) => string
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
 * Diálogo de cadastro curto: campos em duas colunas (children) e rodapé com
 * Cancelar/Salvar. Cuida do envio e do erro do servidor; os campos e a
 * validação ficam com cada formulário. Formulários longos, com carga do
 * registro e seções, usam o Modal direto.
 */
export function ModalCadastro({
  titulo,
  rotuloSalvar,
  onFechar,
  validar,
  onEnviar,
  traduzirErro = mensagemDeEnvio,
  children,
}: ModalCadastroProps) {
  const dialogRef = useRef<HTMLDialogElement>(null)
  const [salvando, setSalvando] = useState(false)
  const [erroEnvio, setErroEnvio] = useState<string | null>(null)

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
      setErroEnvio(traduzirErro(e))
      setSalvando(false)
    }
  }

  return (
    <Modal
      titulo={titulo}
      ocupado={salvando}
      onFechar={onFechar}
      onEnviar={handleSubmit}
      dialogRef={dialogRef}
      rodape={
        <>
          <Button disabled={salvando} onClick={onFechar}>
            Cancelar
          </Button>
          <Button type="submit" variant="accent" disabled={salvando}>
            {salvando ? 'Salvando…' : rotuloSalvar}
          </Button>
        </>
      }
    >
      <div className={styles.grid}>{children}</div>
      {erroEnvio && <ModalAlerta>{erroEnvio}</ModalAlerta>}
    </Modal>
  )
}
