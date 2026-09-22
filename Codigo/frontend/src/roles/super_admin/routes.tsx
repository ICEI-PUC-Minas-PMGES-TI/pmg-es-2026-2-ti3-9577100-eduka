import { Navigate, type RouteObject } from 'react-router'
import { paths } from '../../routes/paths'
import { PainelEscolas } from './pages/PainelEscolas/PainelEscolas'

export const superAdminRoutes: RouteObject = {
  path: paths.superAdmin.raiz,
  children: [
    { index: true, element: <Navigate to={paths.superAdmin.escolas} replace /> },
    { path: paths.superAdmin.escolas, element: <PainelEscolas /> },
  ],
}
