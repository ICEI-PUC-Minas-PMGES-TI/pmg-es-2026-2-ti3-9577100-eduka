import { Navigate, type RouteObject } from 'react-router'
import { paths } from '../../routes/paths'
import { RotaProtegida } from '../../routes/RotaProtegida'
import { MinhasTurmas } from './pages/MinhasTurmas/MinhasTurmas'

// Rota-mãe do perfil PROFESSOR. As telas entram como children do RotaProtegida.
export const professorRoutes: RouteObject = {
  path: paths.professor.raiz,
  element: <RotaProtegida perfis={['PROFESSOR']} />,
  children: [
    // Enquanto o "Início" não existe, o professor cai direto em Minhas turmas.
    { index: true, element: <Navigate to={paths.professor.turmas} replace /> },
    { path: paths.professor.turmas, element: <MinhasTurmas /> },
  ],
}
