import { Navigate, type RouteObject } from 'react-router'
import { paths } from '../../routes/paths'
import { RotaProtegida } from '../../routes/RotaProtegida'
import { Cobrancas } from '../financeiro/pages/Cobrancas/Cobrancas'
import { Despesas } from '../financeiro/pages/Despesas/Despesas'
import { NovaCobranca } from '../financeiro/pages/NovaCobranca/NovaCobranca'
import { Relatorios } from '../financeiro/pages/Relatorios/Relatorios'
import { SecretariaDashboard } from '../secretaria/components/PainelSecretaria/SecretariaDashboard'
import { Alocacoes } from '../secretaria/pages/Alocacoes/Alocacoes'
import { Alunos } from '../secretaria/pages/Alunos/Alunos'
import { Matriculas } from '../secretaria/pages/Matriculas/Matriculas'
import { Professores } from '../secretaria/pages/Professores/Professores'
import { Equipe } from './pages/Equipe/Equipe'
import { Inicio } from './pages/Inicio/Inicio'
import { PlanoEFaturas } from './pages/PlanoEFaturas/PlanoEFaturas'
import { Recebimentos } from './pages/Recebimentos/Recebimentos'

// Rota-mãe do perfil MASTER. As telas entram como children do RotaProtegida.
// Alunos, matrículas, cobranças e despesas são as páginas da secretaria e do
// financeiro: dentro de /master elas usam o menu da gestão (routes/areas.ts).
export const masterRoutes: RouteObject = {
  path: paths.master.raiz,
  element: <RotaProtegida perfis={['MASTER']} />,
  children: [
    { index: true, element: <Navigate to={paths.master.inicio} replace /> },
    { path: paths.master.inicio, element: <Inicio /> },
    { path: paths.master.recebimentos, element: <Recebimentos /> },
    { path: paths.master.planoEFaturas, element: <PlanoEFaturas /> },
    { path: paths.master.equipe, element: <Equipe /> },
    { path: paths.master.alunos, element: <Alunos /> },
    { path: paths.master.professores, element: <Professores /> },
    { path: paths.master.academico, element: <SecretariaDashboard /> },
    { path: paths.master.alocacoes, element: <Alocacoes /> },
    { path: paths.master.matriculas, element: <Matriculas /> },
    { path: paths.master.cobrancas, element: <Cobrancas /> },
    { path: paths.master.novaCobranca, element: <NovaCobranca /> },
    { path: paths.master.despesas, element: <Despesas /> },
    { path: paths.master.relatorios, element: <Relatorios /> },
  ],
}
