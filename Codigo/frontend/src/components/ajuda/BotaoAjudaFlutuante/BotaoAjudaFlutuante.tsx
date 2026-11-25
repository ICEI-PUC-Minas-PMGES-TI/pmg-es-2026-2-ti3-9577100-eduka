import { useState } from 'react'
import { obterUsuarioLogado } from '../../../services/acesso/authService'
import { Modal } from '../../modal/Modal/Modal'
import { Button } from '../../ui/Button/Button'
import { Icon } from '../../ui/Icon/Icon'
import { CentralAjuda } from '../CentralAjuda/CentralAjuda'
import styles from './BotaoAjudaFlutuante.module.css'

/**
 * "Ajuda" das telas fora do painel (login, página inicial, aviso de
 * privacidade, 404...), fixo no canto da tela. Se já houver sessão (ex.: no
 * aviso de privacidade), a Central mostra a ajuda do perfil.
 */
export function BotaoAjudaFlutuante({ tela }: { tela?: string }) {
  const [aberta, setAberta] = useState(false)
  const perfil = obterUsuarioLogado()?.perfil ?? null

  return (
    <>
      <button type="button" className={styles.botao} onClick={() => setAberta(true)} aria-haspopup="dialog">
        <Icon name="help" size={18} />
        Ajuda
      </button>
      {aberta && (
        <Modal
          titulo="Central de ajuda"
          subtitulo="Fale com a sua escola ou com o suporte do Eduka."
          onFechar={() => setAberta(false)}
          rodape={<Button onClick={() => setAberta(false)}>Fechar</Button>}
        >
          <CentralAjuda perfil={perfil} tela={tela} />
        </Modal>
      )}
    </>
  )
}
