import type { ReactNode } from 'react'
import { useNavigate } from 'react-router'
import { SuperAdminHeader } from '../components/SuperAdminHeader/SuperAdminHeader'
import { SuperAdminNav } from '../components/SuperAdminNav/SuperAdminNav'
import { paths } from '../../../routes/paths'
import { limparSessao, obterUsuarioLogado } from '../../../services/authService'
import styles from './SuperAdminLayout.module.css'

type SuperAdminLayoutProps = {
  children: ReactNode
}

export function SuperAdminLayout({ children }: SuperAdminLayoutProps) {
  const navigate = useNavigate()
  const sessao = obterUsuarioLogado()

  // JWT e stateless: nao ha nada para invalidar no backend, basta descartar
  // o token local. replace impede que o "voltar" reabra o painel.
  function sair() {
    limparSessao()
    navigate(paths.login, { replace: true })
  }

  const usuarioLogado = sessao
    ? { nome: sessao.login, perfil: 'Super Admin' }
    : { nome: 'Lucas Silva', perfil: 'Super Admin' }
  return (
    <div className={styles.shell}>
      <SuperAdminHeader usuario={usuarioLogado} onSair={sair} />
      <main className={styles.main}>{children}</main>
      <SuperAdminNav />
    </div>
  )
}
