import { Navigate, type RouteObject } from 'react-router'
import { paths } from '../../routes/paths'
import { RotaProtegida } from '../../routes/RotaProtegida'
import { Alocacoes } from './pages/Alocacoes/Alocacoes'
import { Professores } from './pages/Professores/Professores'

// Rota-mãe do perfil SECRETARIA. As telas entram como children do RotaProtegida.
export const secretariaRoutes: RouteObject = {
  path: paths.secretaria.raiz,
  element: <RotaProtegida perfis={['SECRETARIA']} />,
  children: [
    // Enquanto o "Início" não existe, a secretaria cai direto em Professores.
    { index: true, element: <Navigate to={paths.secretaria.professores} replace /> },
    { path: paths.secretaria.professores, element: <Professores /> },
    { path: paths.secretaria.alocacoes, element: <Alocacoes /> },
  ],
}
