import type { ReactNode } from 'react'
import { useNavigate } from 'react-router'
import { paths } from '../../../routes/paths'
import { limparSessao } from '../../../services/authService'
import { ProfessorHeader } from '../components/ProfessorHeader/ProfessorHeader'
import { ProfessorNav } from '../components/ProfessorNav/ProfessorNav'
import styles from './ProfessorLayout.module.css'

type ProfessorLayoutProps = {
  titulo: string
  subtitulo?: ReactNode
  children: ReactNode
}

export function ProfessorLayout({ titulo, subtitulo, children }: ProfessorLayoutProps) {
  const navigate = useNavigate()

  // JWT e stateless: basta descartar o token local. replace impede que o
  // "voltar" reabra o painel.
  function sair() {
    limparSessao()
    navigate(paths.login, { replace: true })
  }

  return (
    <div className={styles.shell}>
      <ProfessorHeader titulo={titulo} subtitulo={subtitulo} onSair={sair} />
      <main className={styles.main}>{children}</main>
      <ProfessorNav />
    </div>
  )
}
