import type { RouteObject } from 'react-router'
import { AreaEmConstrucao } from '../../pages/AreaEmConstrucao/AreaEmConstrucao'
import { paths } from '../../routes/paths'
import { RotaProtegida } from '../../routes/RotaProtegida'

// Rota-mãe do perfil PROFESSOR. As telas entram como children do RotaProtegida.
export const professorRoutes: RouteObject = {
  path: paths.professor.raiz,
  element: <RotaProtegida perfis={['PROFESSOR']} />,
  children: [{ index: true, element: <AreaEmConstrucao area="Portal do professor" /> }],
}
