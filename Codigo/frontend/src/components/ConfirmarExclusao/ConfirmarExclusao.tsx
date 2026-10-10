import { useState, type ReactNode } from 'react'
import { ApiError } from '../../services/api'
import { Button } from '../Button/Button'
import type { IconName } from '../Icon/Icon'
import { Modal, ModalAlerta } from '../Modal/Modal'
import styles from './ConfirmarExclusao.module.css'

type AcaoAlternativa = {
  rotulo: string
  icon?: IconName
  /** Deve lançar ApiError em caso de falha. */
  onClick: () => Promise<void>
}

type ConfirmarExclusaoProps = {
  /** Ex.: "Excluir curso". */
  titulo: string
  /** Nome do registro, em destaque na pergunta padrão. */
  nome: string
  /** Texto no lugar da pergunta padrão "Excluir X? Esta ação não pode ser desfeita." */
  children?: ReactNode
  /** Rótulo do botão vermelho; o padrão é "Excluir". */
  rotuloConfirmar?: string
  /** Ícone do botão vermelho; o padrão é a lixeira. */
  iconeConfirmar?: IconName
  /** Caminho menos drástico oferecido no mesmo diálogo (ex.: inativar). */
  alternativa?: AcaoAlternativa
  onFechar: () => void
  /** Exclui; deve lançar ApiError em caso de falha (ex.: 409 em uso). */
  onConfirmar: () => Promise<void>
  /** Texto do erro do backend; o padrão é a mensagem que a API mandou. */
  traduzirErro?: (erro: unknown) => string
}

function mensagemPadrao(erro: unknown): string {
  return erro instanceof ApiError ? erro.message : 'Erro inesperado. Tente de novo.'
}

/**
 * Pergunta antes de excluir um registro. Se o backend recusar (registro em
 * uso), mostra o motivo e continua aberto.
 */
export function ConfirmarExclusao({
  titulo,
  nome,
  children,
  rotuloConfirmar = 'Excluir',
  iconeConfirmar = 'trash',
  alternativa,
  onFechar,
  onConfirmar,
  traduzirErro = mensagemPadrao,
}: ConfirmarExclusaoProps) {
  const [enviando, setEnviando] = useState(false)
  const [erro, setErro] = useState<string | null>(null)

  async function executar(acao: () => Promise<void>) {
    setErro(null)
    setEnviando(true)
    try {
      await acao()
    } catch (e) {
      setErro(traduzirErro(e))
      setEnviando(false)
    }
  }

  return (
    <Modal
      titulo={titulo}
      // Com a alternativa são três botões, que não cabem no diálogo pequeno.
      tamanho={alternativa ? 'grande' : 'pequeno'}
      ocupado={enviando}
      onFechar={onFechar}
      rodape={
        <>
          <Button disabled={enviando} onClick={onFechar}>
            Cancelar
          </Button>
          {alternativa && (
            <Button icon={alternativa.icon} disabled={enviando} onClick={() => executar(alternativa.onClick)}>
              {alternativa.rotulo}
            </Button>
          )}
          <Button variant="danger" icon={iconeConfirmar} disabled={enviando} onClick={() => executar(onConfirmar)}>
            {enviando ? 'Aguarde…' : rotuloConfirmar}
          </Button>
        </>
      }
    >
      <div className={styles.texto}>
        {children ?? (
          <p>
            Excluir <strong>{nome}</strong>? Esta ação não pode ser desfeita.
          </p>
        )}
      </div>
      {erro && <ModalAlerta>{erro}</ModalAlerta>}
    </Modal>
  )
}
