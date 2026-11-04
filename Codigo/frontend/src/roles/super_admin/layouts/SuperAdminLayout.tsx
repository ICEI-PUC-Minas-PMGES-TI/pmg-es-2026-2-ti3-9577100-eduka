import type { ReactNode } from 'react'
import { PainelLayout, type ItemMenu } from '../../../components/PainelLayout/PainelLayout'
import { paths } from '../../../routes/paths'
import { obterUsuarioLogado } from '../../../services/authService'

const MENU: ItemMenu[] = [
  { label: 'Escolas', icon: 'home', to: paths.superAdmin.escolas },
  // Em breve: ainda sem página.
  { label: 'Usuários', icon: 'users' },
  { label: 'Assinaturas', icon: 'card' },
  { label: 'Configurações', icon: 'settings' },
]

type SuperAdminLayoutProps = {
  titulo: string
  subtitulo?: ReactNode
  /** Botão principal da tela, ao lado de "Ajuda" e "Sair". */
  acao?: ReactNode
  /** Link de volta, exibido no lugar da linha da marca. */
  voltar?: ReactNode
  children: ReactNode
}

export function SuperAdminLayout(props: SuperAdminLayoutProps) {
  const login = obterUsuarioLogado()?.login
  return (
    <PainelLayout
      contexto="Super Console"
      menu={MENU}
      menuRotulo="Navegação do super admin"
      rotaSaida={paths.loginAdmin}
      usuario={login ? { nome: login, papel: 'Super Admin' } : undefined}
      {...props}
    />
  )
}
