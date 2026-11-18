import { useNavigate } from 'react-router'
import { Button } from '../../../../components/ui/Button/Button'
import { Icon } from '../../../../components/ui/Icon/Icon'
import { Atalhos, EstadoDashboard, Medidor, Painel, PainelVazio } from '../../../../components/layout/Dashboard/Dashboard'
import { StatCard, StatCards } from '../../../../components/ui/StatCard/StatCard'
import { useCarregamento } from '../../../../hooks/useCarregamento'
import { paths, type EstadoAbrirCadastro } from '../../../../routes/paths'
import { obterDashboardSecretaria } from '../../../../services/dashboard/dashboardService'
import { ROTULOS_TURNO } from '../../../../types/academico/alocacao'
import type { DashboardSecretaria, OcupacaoTurma } from '../../../../types/dashboard/dashboard'
import { ROTULOS_STATUS_MATRICULA, type StatusMatricula } from '../../../../types/academico/matricula'
import { plural } from '../../../../utils/formatters'
import { PedidosCorrecao } from '../../components/alunos/PedidosCorrecao/PedidosCorrecao'
import { SecretariaLayout } from '../../layouts/SecretariaLayout'
import styles from './Inicio.module.css'

/** Ordem da legenda da situação das matrículas; a cor de cada uma vem do CSS. */
const SITUACOES: StatusMatricula[] = ['ATIVA', 'CONCLUIDA', 'TRANCADA', 'CANCELADA']
const COR_SITUACAO: Record<StatusMatricula, string> = {
  ATIVA: styles.ativa,
  CONCLUIDA: styles.concluida,
  TRANCADA: styles.trancada,
  CANCELADA: styles.cancelada,
}

type Curso = {
  nome: string
  turmas: OcupacaoTurma[]
  alunos: number
  vagas: number
}

/** Turmas agrupadas por curso, na ordem em que os cursos aparecem; dentro do curso, pelo nome da turma. */
function agruparPorCurso(turmas: OcupacaoTurma[]): Curso[] {
  const porCurso = new Map<string, Curso>()
  for (const turma of turmas) {
    const curso = porCurso.get(turma.cursoNome) ?? { nome: turma.cursoNome, turmas: [], alunos: 0, vagas: 0 }
    curso.turmas.push(turma)
    curso.alunos += turma.matriculasAtivas
    curso.vagas += turma.vagas
    porCurso.set(turma.cursoNome, curso)
  }
  const cursos = [...porCurso.values()]
  for (const curso of cursos) {
    curso.turmas.sort((a, b) => a.turmaNome.localeCompare(b.turmaNome, 'pt-BR', { numeric: true }))
  }
  return cursos
}

/**
 * Início da SECRETARIA (IU14): o que pede ação primeiro (pendências e pedidos de
 * correção), os números de matrícula e a ocupação de cada turma, por curso.
 */
export function Inicio() {
  const { dados, carregando, erro, recarregar } = useCarregamento<DashboardSecretaria | null>(
    obterDashboardSecretaria,
    null,
    'Não foi possível encontrar os dados da escola.',
  )

  return (
    <SecretariaLayout titulo="Início" subtitulo="O que precisa de você hoje, as matrículas e as vagas nas turmas.">
      <EstadoDashboard carregando={carregando} erro={erro} onTentarDeNovo={recarregar} />
      {dados && <Resumo dados={dados} />}
    </SecretariaLayout>
  )
}

function Resumo({ dados }: { dados: DashboardSecretaria }) {
  const navigate = useNavigate()
  const { matriculasPorSituacao, vagas, turmas } = dados
  const ativas = matriculasPorSituacao.ATIVA ?? 0
  const ocupacao = vagas.ofertadas > 0 ? Math.round((vagas.ocupadas / vagas.ofertadas) * 100) : 0
  const periodos = [...new Set(turmas.map((t) => t.periodoLetivoNome))]

  return (
    <>
      <Pendencias dados={dados} />

      <StatCards rotulo="Resumo de matrículas">
        <StatCard
          label="Alunos matriculados"
          value={ativas}
          descricao={
            turmas.length === 0
              ? 'Nenhuma turma ativa'
              : `Em ${turmas.length} ${plural(turmas.length, 'turma ativa', 'turmas ativas')}`
          }
          destaque
        />
        <StatCard
          label="Vagas ocupadas"
          value={`${ocupacao}%`}
          descricao={
            vagas.ofertadas === 0
              ? 'Nenhuma vaga aberta'
              : `${vagas.ocupadas} de ${vagas.ofertadas} · ${vagas.livres} ${plural(vagas.livres, 'livre', 'livres')}`
          }
        />
        <StatCard label="Novas matrículas" value={dados.matriculasUltimos30Dias} descricao="Nos últimos 30 dias" />
      </StatCards>

      <Atalhos
        titulo="Atalhos do dia"
        horizontal
        itens={[
          {
            rotulo: 'Cadastrar aluno',
            icon: 'plus',
            onClick: () =>
              navigate(paths.secretaria.alunos, { state: { abrirCadastro: true } satisfies EstadoAbrirCadastro }),
          },
          { rotulo: 'Matricular aluno', icon: 'check', onClick: () => navigate(paths.secretaria.matriculas) },
          {
            rotulo: 'Criar turma',
            icon: 'book',
            onClick: () => navigate(`${paths.secretaria.academico}?aba=turmas`),
          },
          { rotulo: 'Alocar professores', icon: 'graduationCap', onClick: () => navigate(paths.secretaria.alocacoes) },
        ]}
      />

      <div className={styles.grade}>
        <div className={styles.areaPedidos}>
          <PedidosCorrecao onVerAlunos={() => navigate(paths.secretaria.alunos)} />
        </div>
        <div className={styles.areaTurmas}>
          <OcupacaoDasTurmas turmas={turmas} periodos={periodos} />
        </div>
        <div className={styles.areaSituacao}>
          <SituacaoDasMatriculas porSituacao={matriculasPorSituacao} />
        </div>
      </div>
    </>
  )
}

/** Faixa do que pede ação; some quando está tudo em dia. */
function Pendencias({ dados }: { dados: DashboardSecretaria }) {
  const navigate = useNavigate()
  const semTurma = dados.alunosSemTurma
  const lotadas = dados.vagas.turmasLotadas
  if (semTurma === 0 && lotadas === 0) return null

  return (
    <section className={styles.pendencias} aria-label="Para resolver">
      <h2 className={styles.pendenciasTitulo}>
        <Icon name="alert" size={16} />
        Para resolver
      </h2>
      <ul className={styles.pendenciasLista}>
        {semTurma > 0 && (
          <li className={styles.pendencia}>
            <span>
              <strong>{semTurma}</strong> {plural(semTurma, 'aluno cadastrado está', 'alunos cadastrados estão')} sem
              turma
            </span>
            <Button size="sm" icon="arrowRight" onClick={() => navigate(paths.secretaria.matriculas)}>
              Matricular
            </Button>
          </li>
        )}
        {lotadas > 0 && (
          <li className={styles.pendencia}>
            <span>
              <strong>{lotadas}</strong> {plural(lotadas, 'turma está lotada', 'turmas estão lotadas')}
            </span>
            <Button
              size="sm"
              icon="arrowRight"
              onClick={() => navigate(`${paths.secretaria.academico}?aba=turmas`)}
            >
              Ver turmas
            </Button>
          </li>
        )}
      </ul>
    </section>
  )
}

function OcupacaoDasTurmas({ turmas, periodos }: { turmas: OcupacaoTurma[]; periodos: string[] }) {
  const cursos = agruparPorCurso(turmas)
  return (
    <Painel
      titulo="Ocupação das turmas"
      descricao={periodos.length === 1 ? `Turmas ativas de ${periodos[0]}, por curso.` : 'Turmas ativas, por curso.'}
    >
      {cursos.length === 0 ? (
        <PainelVazio>Nenhuma turma ativa no momento.</PainelVazio>
      ) : (
        <div className={styles.cursos}>
          {cursos.map((curso) => (
            <section key={curso.nome} className={styles.curso} aria-label={curso.nome}>
              <header className={styles.cursoTopo}>
                <h3 className={styles.cursoNome}>{curso.nome}</h3>
                <span className={styles.cursoResumo}>
                  {curso.alunos} {plural(curso.alunos, 'aluno', 'alunos')} · {curso.turmas.length}{' '}
                  {plural(curso.turmas.length, 'turma', 'turmas')} · {Math.max(curso.vagas - curso.alunos, 0)}{' '}
                  {plural(curso.vagas - curso.alunos, 'vaga livre', 'vagas livres')}
                </span>
              </header>
              <ul className={styles.turmas}>
                {curso.turmas.map((turma) => (
                  <LinhaTurma key={turma.turmaId} turma={turma} />
                ))}
              </ul>
            </section>
          ))}
        </div>
      )}
    </Painel>
  )
}

function LinhaTurma({ turma }: { turma: OcupacaoTurma }) {
  const lotada = turma.matriculasAtivas >= turma.vagas
  return (
    <li className={styles.turma}>
      <span className={styles.turmaNome}>
        <strong>{turma.turmaNome}</strong>
        <span className={styles.turno}>{ROTULOS_TURNO[turma.turno]}</span>
      </span>
      <Medidor
        valor={turma.matriculasAtivas}
        maximo={turma.vagas}
        rotulo={`Ocupação da turma ${turma.turmaNome}`}
        alerta={lotada}
      />
      <span className={`${styles.turmaNumero} ${lotada ? styles.lotada : ''}`}>
        {turma.matriculasAtivas}/{turma.vagas}
      </span>
    </li>
  )
}

/** Uma barra só, dividida pela situação, com a legenda abaixo. */
function SituacaoDasMatriculas({ porSituacao }: { porSituacao: Record<StatusMatricula, number> }) {
  const navigate = useNavigate()
  const total = SITUACOES.reduce((soma, s) => soma + (porSituacao[s] ?? 0), 0)

  return (
    <Painel
      titulo="Situação das matrículas"
      descricao={`${total} ${plural(total, 'matrícula', 'matrículas')} de todos os períodos.`}
      acao={
        <Button size="sm" icon="arrowRight" onClick={() => navigate(paths.secretaria.matriculas)}>
          Ver matrículas
        </Button>
      }
    >
      {total === 0 ? (
        <PainelVazio>Nenhuma matrícula registrada ainda.</PainelVazio>
      ) : (
        <>
          <div
            className={styles.barraSituacao}
            role="img"
            aria-label={SITUACOES.map((s) => `${ROTULOS_STATUS_MATRICULA[s]}: ${porSituacao[s] ?? 0}`).join(', ')}
          >
            {SITUACOES.filter((s) => (porSituacao[s] ?? 0) > 0).map((s) => (
              <span
                key={s}
                className={`${styles.segmento} ${COR_SITUACAO[s]}`}
                style={{ flexGrow: porSituacao[s] }}
              />
            ))}
          </div>
          <ul className={styles.legenda}>
            {SITUACOES.map((s) => {
              const quantidade = porSituacao[s] ?? 0
              return (
                <li key={s} className={quantidade === 0 ? styles.legendaZerada : ''}>
                  <span className={`${styles.amostra} ${COR_SITUACAO[s]}`} aria-hidden="true" />
                  <span className={styles.legendaRotulo}>{ROTULOS_STATUS_MATRICULA[s]}</span>
                  <strong>{quantidade}</strong>
                </li>
              )
            })}
          </ul>
        </>
      )}
    </Painel>
  )
}
