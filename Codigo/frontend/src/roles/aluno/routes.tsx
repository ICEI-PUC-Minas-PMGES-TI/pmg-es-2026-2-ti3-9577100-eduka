import type { RouteObject } from 'react-router'
import { AreaEmConstrucao } from '../../pages/AreaEmConstrucao/AreaEmConstrucao'
import { paths } from '../../routes/paths'
import { RotaProtegida } from '../../routes/RotaProtegida'

// Rota-mãe do perfil ALUNO. As telas entram como children do RotaProtegida.
export const alunoRoutes: RouteObject = {
  path: paths.aluno.raiz,
  element: <RotaProtegida perfis={['ALUNO']} />,
  children: [{ index: true, element: <AreaEmConstrucao area="Portal do aluno" /> }],
}
