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
import { temModulo } from '../../../../services/acesso/modulosSessao'
import { formatarMoeda, plural } from '../../../../utils/formatters'
import { MasterLayout } from '../../layouts/MasterLayout'
import styles from './Inicio.module.css'

/** `financeiro` é nulo quando a escola não contratou a gestão financeira. */
type DadosInicio = { escola: DashboardEscola; financeiro: DashboardFinanceiro | null }

/** Os dois resumos juntos: a tela só aparece com os dois carregados. */
async function obterDadosInicio(signal: AbortSignal): Promise<DadosInicio> {
  const [escola, financeiro] = await Promise.all([
    obterDashboardEscola(signal),
    temModulo('GESTAO_FINANCEIRA') ? obterDashboardFinanceiro(signal) : Promise.resolve(null),
  ])
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
      subtitulo={
        temModulo('GESTAO_FINANCEIRA')
          ? 'O resumo da sua escola: alunos, dinheiro, turmas, vagas e desempenho.'
          : 'O resumo da sua escola: alunos, turmas, vagas e desempenho.'
      }
      acao={
        <Button variant="outline" icon="card" onClick={() => navigate(paths.master.planoEFaturas)}>
          Plano e faturas
        </Button>
      }
    >
      <EstadoDashboard carregando={carregando} erro={erro} onTentarDeNovo={recarregar} />
      {dados &&
        (dados.financeiro ? (
          <Resumo escola={dados.escola} financeiro={dados.financeiro} />
        ) : (
          <ResumoSemFinanceiro escola={dados.escola} />
        ))}
    </MasterLayout>
  )
}

function Resumo({ escola, financeiro }: { escola: DashboardEscola; financeiro: DashboardFinanceiro }) {
  const navigate = useNavigate()
  const { pessoas } = escola
  const { recebidoNoMes, faltaReceber, emAtraso } = financeiro

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
          <AtalhosDaGestao comFinanceiro />
          <OcupacaoDasVagas escola={escola} />
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
        <EscolaEmNumeros escola={escola} />
      </Colunas>
    </>
  )
}

/**
 * Escola sem a gestão financeira contratada: o resumo fica com o acadêmico,
 * sem os cards de dinheiro, as cobranças e os atalhos do financeiro.
 */
function ResumoSemFinanceiro({ escola }: { escola: DashboardEscola }) {
  const { pessoas, estrutura } = escola

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
          label="Turmas ativas"
          value={estrutura.turmasAtivas}
          descricao={`De ${estrutura.turmas} ${plural(estrutura.turmas, 'turma', 'turmas')}`}
        />
        <StatCard label="Professores" value={pessoas.professoresAtivos} descricao="Com cadastro ativo" />
        <StatCard
          label="Alunos sem turma"
          value={pessoas.alunosSemTurma}
          descricao="Cadastrados sem matrícula ativa"
          alerta={pessoas.alunosSemTurma > 0}
        />
      </StatCards>

      <Colunas esticar>
        <AtalhosDaGestao comFinanceiro={false} />
        <OcupacaoDasVagas escola={escola} />
      </Colunas>

      <EscolaEmNumeros escola={escola} />
    </>
  )
}

/** IU05: os atalhos levam às telas da secretaria e do financeiro dentro da gestão. */
function AtalhosDaGestao({ comFinanceiro }: { comFinanceiro: boolean }) {
  const navigate = useNavigate()
  const matricular = {
    rotulo: 'Matricular um aluno',
    icon: 'check' as const,
    onClick: () => navigate(paths.master.matriculas),
  }
  const cadastrarProfessor = {
    rotulo: 'Cadastrar um professor',
    icon: 'graduationCap' as const,
    onClick: () => navigate(paths.master.professores, { state: { abrirCadastro: true } satisfies EstadoAbrirCadastro }),
  }
  return (
    <Atalhos
      titulo="O que você quer fazer?"
      itens={
        comFinanceiro
          ? [
              matricular,
              { rotulo: 'Lançar uma cobrança', icon: 'card', onClick: () => navigate(paths.master.novaCobranca) },
              cadastrarProfessor,
              { rotulo: 'Lançar uma despesa', icon: 'dollarSign', onClick: () => navigate(`${paths.master.despesas}?nova=1`) },
            ]
          : [
              matricular,
              cadastrarProfessor,
              { rotulo: 'Ver alunos e turmas', icon: 'book', onClick: () => navigate(paths.master.alunos) },
              { rotulo: 'Gerir a equipe', icon: 'users', onClick: () => navigate(paths.master.equipe) },
            ]
      }
    />
  )
}

function OcupacaoDasVagas({ escola }: { escola: DashboardEscola }) {
  const { ocupacao } = escola
  const vagasLivres = Math.max(0, ocupacao.vagasOfertadas - ocupacao.matriculasAtivas)
  return (
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
  )
}

function EscolaEmNumeros({ escola }: { escola: DashboardEscola }) {
  const { pessoas, estrutura, desempenho } = escola
  return (
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
  )
}
