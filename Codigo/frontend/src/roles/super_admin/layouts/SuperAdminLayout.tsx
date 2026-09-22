import type { ReactNode } from 'react'
import { SuperAdminHeader } from '../components/SuperAdminHeader/SuperAdminHeader'
import { SuperAdminNav } from '../components/SuperAdminNav/SuperAdminNav'
import styles from './SuperAdminLayout.module.css'

type SuperAdminLayoutProps = {
  busca: string
  onBuscaChange: (valor: string) => void
  children: ReactNode
}

// TODO: vir do usuário autenticado quando o login estiver integrado.
const usuarioLogado = { nome: 'Lucas Silva', perfil: 'Super Admin' }

export function SuperAdminLayout({ busca, onBuscaChange, children }: SuperAdminLayoutProps) {
  return (
    <div className={styles.shell}>
      <SuperAdminHeader busca={busca} onBuscaChange={onBuscaChange} usuario={usuarioLogado} />
      <main className={styles.main}>{children}</main>
      <SuperAdminNav />
    </div>
  )
}
