import { Navigate, type RouteObject } from 'react-router'
import { paths } from '../../routes/paths'
import { RotaProtegida } from '../../routes/RotaProtegida'
import { SecretariaDashboard } from './components/PainelSecretaria/SecretariaDashboard'
import { Alocacoes } from './pages/Alocacoes/Alocacoes'
import { Alunos } from './pages/Alunos/Alunos'
import { Inicio } from './pages/Inicio/Inicio'
import { Matriculas } from './pages/Matriculas/Matriculas'
import { Professores } from './pages/Professores/Professores'

// Rota-mãe do perfil SECRETARIA. As telas entram como children do RotaProtegida.
export const secretariaRoutes: RouteObject = {
  path: paths.secretaria.raiz,
  element: <RotaProtegida perfis={['SECRETARIA']} />,
  children: [
    { index: true, element: <Navigate to={paths.secretaria.inicio} replace /> },
    { path: paths.secretaria.inicio, element: <Inicio /> },
    { path: paths.secretaria.alunos, element: <Alunos /> },
    { path: paths.secretaria.professores, element: <Professores /> },
    { path: paths.secretaria.academico, element: <SecretariaDashboard /> },
    { path: paths.secretaria.alocacoes, element: <Alocacoes /> },
    { path: paths.secretaria.matriculas, element: <Matriculas /> },
  ],
}
