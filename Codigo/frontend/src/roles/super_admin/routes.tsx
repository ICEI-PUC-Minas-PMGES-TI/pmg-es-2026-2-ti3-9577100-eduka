import { Navigate, type RouteObject } from 'react-router'
import { paths } from '../../routes/paths'
import { RotaProtegida } from '../../routes/RotaProtegida'
import { EditarEscola } from './pages/EditarEscola/EditarEscola'
import { Faturas } from './pages/Faturas/Faturas'
import { FaturasPlataforma } from './pages/FaturasPlataforma/FaturasPlataforma'
import { PainelEscolas } from './pages/PainelEscolas/PainelEscolas'
import { Usuarios } from './pages/Usuarios/Usuarios'

export const superAdminRoutes: RouteObject = {
  path: paths.superAdmin.raiz,
  children: [
    // Fica fora do RotaProtegida: é o atalho antigo para o login.
    { path: paths.superAdmin.login, element: <Navigate to={paths.loginAdmin} replace /> },
    {
      element: <RotaProtegida perfis={['SUPER_ADMIN']} login={paths.loginAdmin} />,
      children: [
        { index: true, element: <Navigate to={paths.superAdmin.escolas} replace /> },
        { path: paths.superAdmin.escolas, element: <PainelEscolas /> },
        { path: paths.superAdmin.editarEscola, element: <EditarEscola /> },
        { path: paths.superAdmin.faturasEscola, element: <Faturas /> },
        { path: paths.superAdmin.assinaturas, element: <FaturasPlataforma /> },
        { path: paths.superAdmin.usuarios, element: <Usuarios /> },
      ],
    },
  ],
}
