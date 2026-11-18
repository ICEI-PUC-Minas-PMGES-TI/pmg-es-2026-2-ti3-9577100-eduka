import { useNavigate } from 'react-router'
import { Button } from '../../../../components/ui/Button/Button'
import {
  Atalhos,
  Colunas,
  EstadoDashboard,
  Medidor,
  Numeros,
  Painel,
  PainelVazio,
  Pilha,
} from '../../../../components/layout/Dashboard/Dashboard'
import {
  EntrouESaiu,
  UltimasAcoes,
  VencemNaSemana,
} from '../../../../components/financeiro/PainelFinanceiro/PainelFinanceiro'
import { AlunosEmRisco } from '../../../../components/financeiro/RiscoAtraso/RiscoAtraso'
import { descricaoInadimplencia, quantasCobrancas } from '../../../../components/financeiro/PainelFinanceiro/textos'
import { StatCard, StatCards } from '../../../../components/ui/StatCard/StatCard'
import { useCarregamento } from '../../../../hooks/useCarregamento'
import { paths, type EstadoAbrirCadastro } from '../../../../routes/paths'
import { obterDashboardEscola, obterDashboardFinanceiro } from '../../../../services/dashboard/dashboardService'
import type { DashboardEscola, DashboardFinanceiro } from '../../../../types/dashboard/dashboard'
import { formatarMoeda, plural } from '../../../../utils/formatters'
import { MasterLayout } from '../../layouts/MasterLayout'
import styles from './Inicio.module.css'

type DadosInicio = { escola: DashboardEscola; financeiro: DashboardFinanceiro }

/** Os dois resumos juntos: a tela só aparece com os dois carregados. */
async function obterDadosInicio(signal: AbortSignal): Promise<DadosInicio> {
  const [escola, financeiro] = await Promise.all([obterDashboardEscola(signal), obterDashboardFinanceiro(signal)])
  return { escola, financeiro }
}

/** Início do MASTER: resumo da escola (pessoas, dinheiro, estrutura, vagas e desempenho). */
export function Inicio() {
  const navigate = useNavigate()
  const { dados, carregando, erro, recarregar } = useCarregamento<DadosInicio | null>(
    obterDadosInicio,
    null,
    'Não foi possível encontrar os dados da escola.',
  )

  return (
    <MasterLayout
      titulo="Início"
      subtitulo="O resumo da sua escola: alunos, dinheiro, turmas, vagas e desempenho."
      acao={
        <Button variant="outline" icon="card" onClick={() => navigate(paths.master.planoEFaturas)}>
          Plano e faturas
        </Button>
      }
    >
      <EstadoDashboard carregando={carregando} erro={erro} onTentarDeNovo={recarregar} />
      {dados && <Resumo escola={dados.escola} financeiro={dados.financeiro} />}
    </MasterLayout>
  )
}

function Resumo({ escola, financeiro }: DadosInicio) {
  const navigate = useNavigate()
  const { pessoas, estrutura, ocupacao, desempenho } = escola
  const { recebidoNoMes, faltaReceber, emAtraso } = financeiro
  const vagasLivres = Math.max(0, ocupacao.vagasOfertadas - ocupacao.matriculasAtivas)

  return (
    <>
      <StatCards rotulo="Resumo da escola">
        <StatCard
          label="Alunos matriculados"
          value={pessoas.alunosAtivos}
          descricao={`De ${pessoas.alunos} ${plural(pessoas.alunos, 'aluno cadastrado', 'alunos cadastrados')}`}
          destaque
        />
        <StatCard
          label="Recebido este mês"
          value={formatarMoeda(recebidoNoMes.valor)}
          descricao={quantasCobrancas(recebidoNoMes.quantidade)}
        />
        <StatCard
          label="Falta receber"
          value={formatarMoeda(faltaReceber.valor)}
          descricao={`${quantasCobrancas(faltaReceber.quantidade)} em aberto`}
        />
        <StatCard
          label="Pagamentos atrasados"
          value={emAtraso.alunos}
          descricao={`${plural(emAtraso.alunos, 'Aluno', 'Alunos')} · ${descricaoInadimplencia(emAtraso)}`}
          alerta={emAtraso.alunos > 0}
        />
      </StatCards>

      <Colunas esticar>
        <EntrouESaiu meses={financeiro.ultimosMeses} />
        <Pilha>
          {/* IU05: os atalhos levam às telas da secretaria e do financeiro dentro da gestão. */}
          <Atalhos
            titulo="O que você quer fazer?"
            itens={[
              { rotulo: 'Matricular um aluno', icon: 'check', onClick: () => navigate(paths.master.matriculas) },
              { rotulo: 'Lançar uma cobrança', icon: 'card', onClick: () => navigate(paths.master.novaCobranca) },
              {
                rotulo: 'Cadastrar um professor',
                icon: 'graduationCap',
                onClick: () =>
                  navigate(paths.master.professores, { state: { abrirCadastro: true } satisfies EstadoAbrirCadastro }),
              },
              { rotulo: 'Lançar uma despesa', icon: 'dollarSign', onClick: () => navigate(paths.master.despesas) },
            ]}
          />
          <Painel titulo="Ocupação das vagas" descricao="Matrículas ativas sobre as vagas das turmas ativas.">
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
                    {ocupacao.matriculasAtivas} de {ocupacao.vagasOfertadas} vagas · {vagasLivres}{' '}
                    {plural(vagasLivres, 'livre', 'livres')}
                  </span>
                </div>
              </div>
            )}
          </Painel>
        </Pilha>
      </Colunas>

      <Colunas esticar>
        <VencemNaSemana
          cobrancas={financeiro.vencemNaSemana}
          totalEmAtraso={emAtraso.cobrancas}
          acao={(aba) => (
            <Button
              size="sm"
              icon="arrowRight"
              onClick={() =>
                navigate(aba === 'EM_ATRASO' ? `${paths.master.cobrancas}?status=EM_ATRASO` : paths.master.cobrancas)
              }
            >
              Ver cobranças
            </Button>
          )}
        />
        <AlunosEmRisco />
      </Colunas>

      <Colunas esticar>
        <UltimasAcoes acoes={financeiro.ultimasAcoes} />
        <Painel titulo="A escola em números" descricao="Estrutura acadêmica, equipe e desempenho.">
          <Numeros
            itens={[
              {
                rotulo: 'Período letivo',
                valor: estrutura.periodoLetivoAtual ?? '—',
                detalhe: estrutura.periodoLetivoAtual ? 'Em andamento' : 'Nenhum em andamento',
              },
              {
                rotulo: 'Turmas ativas',
                valor: estrutura.turmasAtivas,
                detalhe: `de ${estrutura.turmas} ${plural(estrutura.turmas, 'turma', 'turmas')}`,
              },
              { rotulo: 'Cursos', valor: estrutura.cursos },
              { rotulo: 'Disciplinas', valor: estrutura.disciplinas },
              {
                rotulo: 'Professores',
                valor: pessoas.professoresAtivos,
                detalhe:
                  pessoas.professoresInativos > 0
                    ? `${pessoas.professoresInativos} ${plural(pessoas.professoresInativos, 'inativo', 'inativos')}`
                    : undefined,
              },
              { rotulo: 'Secretaria', valor: pessoas.secretarias },
              { rotulo: 'Financeiro', valor: pessoas.financeiro },
              { rotulo: 'Alunos sem turma', valor: pessoas.alunosSemTurma },
              { rotulo: 'Avaliações', valor: desempenho.avaliacoes },
              { rotulo: 'Notas lançadas', valor: desempenho.notasLancadas },
              {
                rotulo: 'Aproveitamento',
                valor: desempenho.aproveitamento === null ? '—' : `${desempenho.aproveitamento}%`,
                detalhe: desempenho.aproveitamento === null ? 'Sem notas' : 'Média das notas',
              },
            ]}
          />
        </Painel>
      </Colunas>
    </>
  )
}
