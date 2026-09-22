import { createBrowserRouter, Navigate } from 'react-router'
import { PaginaNaoEncontrada } from '../pages/PaginaNaoEncontrada/PaginaNaoEncontrada'
import { superAdminRoutes } from '../roles/super_admin/routes'
import { paths } from './paths'

export const router = createBrowserRouter([
  // TODO: quando o login existir, "/" deve levar ao login ou ao painel do perfil logado.
  { path: '/', element: <Navigate to={paths.superAdmin.escolas} replace /> },
  superAdminRoutes,
  { path: '*', element: <PaginaNaoEncontrada /> },
])
