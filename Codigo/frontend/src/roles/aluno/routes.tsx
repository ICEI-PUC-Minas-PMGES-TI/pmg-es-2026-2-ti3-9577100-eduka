import { Navigate, type RouteObject } from 'react-router'
import { paths } from '../../routes/paths'
import { RotaProtegida } from '../../routes/RotaProtegida'
import { Avaliacoes } from './pages/Avaliacoes/Avaliacoes'
import { Boletim } from './pages/Boletim/Boletim'
import { MeusDados } from './pages/MeusDados/MeusDados'

// Rota-mãe do perfil ALUNO. As telas entram como children do RotaProtegida.
export const alunoRoutes: RouteObject = {
  path: paths.aluno.raiz,
  element: <RotaProtegida perfis={['ALUNO']} />,
  children: [
    // Enquanto o "Início" não existe, o aluno cai direto no boletim.
    { index: true, element: <Navigate to={paths.aluno.boletim} replace /> },
    { path: paths.aluno.boletim, element: <Boletim /> },
    { path: paths.aluno.avaliacoes, element: <Avaliacoes /> },
    { path: paths.aluno.meusDados, element: <MeusDados /> },
  ],
}
