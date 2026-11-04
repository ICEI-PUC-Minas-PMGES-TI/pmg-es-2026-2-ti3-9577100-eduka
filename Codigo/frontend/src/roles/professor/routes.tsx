import { Navigate, redirect, type RouteObject } from 'react-router'
import { paths } from '../../routes/paths'
import { RotaProtegida } from '../../routes/RotaProtegida'
import { Chamada } from './pages/Chamada/Chamada'
import { Inicio } from './pages/Inicio/Inicio'
import { MinhasTurmas } from './pages/MinhasTurmas/MinhasTurmas'
import { Notas } from './pages/Notas/Notas'

// Rota-mãe do perfil PROFESSOR. As telas entram como children do RotaProtegida.
export const professorRoutes: RouteObject = {
  path: paths.professor.raiz,
  element: <RotaProtegida perfis={['PROFESSOR']} />,
  children: [
    { index: true, element: <Navigate to={paths.professor.inicio} replace /> },
    { path: paths.professor.inicio, element: <Inicio /> },
    { path: paths.professor.turmas, element: <MinhasTurmas /> },
    // Avaliações e notas viraram uma tela só: o endereço antigo cai nela, com os mesmos parâmetros.
    {
      path: paths.professor.avaliacoes,
      loader: ({ request }) => redirect(`${paths.professor.notas}${new URL(request.url).search}`),
    },
    { path: paths.professor.notas, element: <Notas /> },
    { path: paths.professor.chamada, element: <Chamada /> },
  ],
}
