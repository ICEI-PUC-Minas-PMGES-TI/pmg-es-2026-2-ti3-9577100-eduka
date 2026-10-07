import type { ReactNode } from 'react'
import { useNavigate } from 'react-router'
import { PainelHeader } from '../../../components/PainelHeader/PainelHeader'
import { paths } from '../../../routes/paths'
import { limparSessao } from '../../../services/authService'
import styles from './MasterLayout.module.css'

type MasterLayoutProps = {
  titulo: string
  subtitulo?: ReactNode
  children: ReactNode
}

// Sem navegação flutuante por enquanto: o master só tem a tela de início.
export function MasterLayout({ titulo, subtitulo, children }: MasterLayoutProps) {
  const navigate = useNavigate()

  // JWT e stateless: basta descartar o token local. replace impede que o
  // "voltar" reabra o painel.
  function sair() {
    limparSessao()
    navigate(paths.login, { replace: true })
  }

  return (
    <div className={styles.shell}>
      <PainelHeader titulo={titulo} subtitulo={subtitulo} contexto="Gestão da escola" onSair={sair} />
      <main className={styles.main}>{children}</main>
    </div>
  )
}
