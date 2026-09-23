import type { RouteObject } from 'react-router'
import { AreaEmConstrucao } from '../../pages/AreaEmConstrucao/AreaEmConstrucao'
import { paths } from '../../routes/paths'
import { RotaProtegida } from '../../routes/RotaProtegida'

// Rota-mãe do perfil SECRETARIA. As telas entram como children do RotaProtegida.
export const secretariaRoutes: RouteObject = {
  path: paths.secretaria.raiz,
  element: <RotaProtegida perfis={['SECRETARIA']} />,
  children: [{ index: true, element: <AreaEmConstrucao area="Secretaria" /> }],
}
