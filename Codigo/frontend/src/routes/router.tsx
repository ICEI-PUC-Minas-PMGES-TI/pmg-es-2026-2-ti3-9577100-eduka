import { createBrowserRouter } from 'react-router'
import { Home } from '../pages/Home/Home'
import { Login } from '../pages/Login/Login'
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
  { path: '/', element: <Home /> },
  { path: paths.login, element: <Login /> },
  { path: paths.loginAdmin, element: <LoginAdmin /> },
  // Login de um clique por perfil. Em produção DEV é false e o Vite remove
  // este trecho, junto com o chunk da tela: /login/dev cai no 404.
  ...(import.meta.env.DEV
    ? [
        {
          path: paths.loginDev,
          lazy: async () => ({ Component: (await import('../pages/LoginDev/LoginDev')).LoginDev }),
        },
      ]
    : []),
  // Acesso de demonstração, protegido pelo backend (perfil demo). Fora do
  // build de apresentação a rota não existe, como o /login/dev.
  ...(import.meta.env.DEV || import.meta.env.VITE_DEMO === 'true'
    ? [
        {
          path: paths.loginDemo,
          lazy: async () => ({ Component: (await import('../pages/LoginDemo/LoginDemo')).LoginDemo }),
        },
      ]
    : []),
  superAdminRoutes,
  masterRoutes,
  secretariaRoutes,
  financeiroRoutes,
  professorRoutes,
  alunoRoutes,
  { path: '*', element: <PaginaNaoEncontrada /> },
])
