import type { ReactNode } from 'react'
import { useNavigate } from 'react-router'
import { paths } from '../../../routes/paths'
import { limparSessao } from '../../../services/authService'
import { SecretariaHeader } from '../components/SecretariaHeader/SecretariaHeader'
import { SecretariaNav } from '../components/SecretariaNav/SecretariaNav'
import styles from './SecretariaLayout.module.css'

type SecretariaLayoutProps = {
  titulo: string
  subtitulo?: ReactNode
  children: ReactNode
}

export function SecretariaLayout({ titulo, subtitulo, children }: SecretariaLayoutProps) {
  const navigate = useNavigate()

  // JWT e stateless: basta descartar o token local. replace impede que o
  // "voltar" reabra o painel.
  function sair() {
    limparSessao()
    navigate(paths.login, { replace: true })
  }

  return (
    <div className={styles.shell}>
      <SecretariaHeader titulo={titulo} subtitulo={subtitulo} onSair={sair} />
      <main className={styles.main}>{children}</main>
      <SecretariaNav />
    </div>
  )
}
