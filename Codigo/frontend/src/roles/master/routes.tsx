import type { RouteObject } from 'react-router'
import { AreaEmConstrucao } from '../../pages/AreaEmConstrucao/AreaEmConstrucao'
import { paths } from '../../routes/paths'
import { RotaProtegida } from '../../routes/RotaProtegida'

// Rota-mãe do perfil MASTER. As telas entram como children do RotaProtegida.
export const masterRoutes: RouteObject = {
  path: paths.master.raiz,
  element: <RotaProtegida perfis={['MASTER']} />,
  children: [{ index: true, element: <AreaEmConstrucao area="Gestão da escola" /> }],
}
