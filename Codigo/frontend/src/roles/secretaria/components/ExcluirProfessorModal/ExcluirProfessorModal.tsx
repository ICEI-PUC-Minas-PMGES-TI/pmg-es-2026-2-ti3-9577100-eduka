import { useEffect, useId, useRef, useState } from 'react'
import { Button } from '../../../../components/Button/Button'
import { Icon } from '../../../../components/Icon/Icon'
import { ApiError } from '../../../../services/api'
import type { Professor } from '../../../../types/professor'
import modal from '../CadastroProfessorModal/CadastroProfessorModal.module.css'
import styles from './ExcluirProfessorModal.module.css'

type ExcluirProfessorModalProps = {
  professor: Professor
  onFechar: () => void
  /** Exclui de vez; deve lançar ApiError em caso de falha. */
  onExcluir: (professor: Professor) => Promise<void>
  /** Inativa no lugar de excluir; deve lançar ApiError em caso de falha. */
  onInativar: (professor: Professor) => Promise<void>
  onConcluido: (mensagem: string) => void
}

/**
 * Confirmação da exclusão. Excluir é para quem saiu da escola e não tem
 * volta; para afastamento (licença) o caminho é inativar, oferecido aqui
 * mesmo para quem abriu o diálogo pelo motivo errado.
 */
export function ExcluirProfessorModal({
  professor,
  onFechar,
  onExcluir,
  onInativar,
  onConcluido,
}: ExcluirProfessorModalProps) {
  const dialogRef = useRef<HTMLDialogElement>(null)
  const tituloId = useId()
  const [erro, setErro] = useState<string | null>(null)
  const [enviando, setEnviando] = useState(false)

  useEffect(() => {
    const dialog = dialogRef.current
    dialog?.showModal()
    return () => dialog?.close()
  }, [])

  function fechar() {
    if (!enviando) onFechar()
  }

  async function executar(acao: () => Promise<void>, sucesso: string, falha: string) {
    setErro(null)
    setEnviando(true)
    try {
      await acao()
      onConcluido(sucesso)
    } catch (e) {
      if (e instanceof ApiError && e.status === 409) {
        setErro('Este professor já tem histórico na escola e não pode ser excluído. Inative-o.')
      } else {
        setErro(e instanceof ApiError ? e.message : falha)
      }
      setEnviando(false)
    }
  }

  return (
    <dialog
      ref={dialogRef}
      className={`${modal.dialog} ${styles.dialog}`}
      aria-labelledby={tituloId}
      onCancel={(e) => {
        e.preventDefault()
        fechar()
      }}
    >
      <div className={modal.form}>
        <header className={modal.header}>
          <h2 id={tituloId} className={modal.title}>
            Excluir professor?
          </h2>
          <button
            type="button"
            className={modal.close}
            aria-label="Fechar"
            disabled={enviando}
            onClick={fechar}
          >
            <Icon name="close" size={16} />
          </button>
        </header>

        <div className={modal.body}>
          <div className={styles.texto}>
            <p>
              <strong>{professor.nome}</strong> e o acesso dele ao Eduka serão apagados. Não dá para desfazer.
            </p>
            <p>Use a exclusão quando o professor saiu da escola. Para licença ou outro afastamento, inative.</p>
          </div>

          {erro && (
            <p className={modal.alerta} role="alert">
              {erro}
            </p>
          )}
        </div>

        <footer className={modal.footer}>
          <Button disabled={enviando} onClick={fechar}>
            Cancelar
          </Button>
          {professor.ativo && (
            <Button
              icon="pause"
              disabled={enviando}
              onClick={() =>
                executar(
                  () => onInativar(professor),
                  `"${professor.nome}" foi inativado.`,
                  'Erro inesperado ao inativar o professor.',
                )
              }
            >
              Inativar em vez disso
            </Button>
          )}
          <Button
            className={styles.perigo}
            icon="trash"
            disabled={enviando}
            onClick={() =>
              executar(
                () => onExcluir(professor),
                `"${professor.nome}" foi excluído.`,
                'Erro inesperado ao excluir o professor.',
              )
            }
          >
            {enviando ? 'Aguarde…' : 'Excluir professor'}
          </Button>
        </footer>
      </div>
    </dialog>
  )
}
