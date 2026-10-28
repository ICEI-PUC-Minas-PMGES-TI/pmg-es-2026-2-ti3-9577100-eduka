import { Navigate, type RouteObject } from 'react-router'
import { paths } from '../../routes/paths'
import { RotaProtegida } from '../../routes/RotaProtegida'
import { Inicio } from './pages/Inicio/Inicio'
import { Recebimentos } from './pages/Recebimentos/Recebimentos'

// Rota-mãe do perfil MASTER. As telas entram como children do RotaProtegida.
export const masterRoutes: RouteObject = {
  path: paths.master.raiz,
  element: <RotaProtegida perfis={['MASTER']} />,
  children: [
    { index: true, element: <Navigate to={paths.master.inicio} replace /> },
    { path: paths.master.inicio, element: <Inicio /> },
    { path: paths.master.recebimentos, element: <Recebimentos /> },
  ],
}
