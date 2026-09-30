import { useState, type FormEvent } from 'react'
import { Button } from '../../../../components/Button/Button'
import { Icon } from '../../../../components/Icon/Icon'
import { solicitarCorrecaoDados } from '../../../../services/alunoService'
import styles from './ModalCorrecaoDados.module.css'

type ModalCorrecaoDadosProps = {
  aberto: boolean
  escolaNome: string
  escolaEmail: string
  escolaTelefone: string
  onFechar: () => void
}

export function ModalCorrecaoDados({
  aberto,
  escolaNome,
  escolaEmail,
  escolaTelefone,
  onFechar,
}: ModalCorrecaoDadosProps) {
  const [mensagem, setMensagem] = useState('')
  const [enviando, setEnviando] = useState(false)
  const [sucesso, setSucesso] = useState(false)

  if (!aberto) return null

  async function handleSubmit(e: FormEvent) {
    e.preventDefault()
    if (!mensagem.trim()) return

    setEnviando(true)
    try {
      await solicitarCorrecaoDados(mensagem)
      setSucesso(true)
      setTimeout(() => {
        setSucesso(false)
        setMensagem('')
        onFechar()
      }, 2200)
    } finally {
      setEnviando(false)
    }
  }

  return (
    <div className={styles.backdrop} onClick={onFechar} role="dialog" aria-modal="true">
      <div className={styles.modal} onClick={(e) => e.stopPropagation()}>
        <div className={styles.cabecalho}>
          <h2 className={styles.titulo}>Pedir correção de dados</h2>
          <button
            type="button"
            className={styles.botaoFechar}
            onClick={onFechar}
            aria-label="Fechar modal"
          >
            <Icon name="close" size={16} />
          </button>
        </div>

        <div className={styles.infoBox}>
          <p>
            Por determinação acadêmica e de segurança, os dados cadastrais só podem ser modificados diretamente pela secretaria de <strong>{escolaNome}</strong>.
          </p>
          <div className={styles.contatos}>
            <div>📧 E-mail: <strong>{escolaEmail}</strong></div>
            <div>📞 Telefone: <strong>{escolaTelefone}</strong></div>
          </div>
        </div>

        {sucesso ? (
          <div className={styles.sucessoMensagem}>
            ✓ Solicitação registrada com sucesso! A secretaria analisará sua solicitação.
          </div>
        ) : (
          <form className={styles.formulario} onSubmit={handleSubmit}>
            <label className={styles.label} htmlFor="mensagemCorrecao">
              Envie uma mensagem descrevendo o que precisa ser corrigido:
            </label>
            <textarea
              id="mensagemCorrecao"
              className={styles.textarea}
              placeholder="Ex: Meu número de telefone mudou para (31) 98765-4321..."
              value={mensagem}
              onChange={(e) => setMensagem(e.target.value)}
              required
              rows={4}
            />

            <div className={styles.acoes}>
              <Button variant="outline" onClick={onFechar} type="button">
                Cancelar
              </Button>
              <Button variant="primary" type="submit" disabled={enviando || !mensagem.trim()}>
                {enviando ? 'Enviando…' : 'Enviar solicitação'}
              </Button>
            </div>
          </form>
        )}
      </div>
    </div>
  )
}
