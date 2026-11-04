import { useState } from 'react'
import { useNavigate } from 'react-router'
import { Button } from '../../../../components/Button/Button'
import {
  Atalhos,
  Colunas,
  EstadoDashboard,
  GraficoBarras,
  Medidor,
  Painel,
  PainelVazio,
} from '../../../../components/Dashboard/Dashboard'
import { StatCard, StatCards } from '../../../../components/StatCard/StatCard'
import { useCarregamento } from '../../../../hooks/useCarregamento'
import { paths, type EstadoAbrirCadastro } from '../../../../routes/paths'
import { obterDashboardSecretaria } from '../../../../services/dashboardService'
import { ROTULOS_TURNO } from '../../../../types/alocacao'
import type { DashboardSecretaria, OcupacaoTurma } from '../../../../types/dashboard'
import { ROTULOS_STATUS_MATRICULA, type StatusMatricula } from '../../../../types/matricula'
import { plural } from '../../../../utils/formatters'
import { PedidosCorrecao } from '../../components/PedidosCorrecao/PedidosCorrecao'
import { SecretariaLayout } from '../../layouts/SecretariaLayout'
import styles from './Inicio.module.css'

const SITUACOES: StatusMatricula[] = ['ATIVA', 'TRANCADA', 'CONCLUIDA', 'CANCELADA']
/** Turmas mostradas antes do "ver todas". */
const TURMAS_VISIVEIS = 6

/** Matrículas ativas somadas por curso, na ordem em que os cursos aparecem nas turmas. */
function matriculasPorCurso(turmas: OcupacaoTurma[]): { rotulo: string; valor: number }[] {
  const porCurso = new Map<string, number>()
  for (const turma of turmas) {
    porCurso.set(turma.cursoNome, (porCurso.get(turma.cursoNome) ?? 0) + turma.matriculasAtivas)
  }
  return [...porCurso].map(([rotulo, valor]) => ({ rotulo, valor }))
}

/** Início da SECRETARIA: resumo de matrículas, ocupação das turmas e atalhos do dia. */
export function Inicio() {
  const navigate = useNavigate()
  const { dados, carregando, erro, recarregar } = useCarregamento<DashboardSecretaria | null>(
    obterDashboardSecretaria,
    null,
    'Não foi possível encontrar os dados da escola.',
  )

  return (
    <SecretariaLayout titulo="Início" subtitulo="O resumo das matrículas e das vagas nas turmas.">
      <EstadoDashboard carregando={carregando} erro={erro} onTentarDeNovo={recarregar} />
      {dados && <Resumo dados={dados} />}
      <PedidosCorrecao onVerAlunos={() => navigate(paths.secretaria.alunos)} />
    </SecretariaLayout>
  )
}

function Resumo({ dados }: { dados: DashboardSecretaria }) {
  const navigate = useNavigate()
  const [todasAsTurmas, setTodasAsTurmas] = useState(false)
  const { matriculasPorSituacao, vagas, turmas } = dados

  const totalMatriculas = SITUACOES.reduce((soma, s) => soma + (matriculasPorSituacao[s] ?? 0), 0)
  const turmasVisiveis = todasAsTurmas ? turmas : turmas.slice(0, TURMAS_VISIVEIS)
  const porCurso = matriculasPorCurso(turmas)
  const periodos = [...new Set(turmas.map((t) => t.periodoLetivoNome))]

  return (
    <>
      <StatCards rotulo="Resumo de matrículas">
        <StatCard
          label="Matrículas ativas"
          value={matriculasPorSituacao.ATIVA ?? 0}
          descricao={`De ${vagas.ofertadas} ${plural(vagas.ofertadas, 'vaga aberta', 'vagas abertas')}`}
          destaque
        />
        <StatCard label="Novas matrículas" value={dados.matriculasUltimos30Dias} descricao="Nos últimos 30 dias" />
        <StatCard
          label="Alunos sem turma"
          value={dados.alunosSemTurma}
          descricao={dados.alunosSemTurma > 0 ? 'Sem matrícula ativa' : 'Todos matriculados'}
          alerta={dados.alunosSemTurma > 0}
        />
        <StatCard
          label="Trancamentos"
          value={matriculasPorSituacao.TRANCADA ?? 0}
          descricao={`De ${totalMatriculas} ${plural(totalMatriculas, 'matrícula', 'matrículas')} no total`}
        />
      </StatCards>

      <Colunas>
        <Painel titulo="Matrículas por curso" descricao={periodos.length === 1 ? periodos[0] : 'Turmas ativas'}>
          {porCurso.length === 0 ? (
            <PainelVazio>Nenhuma turma ativa no momento.</PainelVazio>
          ) : (
            <GraficoBarras
              rotulo="Matrículas ativas por curso"
              itens={porCurso.map((curso) => ({
                ...curso,
                legenda: `${curso.valor} ${plural(curso.valor, 'aluno', 'alunos')}`,
              }))}
            />
          )}
        </Painel>

        <Atalhos
          titulo="Atalhos do dia"
          itens={[
            {
              rotulo: 'Cadastrar um aluno',
              icon: 'plus',
              onClick: () =>
                navigate(paths.secretaria.alunos, { state: { abrirCadastro: true } satisfies EstadoAbrirCadastro }),
            },
            { rotulo: 'Matricular em uma turma', icon: 'check', onClick: () => navigate(paths.secretaria.matriculas) },
            {
              rotulo: 'Criar turma ou disciplina',
              icon: 'book',
              onClick: () => navigate(`${paths.secretaria.academico}?aba=turmas`),
            },
            { rotulo: 'Alocar professores', icon: 'graduationCap', onClick: () => navigate(paths.secretaria.alocacoes) },
          ]}
        />
      </Colunas>

      <Colunas>
        <Painel
          titulo="Matrículas por situação"
          acao={
            <Button size="sm" icon="arrowRight" onClick={() => navigate(paths.secretaria.matriculas)}>
              Ver matrículas
            </Button>
          }
        >
          {totalMatriculas === 0 ? (
            <PainelVazio>Nenhuma matrícula registrada ainda.</PainelVazio>
          ) : (
            <ul className={styles.lista}>
              {SITUACOES.map((situacao) => {
                const quantidade = matriculasPorSituacao[situacao] ?? 0
                return (
                  <li key={situacao} className={styles.situacao}>
                    <span className={styles.situacaoRotulo}>{ROTULOS_STATUS_MATRICULA[situacao]}</span>
                    <Medidor
                      valor={quantidade}
                      maximo={totalMatriculas}
                      rotulo={`Matrículas na situação ${ROTULOS_STATUS_MATRICULA[situacao]}`}
                    />
                    <strong className={styles.numero}>{quantidade}</strong>
                  </li>
                )
              })}
            </ul>
          )}
        </Painel>

        <Painel titulo="Ocupação das turmas" descricao="Turmas ativas, da mais cheia para a mais vazia.">
          {turmas.length === 0 ? (
            <PainelVazio>Nenhuma turma ativa no momento.</PainelVazio>
          ) : (
            <>
              <ul className={styles.lista}>
                {turmasVisiveis.map((turma) => (
                  <ItemTurma key={turma.turmaId} turma={turma} />
                ))}
              </ul>
              {turmas.length > TURMAS_VISIVEIS && (
                <Button size="sm" onClick={() => setTodasAsTurmas((v) => !v)}>
                  {todasAsTurmas ? 'Mostrar menos' : `Ver todas as ${turmas.length} turmas`}
                </Button>
              )}
            </>
          )}
        </Painel>
      </Colunas>
    </>
  )
}

function ItemTurma({ turma }: { turma: OcupacaoTurma }) {
  const lotada = turma.matriculasAtivas >= turma.vagas
  return (
    <li className={styles.turma}>
      <div className={styles.turmaTexto}>
        <strong>{turma.turmaNome}</strong>
        <span>
          {turma.cursoNome} · {turma.periodoLetivoNome} · {ROTULOS_TURNO[turma.turno]}
        </span>
      </div>
      <div className={styles.turmaOcupacao}>
        <Medidor
          valor={turma.matriculasAtivas}
          maximo={turma.vagas}
          rotulo={`Ocupação da turma ${turma.turmaNome}`}
          alerta={lotada}
        />
        <strong className={`${styles.numero} ${lotada ? styles.lotada : ''}`}>
          {turma.matriculasAtivas}/{turma.vagas}
        </strong>
      </div>
    </li>
  )
}
