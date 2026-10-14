import { Navigate, type RouteObject } from 'react-router'
import { paths } from '../../routes/paths'
import { RotaProtegida } from '../../routes/RotaProtegida'
import { Cobrancas } from './pages/Cobrancas/Cobrancas'

// Rota-mãe do perfil FINANCEIRO. As telas entram como children do RotaProtegida.
export const financeiroRoutes: RouteObject = {
  path: paths.financeiro.raiz,
  element: <RotaProtegida perfis={['FINANCEIRO']} />,
  children: [
    { index: true, element: <Navigate to={paths.financeiro.cobrancas} replace /> },
    { path: paths.financeiro.cobrancas, element: <Cobrancas /> },
  ],
}
