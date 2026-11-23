import { useState } from 'react'
import { useNavigate } from 'react-router'
import { Button } from '../../../../components/ui/Button/Button'
import { Icon } from '../../../../components/ui/Icon/Icon'
import { Paginacao } from '../../../../components/ui/Paginacao/Paginacao'
import { Select, type OpcaoSelect } from '../../../../components/formulario/Select/Select'
import { StatCard, StatCards } from '../../../../components/ui/StatCard/StatCard'
import { useCarregamento } from '../../../../hooks/useCarregamento'
import { usePaginacao } from '../../../../hooks/usePaginacao'
import { paths, type EstadoNovaAvaliacao } from '../../../../routes/paths'
import { obterDashboardProfessor } from '../../../../services/portal/portalProfessorService'
import type { AvaliacaoResumo } from '../../../../types/dashboard/dashboard'
import type { TurmaDoProfessor } from '../../../../types/academico/turmaProfessor'
import { plural } from '../../../../utils/formatters'
import { GradeSemanal } from '../../components/GradeSemanal/GradeSemanal'
import { ModalAlunosDaTurma } from '../../components/ModalAlunosDaTurma/ModalAlunosDaTurma'
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

/** Tela de avaliações já na turma, na primeira disciplina do professor nela. */
function avaliacoesDaTurma(turma: TurmaDoProfessor): string {
  const params = new URLSearchParams({ turma: String(turma.id) })
  if (turma.disciplinas[0]) params.set('disciplina', String(turma.disciplinas[0].id))
  return `${paths.professor.avaliacoes}?${params}`
}

/** Lançar notas: na avaliação mais antiga com nota faltando, ou na turma quando está tudo em dia. */
function lancarNotasDaTurma(turma: TurmaDoProfessor, pendentes: AvaliacaoResumo[]): string {
  const primeira = pendentes[0]
  if (!primeira) return avaliacoesDaTurma(turma)
  const params = new URLSearchParams({
    turma: String(primeira.turmaId),
    disciplina: String(primeira.disciplinaId),
    avaliacao: primeira.id,
  })
  return `${paths.professor.avaliacoes}?${params}`
}

const SEM_PENDENCIAS: AvaliacaoResumo[] = []

// Só as notas a lançar do resumo do professor; se ele falhar, a tela segue sem elas.
function buscarPendentes(signal: AbortSignal): Promise<AvaliacaoResumo[]> {
  return obterDashboardProfessor(signal).then((dashboard) => dashboard.avaliacoesPendentes)
}

export function MinhasTurmas() {
  const navigate = useNavigate()
  const { turmas, carregando, erro, recarregar } = useMinhasTurmas()
  // Avaliações já realizadas com aluno sem nota, da mais antiga para a mais recente.
  const { dados: pendentes } = useCarregamento(buscarPendentes, SEM_PENDENCIAS, '')
  const pendentesDa = (turma: TurmaDoProfessor) => pendentes.filter((p) => p.turmaId === turma.id)
  const [vendoAlunos, setVendoAlunos] = useState<TurmaDoProfessor | null>(null)
  const [busca, setBusca] = useState('')
  const [periodo, setPeriodo] = useState(TODOS_PERIODOS)

  const opcoesPeriodo = opcoesDePeriodo(turmas)
  const visiveis = turmas.filter(
    (t) =>
      (periodo === TODOS_PERIODOS || String(t.periodoLetivoId) === periodo) && correspondeBusca(t, busca),
  )
  const temTurmas = !carregando && !erro && turmas.length > 0
  const { paginacao, topo } = usePaginacao<TurmaDoProfessor, HTMLElement>(visiveis, { reiniciarCom: `${busca}|${periodo}` })

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
            <StatCard
              label="Notas a lançar"
              value={pendentes.length}
              descricao={
                pendentes.length > 0
                  ? plural(pendentes.length, 'Avaliação com nota faltando', 'Avaliações com nota faltando')
                  : 'Tudo em dia'
              }
              alerta={pendentes.length > 0}
            />
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

      {carregando && <div className={styles.mensagem} role="status">Carregando suas turmas…</div>}

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
        <>
          <section ref={topo} className={styles.grid} aria-label="Turmas">
            {paginacao.itensDaPagina.map((turma) => (
              <TurmaCard
                key={turma.id}
                turma={turma}
                pendentes={pendentesDa(turma)}
                onVerAlunos={setVendoAlunos}
                onLancarNotas={(t) => navigate(lancarNotasDaTurma(t, pendentesDa(t)))}
                onCriarAvaliacao={(t) =>
                  navigate(avaliacoesDaTurma(t), {
                    state: { abrirCadastro: true, turmaId: t.id } satisfies EstadoNovaAvaliacao,
                  })
                }
              />
            ))}
          </section>
          <Paginacao paginacao={paginacao} rotulo="Páginas das turmas" />
        </>
      )}

      {temTurmas && <GradeSemanal turmas={turmas} />}

      {vendoAlunos && <ModalAlunosDaTurma turma={vendoAlunos} onFechar={() => setVendoAlunos(null)} />}
    </ProfessorLayout>
  )
}
