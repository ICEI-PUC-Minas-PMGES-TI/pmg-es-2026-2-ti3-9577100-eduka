import type { ReactNode } from 'react'
import { useNavigate } from 'react-router'
import { PainelHeader } from '../../../components/PainelHeader/PainelHeader'
import { paths } from '../../../routes/paths'
import { limparSessao } from '../../../services/authService'
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
      <PainelHeader titulo={titulo} subtitulo={subtitulo} contexto="Secretaria" onSair={sair} />
      <main className={styles.main}>{children}</main>
      <SecretariaNav />
    </div>
  )
}
