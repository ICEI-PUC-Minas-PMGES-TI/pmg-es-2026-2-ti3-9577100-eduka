import type { RouteObject } from 'react-router'
import { AreaEmConstrucao } from '../../pages/AreaEmConstrucao/AreaEmConstrucao'
import { paths } from '../../routes/paths'
import { RotaProtegida } from '../../routes/RotaProtegida'

// Rota-mãe do perfil FINANCEIRO. As telas entram como children do RotaProtegida.
export const financeiroRoutes: RouteObject = {
  path: paths.financeiro.raiz,
  element: <RotaProtegida perfis={['FINANCEIRO']} />,
  children: [{ index: true, element: <AreaEmConstrucao area="Financeiro" /> }],
}
