import { Navigate, type RouteObject } from 'react-router'
import { paths } from '../../routes/paths'
import { RotaProtegida } from '../../routes/RotaProtegida'
import { Boletim } from './pages/Boletim/Boletim'
import { MeusDados } from './pages/MeusDados/MeusDados'

// Rota-mãe do perfil ALUNO. As telas entram como children do RotaProtegida.
export const alunoRoutes: RouteObject = {
  path: paths.aluno.raiz,
  element: <RotaProtegida perfis={['ALUNO']} />,
  children: [
    { index: true, element: <Navigate to={paths.aluno.boletim} replace /> },
    { path: paths.aluno.boletim, element: <Boletim /> },
    { path: paths.aluno.meusDados, element: <MeusDados /> },
  ],
}
