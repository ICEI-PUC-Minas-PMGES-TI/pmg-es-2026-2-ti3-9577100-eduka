import { useState } from 'react'
import { useNavigate } from 'react-router'
import { Button } from '../../../../components/Button/Button'
import { Colunas, EstadoDashboard, Indicadores, Medidor, Painel, PainelVazio } from '../../../../components/Dashboard/Dashboard'
import { StatCard } from '../../../../components/StatCard/StatCard'
import { paths } from '../../../../routes/paths'
import { obterDashboardSecretaria } from '../../../../services/dashboardService'
import { ROTULOS_TURNO } from '../../../../types/alocacao'
import type { DashboardSecretaria, OcupacaoTurma } from '../../../../types/dashboard'
import { ROTULOS_STATUS_MATRICULA, type StatusMatricula } from '../../../../types/matricula'
import { plural } from '../../../../utils/formatters'
import { useCarregamento } from '../../../professor/hooks/useCarregamento'
import { SecretariaLayout } from '../../layouts/SecretariaLayout'
import styles from './Inicio.module.css'

const SITUACOES: StatusMatricula[] = ['ATIVA', 'TRANCADA', 'CONCLUIDA', 'CANCELADA']
/** Turmas mostradas antes do "ver todas". */
const TURMAS_VISIVEIS = 6

/** Início da SECRETARIA: resumo de matrículas e ocupação das turmas. */
export function Inicio() {
  const { dados, carregando, erro, recarregar } = useCarregamento<DashboardSecretaria | null>(
    obterDashboardSecretaria,
    null,
    'Não foi possível encontrar os dados da escola.',
  )

  return (
    <SecretariaLayout titulo="Início" subtitulo="O resumo das matrículas e das vagas nas turmas.">
      <EstadoDashboard carregando={carregando} erro={erro} onTentarDeNovo={recarregar} />
      {dados && <Resumo dados={dados} />}
    </SecretariaLayout>
  )
}

function Resumo({ dados }: { dados: DashboardSecretaria }) {
  const navigate = useNavigate()
  const [todasAsTurmas, setTodasAsTurmas] = useState(false)
  const { matriculasPorSituacao, vagas, turmas } = dados

  const totalMatriculas = SITUACOES.reduce((soma, s) => soma + (matriculasPorSituacao[s] ?? 0), 0)
  const turmasVisiveis = todasAsTurmas ? turmas : turmas.slice(0, TURMAS_VISIVEIS)

  return (
    <>
      <Indicadores rotulo="Resumo de matrículas">
        <StatCard
          label="Matrículas ativas"
          value={matriculasPorSituacao.ATIVA ?? 0}
          descricao={`De ${totalMatriculas} ${plural(totalMatriculas, 'matrícula', 'matrículas')} no total`}
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
          label="Vagas livres"
          value={vagas.livres}
          descricao={
            vagas.turmasLotadas > 0
              ? `${vagas.turmasLotadas} ${plural(vagas.turmasLotadas, 'turma lotada', 'turmas lotadas')}`
              : `De ${vagas.ofertadas} ofertadas`
          }
        />
      </Indicadores>

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
