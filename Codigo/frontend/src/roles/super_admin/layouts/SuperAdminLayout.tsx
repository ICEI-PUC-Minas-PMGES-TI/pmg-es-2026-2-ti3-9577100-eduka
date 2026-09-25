import type { ReactNode } from 'react'
import { useNavigate } from 'react-router'
import { SuperAdminHeader } from '../components/SuperAdminHeader/SuperAdminHeader'
import { SuperAdminNav } from '../components/SuperAdminNav/SuperAdminNav'
import { paths } from '../../../routes/paths'
import { limparSessao, obterUsuarioLogado } from '../../../services/authService'
import styles from './SuperAdminLayout.module.css'

type SuperAdminLayoutProps = {
  titulo: string
  subtitulo?: ReactNode
  /** Botão principal da tela, ao lado do "Sair". */
  acao?: ReactNode
  /** Link de volta, exibido no lugar da linha da marca. */
  voltar?: ReactNode
  children: ReactNode
}

export function SuperAdminLayout({ titulo, subtitulo, acao, voltar, children }: SuperAdminLayoutProps) {
  const navigate = useNavigate()
  const sessao = obterUsuarioLogado()

  // JWT e stateless: nao ha nada para invalidar no backend, basta descartar
  // o token local. replace impede que o "voltar" reabra o painel.
  function sair() {
    limparSessao()
    navigate(paths.login, { replace: true })
  }

  return (
    <div className={styles.shell}>
      <SuperAdminHeader
        titulo={titulo}
        subtitulo={subtitulo}
        acao={acao}
        voltar={voltar}
        usuario={sessao?.login}
        onSair={sair}
      />
      <main className={styles.main}>{children}</main>
      <SuperAdminNav />
    </div>
  )
}
