import type { ReactNode } from 'react'
import { useNavigate } from 'react-router'
import { paths } from '../../../routes/paths'
import { limparSessao } from '../../../services/authService'
import { AlunoHeader } from '../components/AlunoHeader/AlunoHeader'
import { AlunoNav } from '../components/AlunoNav/AlunoNav'
import styles from './AlunoLayout.module.css'

type AlunoLayoutProps = {
  titulo: string
  subtitulo?: ReactNode
  escola?: string
  children: ReactNode
}

export function AlunoLayout({ titulo, subtitulo, escola, children }: AlunoLayoutProps) {
  const navigate = useNavigate()

  function sair() {
    limparSessao()
    navigate(paths.login, { replace: true })
  }

  function handleAjuda() {
    alert(
      'Precisa de ajuda?\n\nEntre em contato com a secretaria da sua escola através dos canais de atendimento ou dirija-se à coordenação pedagógica.',
    )
  }

  return (
    <div className={styles.shell}>
      <AlunoHeader
        titulo={titulo}
        subtitulo={subtitulo}
        escola={escola}
        onSair={sair}
        onAjuda={handleAjuda}
      />
      <main className={styles.main}>{children}</main>
      <AlunoNav />
    </div>
  )
}
