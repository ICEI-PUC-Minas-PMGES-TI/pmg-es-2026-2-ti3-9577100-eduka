import { createBrowserRouter, Navigate } from 'react-router'
import { PaginaNaoEncontrada } from '../pages/PaginaNaoEncontrada/PaginaNaoEncontrada'
import { alunoRoutes } from '../roles/aluno/routes'
import { financeiroRoutes } from '../roles/financeiro/routes'
import { masterRoutes } from '../roles/master/routes'
import { professorRoutes } from '../roles/professor/routes'
import { secretariaRoutes } from '../roles/secretaria/routes'
import { LoginAdmin } from '../roles/super_admin/pages/LoginAdmin/LoginAdmin'
import { superAdminRoutes } from '../roles/super_admin/routes'
import { paths } from './paths'

export const router = createBrowserRouter([
  { path: '/', element: <Navigate to={paths.login} replace /> },
  { path: paths.login, element: <LoginAdmin /> },
  superAdminRoutes,
  masterRoutes,
  secretariaRoutes,
  financeiroRoutes,
  professorRoutes,
  alunoRoutes,
  { path: '*', element: <PaginaNaoEncontrada /> },
])
