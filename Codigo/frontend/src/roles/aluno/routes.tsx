import { Navigate, type RouteObject } from 'react-router'
import { paths } from '../../routes/paths'
import { RotaProtegida } from '../../routes/RotaProtegida'
import { MeusDados } from './pages/MeusDados/MeusDados'

// Rota-mãe do perfil ALUNO. As telas entram como children do RotaProtegida.
export const alunoRoutes: RouteObject = {
  path: paths.aluno.raiz,
  element: <RotaProtegida perfis={['ALUNO']} />,
  children: [
    { index: true, element: <Navigate to={paths.aluno.meusDados} replace /> },
    { path: paths.aluno.meusDados, element: <MeusDados /> },
  ],
}
