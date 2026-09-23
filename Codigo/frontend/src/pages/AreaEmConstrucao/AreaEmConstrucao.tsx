import { useNavigate } from 'react-router'
import { paths } from '../../routes/paths'
import { limparSessao, obterUsuarioLogado } from '../../services/authService'
import styles from './AreaEmConstrucao.module.css'

type AreaEmConstrucaoProps = {
  area: string
}

// Tela provisória da rota-mãe de um perfil cujas telas ainda não existem.
// Substitua pelo layout/painel real do perfil quando ele for desenvolvido.
export function AreaEmConstrucao({ area }: AreaEmConstrucaoProps) {
  const navigate = useNavigate()
  const sessao = obterUsuarioLogado()

  function sair() {
    limparSessao()
    navigate(paths.login, { replace: true })
  }

  return (
    <main className={styles.page}>
      <span className={styles.badge}>{area}</span>
      <h1 className={styles.title}>Área em construção</h1>
      <p className={styles.text}>
        {sessao ? `Você entrou como ${sessao.login}. ` : ''}
        As telas deste perfil ainda estão sendo desenvolvidas.
      </p>
      <button type="button" className={styles.sair} onClick={sair}>
        Sair
      </button>
    </main>
  )
}
