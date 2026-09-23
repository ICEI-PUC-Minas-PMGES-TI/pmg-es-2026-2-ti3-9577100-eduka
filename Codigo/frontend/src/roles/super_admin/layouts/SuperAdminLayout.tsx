import type { ReactNode } from 'react'
import { SuperAdminHeader } from '../components/SuperAdminHeader/SuperAdminHeader'
import { SuperAdminNav } from '../components/SuperAdminNav/SuperAdminNav'
import { obterUsuarioLogado } from '../../../services/authService'
import styles from './SuperAdminLayout.module.css'

type SuperAdminLayoutProps = {
  busca: string
  onBuscaChange: (valor: string) => void
  children: ReactNode
}

export function SuperAdminLayout({ busca, onBuscaChange, children }: SuperAdminLayoutProps) {
  const sessao = obterUsuarioLogado()
  const usuarioLogado = sessao
    ? { nome: sessao.login, perfil: 'Super Admin' }
    : { nome: 'Lucas Silva', perfil: 'Super Admin' }
  return (
    <div className={styles.shell}>
      <SuperAdminHeader busca={busca} onBuscaChange={onBuscaChange} usuario={usuarioLogado} />
      <main className={styles.main}>{children}</main>
      <SuperAdminNav />
    </div>
  )
}
