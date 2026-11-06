import { Navigate, type RouteObject } from 'react-router'
import { paths } from '../../routes/paths'
import { RotaProtegida } from '../../routes/RotaProtegida'
import { Cobrancas } from './pages/Cobrancas/Cobrancas'
import { Despesas } from './pages/Despesas/Despesas'
import { Inicio } from './pages/Inicio/Inicio'
import { NovaCobranca } from './pages/NovaCobranca/NovaCobranca'
import { Relatorios } from './pages/Relatorios/Relatorios'

// Rota-mãe do perfil FINANCEIRO. As telas entram como children do RotaProtegida.
export const financeiroRoutes: RouteObject = {
  path: paths.financeiro.raiz,
  element: <RotaProtegida perfis={['FINANCEIRO']} />,
  children: [
    { index: true, element: <Navigate to={paths.financeiro.inicio} replace /> },
    { path: paths.financeiro.inicio, element: <Inicio /> },
    { path: paths.financeiro.cobrancas, element: <Cobrancas /> },
    { path: paths.financeiro.novaCobranca, element: <NovaCobranca /> },
    { path: paths.financeiro.despesas, element: <Despesas /> },
    { path: paths.financeiro.relatorios, element: <Relatorios /> },
  ],
}
