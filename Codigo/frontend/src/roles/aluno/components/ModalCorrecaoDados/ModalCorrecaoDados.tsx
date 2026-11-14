import { useState, type FormEvent } from 'react'
import { Button } from '../../../../components/ui/Button/Button'
import { Modal } from '../../../../components/modal/Modal/Modal'
import { ApiError } from '../../../../services/api'
import { pedirCorrecao } from '../../../../services/pessoas/solicitacaoCorrecaoService'
import type { SolicitacaoCorrecao } from '../../../../types/pessoas/solicitacaoCorrecao'
import styles from './ModalCorrecaoDados.module.css'

// Limite da coluna MENSAGEM (V37).
const MAX_MENSAGEM = 500

function mensagemErro(erro: unknown): string {
  if (erro instanceof ApiError && erro.codigo === 'SOLICITACAO_ABERTA') {
    return 'Você já tem um pedido aguardando a secretaria. Espere o atendimento para pedir de novo.'
  }
  if (erro instanceof ApiError) return erro.detalhe ?? erro.message
  return 'Erro inesperado ao enviar o pedido.'
}

type ModalCorrecaoDadosProps = {
  onEnviado: (pedido: SolicitacaoCorrecao) => void
  onFechar: () => void
}

/** Pedir correção de dados (IU15): o aluno diz o que está errado e a secretaria corrige. */
export function ModalCorrecaoDados({ onEnviado, onFechar }: ModalCorrecaoDadosProps) {
  const [mensagem, setMensagem] = useState('')
  const [enviando, setEnviando] = useState(false)
  const [erro, setErro] = useState<string | null>(null)

  async function enviar(evento: FormEvent<HTMLFormElement>) {
    evento.preventDefault()
    if (!mensagem.trim()) {
      setErro('Conte o que precisa ser corrigido.')
      return
    }
    setErro(null)
    setEnviando(true)
    try {
      onEnviado(await pedirCorrecao(mensagem.trim()))
    } catch (e) {
      setErro(mensagemErro(e))
      setEnviando(false)
    }
  }

  return (
    <Modal
      titulo="Pedir correção de dados"
      subtitulo="A secretaria recebe o pedido e corrige o seu cadastro."
      tamanho="medio"
      ocupado={enviando}
      onFechar={onFechar}
      onEnviar={enviar}
      rodape={
        <>
          <Button onClick={onFechar} disabled={enviando}>
            Cancelar
          </Button>
          <Button type="submit" variant="primary" disabled={enviando}>
            {enviando ? 'Enviando…' : 'Enviar pedido'}
          </Button>
        </>
      }
    >
      <label htmlFor="correcao-mensagem" className={styles.rotulo}>
        O que está errado?
      </label>
      <textarea
        id="correcao-mensagem"
        className={styles.campo}
        rows={5}
        maxLength={MAX_MENSAGEM}
        placeholder="Ex.: meu telefone mudou para (31) 99999-0000."
        value={mensagem}
        aria-invalid={erro !== null}
        onChange={(e) => {
          setMensagem(e.target.value)
          setErro(null)
        }}
      />
      <span className={styles.contador}>
        {mensagem.length}/{MAX_MENSAGEM}
      </span>
      {erro && (
        <p className={styles.erro} role="alert">
          {erro}
        </p>
      )}
    </Modal>
  )
}
