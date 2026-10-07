import { useState } from 'react'
import { Button } from '../../../../components/Button/Button'
import { Icon } from '../../../../components/Icon/Icon'
import { Select, type OpcaoSelect } from '../../../../components/Select/Select'
import { StatCard, StatCards } from '../../../../components/StatCard/StatCard'
import type { TurmaDoProfessor } from '../../../../types/turmaProfessor'
import { TurmaCard } from '../../components/TurmaCard/TurmaCard'
import { useMinhasTurmas } from '../../hooks/useMinhasTurmas'
import { ProfessorLayout } from '../../layouts/ProfessorLayout'
import styles from './MinhasTurmas.module.css'

const TODOS_PERIODOS = ''

// Busca pelo nome/código da turma, pelo curso ou por uma das disciplinas.
function correspondeBusca(turma: TurmaDoProfessor, busca: string): boolean {
  const termo = busca.trim().toLowerCase()
  if (!termo) return true
  const textos = [
    turma.nome,
    turma.codigo,
    turma.cursoNome,
    ...turma.disciplinas.flatMap((d) => [d.nome, d.codigo]),
  ]
  return textos.some((texto) => texto?.toLowerCase().includes(termo))
}

function opcoesDePeriodo(turmas: TurmaDoProfessor[]): OpcaoSelect<string>[] {
  const periodos = new Map<string, string>()
  for (const turma of turmas) periodos.set(String(turma.periodoLetivoId), turma.periodoLetivoNome)
  return [
    { valor: TODOS_PERIODOS, rotulo: 'Todos os períodos' },
    ...[...periodos].map(([valor, rotulo]) => ({ valor, rotulo })),
  ]
}

export function MinhasTurmas() {
  const { turmas, carregando, erro, recarregar } = useMinhasTurmas()
  const [busca, setBusca] = useState('')
  const [periodo, setPeriodo] = useState(TODOS_PERIODOS)

  const opcoesPeriodo = opcoesDePeriodo(turmas)
  const visiveis = turmas.filter(
    (t) =>
      (periodo === TODOS_PERIODOS || String(t.periodoLetivoId) === periodo) && correspondeBusca(t, busca),
  )
  const temTurmas = !carregando && !erro && turmas.length > 0

  // A mesma disciplina pode ser dada em várias turmas: conta uma vez só.
  const disciplinas = new Set(turmas.flatMap((t) => t.disciplinas.map((d) => d.id))).size
  const cargaHoraria = turmas.reduce(
    (total, t) => total + t.disciplinas.reduce((soma, d) => soma + d.cargaHoraria, 0),
    0,
  )
  const ativas = turmas.filter((t) => t.ativa).length

  return (
    <ProfessorLayout titulo="Minhas turmas" subtitulo="As turmas e disciplinas em que você dá aula.">
      {temTurmas && (
        <>
          <StatCards rotulo="Resumo das suas turmas">
            <StatCard
              label="Turmas"
              value={turmas.length}
              descricao={ativas === turmas.length ? 'Todas ativas' : `${ativas} ativas`}
              destaque
            />
            <StatCard label="Disciplinas" value={disciplinas} descricao="Que você leciona" />
            <StatCard label="Carga horária" value={`${cargaHoraria} h`} descricao="Somando todas as turmas" />
          </StatCards>

          <div className={styles.toolbar}>
            <label className={styles.busca}>
              <Icon name="search" size={17} />
              <input
                type="search"
                placeholder="Procurar turma, curso ou disciplina"
                aria-label="Buscar turma, curso ou disciplina"
                value={busca}
                onChange={(e) => setBusca(e.target.value)}
              />
            </label>
            {/* Só vale filtrar quando há turmas em mais de um período. */}
            {opcoesPeriodo.length > 2 && (
              <Select
                opcoes={opcoesPeriodo}
                valor={periodo}
                onChange={setPeriodo}
                aria-label="Filtrar por período letivo"
                className={`${styles.filtro} ${periodo !== TODOS_PERIODOS ? styles.filtrando : ''}`}
              />
            )}
          </div>
        </>
      )}

      {carregando && <div className={styles.mensagem}>Carregando suas turmas…</div>}

      {!carregando && erro && (
        <div className={styles.mensagem} role="alert">
          <span>{erro}</span>
          <Button size="sm" onClick={recarregar}>
            Tentar novamente
          </Button>
        </div>
      )}

      {!carregando && !erro && turmas.length === 0 && (
        <div className={styles.mensagem}>
          <strong>Você ainda não tem turmas.</strong>
          <span>Assim que a secretaria alocar você em uma turma, ela aparece aqui.</span>
        </div>
      )}

      {temTurmas && visiveis.length === 0 && (
        <div className={styles.mensagem}>Nenhuma turma encontrada para o filtro.</div>
      )}

      {temTurmas && visiveis.length > 0 && (
        <section className={styles.grid} aria-label="Turmas">
          {visiveis.map((turma) => (
            <TurmaCard key={turma.id} turma={turma} />
          ))}
        </section>
      )}
    </ProfessorLayout>
  )
}
