import { Navigate, redirect, type RouteObject } from 'react-router'
import { paths } from '../../routes/paths'
import { RotaProtegida } from '../../routes/RotaProtegida'
import { Avaliacoes } from './pages/Avaliacoes/Avaliacoes'
import { Chamada } from './pages/Chamada/Chamada'
import { Inicio } from './pages/Inicio/Inicio'
import { MinhasTurmas } from './pages/MinhasTurmas/MinhasTurmas'

// Rota-mãe do perfil PROFESSOR. As telas entram como children do RotaProtegida.
export const professorRoutes: RouteObject = {
  path: paths.professor.raiz,
  element: <RotaProtegida perfis={['PROFESSOR']} />,
  children: [
    { index: true, element: <Navigate to={paths.professor.inicio} replace /> },
    { path: paths.professor.inicio, element: <Inicio /> },
    { path: paths.professor.turmas, element: <MinhasTurmas /> },
    // A tela de notas passou a se chamar Avaliações: o endereço antigo cai nela, com os mesmos parâmetros.
    {
      path: paths.professor.notas,
      loader: ({ request }) => redirect(`${paths.professor.avaliacoes}${new URL(request.url).search}`),
    },
    { path: paths.professor.avaliacoes, element: <Avaliacoes /> },
    { path: paths.professor.chamada, element: <Chamada /> },
  ],
}
