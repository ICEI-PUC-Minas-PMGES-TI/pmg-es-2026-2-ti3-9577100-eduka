import { Colunas, EstadoDashboard, Linhas, Medidor, Painel, PainelVazio } from '../../../../components/Dashboard/Dashboard'
import { StatCard, StatCards } from '../../../../components/StatCard/StatCard'
import { obterDashboardEscola } from '../../../../services/dashboardService'
import type { DashboardEscola } from '../../../../types/dashboard'
import { plural } from '../../../../utils/formatters'
import { useCarregamento } from '../../../professor/hooks/useCarregamento'
import { MasterLayout } from '../../layouts/MasterLayout'
import styles from './Inicio.module.css'

/** Início do MASTER: resumo da escola (pessoas, estrutura, vagas e desempenho). */
export function Inicio() {
  const { dados, carregando, erro, recarregar } = useCarregamento<DashboardEscola | null>(
    obterDashboardEscola,
    null,
    'Não foi possível encontrar os dados da escola.',
  )

  return (
    <MasterLayout titulo="Início" subtitulo="O resumo da sua escola: pessoas, turmas, vagas e desempenho.">
      <EstadoDashboard carregando={carregando} erro={erro} onTentarDeNovo={recarregar} />
      {dados && <Resumo dados={dados} />}
    </MasterLayout>
  )
}

function Resumo({ dados }: { dados: DashboardEscola }) {
  const { pessoas, estrutura, ocupacao, desempenho } = dados
  const semTurma = pessoas.alunos - pessoas.alunosAtivos
  const vagasLivres = Math.max(0, ocupacao.vagasOfertadas - ocupacao.matriculasAtivas)

  return (
    <>
      <StatCards rotulo="Resumo da escola">
        <StatCard
          label="Alunos"
          value={pessoas.alunos}
          descricao={`${pessoas.alunosAtivos} com matrícula ativa`}
          destaque
        />
        <StatCard
          label="Professores"
          value={pessoas.professoresAtivos}
          descricao={
            pessoas.professoresInativos > 0
              ? `Ativos · ${pessoas.professoresInativos} ${plural(pessoas.professoresInativos, 'inativo', 'inativos')}`
              : 'Todos ativos'
          }
        />
        <StatCard
          label="Turmas ativas"
          value={estrutura.turmasAtivas}
          descricao={`De ${estrutura.turmas} ${plural(estrutura.turmas, 'cadastrada', 'cadastradas')}`}
        />
        <StatCard
          label="Aproveitamento"
          value={desempenho.aproveitamento === null ? '—' : `${desempenho.aproveitamento}%`}
          descricao={desempenho.aproveitamento === null ? 'Ainda sem notas lançadas' : 'Média geral das notas lançadas'}
        />
      </StatCards>

      <Painel
        titulo="Ocupação das vagas"
        descricao="Matrículas ativas sobre as vagas das turmas ativas."
      >
        {ocupacao.percentual === null ? (
          <PainelVazio>Nenhuma turma ativa com vagas ofertadas.</PainelVazio>
        ) : (
          <div className={styles.ocupacao}>
            <strong className={styles.percentual}>{ocupacao.percentual}%</strong>
            <div className={styles.barra}>
              <Medidor
                valor={ocupacao.matriculasAtivas}
                maximo={ocupacao.vagasOfertadas}
                rotulo="Ocupação das vagas da escola"
              />
              <span>
                {ocupacao.matriculasAtivas} de {ocupacao.vagasOfertadas} vagas ocupadas · {vagasLivres}{' '}
                {plural(vagasLivres, 'livre', 'livres')}
              </span>
            </div>
          </div>
        )}
      </Painel>

      <Colunas>
        <Painel titulo="Estrutura acadêmica">
          <Linhas
            itens={[
              { rotulo: 'Período letivo atual', valor: estrutura.periodoLetivoAtual ?? 'Nenhum em andamento' },
              { rotulo: 'Cursos', valor: estrutura.cursos },
              { rotulo: 'Disciplinas', valor: estrutura.disciplinas },
              { rotulo: 'Turmas', valor: `${estrutura.turmasAtivas} ativas de ${estrutura.turmas}` },
            ]}
          />
        </Painel>

        <Painel titulo="Pessoas">
          <Linhas
            itens={[
              { rotulo: 'Alunos sem turma', valor: semTurma },
              { rotulo: 'Professores', valor: pessoas.professoresAtivos + pessoas.professoresInativos },
              { rotulo: 'Secretaria', valor: pessoas.secretarias },
              { rotulo: 'Financeiro', valor: pessoas.financeiro },
            ]}
          />
        </Painel>
      </Colunas>

      <Painel titulo="Desempenho" descricao="Avaliações e notas de todas as turmas.">
        <Linhas
          itens={[
            { rotulo: 'Avaliações cadastradas', valor: desempenho.avaliacoes },
            { rotulo: 'Notas lançadas', valor: desempenho.notasLancadas },
            {
              rotulo: 'Aproveitamento geral',
              valor: desempenho.aproveitamento === null ? 'Sem notas' : `${desempenho.aproveitamento}%`,
            },
          ]}
        />
      </Painel>
    </>
  )
}
