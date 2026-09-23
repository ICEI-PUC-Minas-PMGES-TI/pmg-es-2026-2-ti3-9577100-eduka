import { createBrowserRouter, Navigate } from 'react-router'
import { PaginaNaoEncontrada } from '../pages/PaginaNaoEncontrada/PaginaNaoEncontrada'
import { LoginAdmin } from '../roles/super_admin/pages/LoginAdmin/LoginAdmin'
import { superAdminRoutes } from '../roles/super_admin/routes'
import { paths } from './paths'

export const router = createBrowserRouter([
  { path: '/', element: <Navigate to={paths.login} replace /> },
  { path: paths.login, element: <LoginAdmin /> },
  superAdminRoutes,
  { path: '*', element: <PaginaNaoEncontrada /> },
])
