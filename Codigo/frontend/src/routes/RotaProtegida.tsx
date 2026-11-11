import { Navigate, Outlet } from 'react-router'
import {
  limparSessao,
  obterToken,
  obterUsuarioLogado,
  type PerfilUsuario,
} from '../services/authService'
import { ExigeAceitePrivacidade } from './ExigeAceitePrivacidade'
import { paths, rotaInicialPorPerfil } from './paths'

type RotaProtegidaProps = {
  perfis: readonly PerfilUsuario[]
  /** Tela de login desta área. Padrão: o login da escola. */
  login?: string
}

// Guarda a rota-mãe de um perfil. Sem sessão, manda para o login; com sessão
// de outro perfil, manda para a área do próprio usuário; sem o aceite do aviso
// de privacidade em vigor, manda para o aviso (IU29).
//
// É só conveniência de navegação: quem garante o acesso é o backend, que
// responde 403 para qualquer rota fora do perfil do token.
export function RotaProtegida({ perfis, login = paths.login }: RotaProtegidaProps) {
  const usuario = obterUsuarioLogado()

  if (!usuario || !obterToken()) {
    return <Navigate to={login} replace />
  }

  if (!perfis.includes(usuario.perfil)) {
    const destino = rotaInicialPorPerfil[usuario.perfil]
    // Perfil desconhecido no storage: descarta a sessão em vez de entrar em
    // loop entre o login e a área protegida.
    if (!destino) {
      limparSessao()
      return <Navigate to={login} replace />
    }
    return <Navigate to={destino} replace />
  }

  return (
    <ExigeAceitePrivacidade login={login}>
      <Outlet />
    </ExigeAceitePrivacidade>
  )
}
