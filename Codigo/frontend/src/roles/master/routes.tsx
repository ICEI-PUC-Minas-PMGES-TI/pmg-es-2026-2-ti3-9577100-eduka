import type { RouteObject } from 'react-router'
import { paths } from '../../routes/paths'
import { RotaProtegida } from '../../routes/RotaProtegida'
import { Inicio } from './pages/Inicio/Inicio'

// Rota-mãe do perfil MASTER. As telas entram como children do RotaProtegida.
export const masterRoutes: RouteObject = {
  path: paths.master.raiz,
  element: <RotaProtegida perfis={['MASTER']} />,
  children: [{ index: true, element: <Inicio /> }],
}
