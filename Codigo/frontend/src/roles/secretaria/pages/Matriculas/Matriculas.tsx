import { useState } from 'react'
import { useNavigate } from 'react-router'
import { Button } from '../../../../components/Button/Button'
import { paths } from '../../../../routes/paths'
import type { TurmaAcademica } from '../../../../types/academico'
import { ROTULOS_TURNO } from '../../../../types/alocacao'
import {
  ROTULOS_STATUS_MATRICULA,
  type Matricula,
  type StatusMatricula,
} from '../../../../types/matricula'
import { plural } from '../../../../utils/formatters'
import { AlterarMatriculaModal } from '../../components/AlterarMatriculaModal/AlterarMatriculaModal'
import { DetalhesMatriculaModal } from '../../components/DetalhesMatriculaModal/DetalhesMatriculaModal'
import {
  MatricularAlunosModal,
  type ResultadoMatriculas,
} from '../../components/MatricularAlunosModal/MatricularAlunosModal'
import { MatriculasTable } from '../../components/MatriculasTable/MatriculasTable'
import { TurmasMatricula } from '../../components/TurmasMatricula/TurmasMatricula'
import { useMatriculas } from '../../hooks/useMatriculas'
import { SecretariaLayout } from '../../layouts/SecretariaLayout'
import styles from './Matriculas.module.css'

const FILTROS: { valor: StatusMatricula | ''; rotulo: string }[] = [
  { valor: '', rotulo: 'Todas' },
  { valor: 'ATIVA', rotulo: 'Ativas' },
  { valor: 'TRANCADA', rotulo: 'Trancadas' },
  { valor: 'CANCELADA', rotulo: 'Canceladas' },
  { valor: 'CONCLUIDA', rotulo: 'Concluídas' },
]

function porNomeDoAluno(a: Matricula, b: Matricula): number {
  return (a.alunoNome ?? '').localeCompare(b.alunoNome ?? '', 'pt-BR')
}

export function Matriculas() {
  const navigate = useNavigate()
  const { turmas, alunos, matriculas, carregando, erro, recarregar, matricular, atualizar } = useMatriculas()
  const [turmaId, setTurmaId] = useState<number | null>(null)
  const [filtro, setFiltro] = useState<StatusMatricula | ''>('')
  const [matriculando, setMatriculando] = useState(false)
  const [alterando, setAlterando] = useState<Matricula | null>(null)
  const [vendo, setVendo] = useState<Matricula | null>(null)
  const [sucesso, setSucesso] = useState<string | null>(null)
  const [erroAcao, setErroAcao] = useState<string | null>(null)

  // Sem escolha feita (ou com a turma escolhida fora da lista), abre na primeira ativa.
  const turma = turmas.find((t) => t.id === turmaId) ?? turmas.find((t) => t.ativa) ?? turmas[0] ?? null

  const ativasPorTurma = new Map<number, number>()
  for (const m of matriculas) {
    if (m.status === 'ATIVA') ativasPorTurma.set(m.turmaId, (ativasPorTurma.get(m.turmaId) ?? 0) + 1)
  }

  const daTurma = turma ? matriculas.filter((m) => m.turmaId === turma.id).sort(porNomeDoAluno) : []
  const visiveis = filtro ? daTurma.filter((m) => m.status === filtro) : daTurma
  const ativas = turma ? (ativasPorTurma.get(turma.id) ?? 0) : 0
  const vagasLivres = turma ? Math.max(turma.vagas - ativas, 0) : 0
  const podeMatricular = Boolean(turma?.ativa) && vagasLivres > 0

  function limparAvisos() {
    setSucesso(null)
    setErroAcao(null)
  }

  function handleSelecionar(selecionada: TurmaAcademica) {
    limparAvisos()
    setFiltro('')
    setTurmaId(selecionada.id)
  }

  function handleMatriculados({ matriculados, falhas }: ResultadoMatriculas) {
    setMatriculando(false)
    setSucesso(
      matriculados.length > 0
        ? `${matriculados.length} ${plural(matriculados.length, 'aluno matriculado', 'alunos matriculados')} na turma ${turma?.nome}.`
        : null,
    )
    setErroAcao(
      falhas.length > 0
        ? `Não foi possível matricular: ${falhas.map((f) => `${f.nome} (${f.motivo})`).join('; ')}`
        : null,
    )
  }

  function handleAlterado(mensagem: string) {
    setAlterando(null)
    setErroAcao(null)
    setSucesso(mensagem)
  }

  return (
    <SecretariaLayout titulo="Matrículas" subtitulo="Escolha a turma e matricule os alunos.">
      {carregando && <p className={styles.estado}>Carregando turmas, alunos e matrículas…</p>}

      {!carregando && erro && (
        <div className={styles.estado}>
          <span>{erro}</span>
          <Button size="sm" onClick={recarregar}>
            Tentar novamente
          </Button>
        </div>
      )}

      {!carregando && !erro && !turma && (
        <div className={styles.estado}>
          <span>Nenhuma turma cadastrada ainda. Cadastre uma turma para matricular os alunos.</span>
          <Button size="sm" onClick={() => navigate(`${paths.secretaria.academico}?aba=turmas`)}>
            Ir para Turmas
          </Button>
        </div>
      )}

      {!carregando && !erro && turma && (
        <div className={styles.colunas}>
          <div className={styles.turmas}>
            <TurmasMatricula
              turmas={turmas}
              turmaSelecionadaId={turma.id}
              ativasPorTurma={ativasPorTurma}
              onSelecionar={handleSelecionar}
            />
          </div>

          <div className={styles.painel}>
            <section className={styles.cabecalho} aria-label={`Turma ${turma.nome}`}>
              <div className={styles.turmaInfo}>
                <h2 className={styles.turmaNome}>Turma {turma.nome}</h2>
                <span className={styles.turmaDetalhes}>
                  {turma.cursoNome} · {turma.periodoLetivoNome} · {ROTULOS_TURNO[turma.turno]}
                </span>
              </div>
              <dl className={styles.numeros}>
                <div>
                  <dt>Matriculados</dt>
                  <dd>
                    {ativas}
                    <small>/{turma.vagas}</small>
                  </dd>
                </div>
                <div>
                  <dt>Vagas livres</dt>
                  <dd className={vagasLivres === 0 ? styles.esgotado : undefined}>{vagasLivres}</dd>
                </div>
              </dl>
              <Button
                variant="primary"
                icon="plus"
                disabled={!podeMatricular}
                title={
                  !turma.ativa ? 'Turma inativa' : vagasLivres === 0 ? 'Turma sem vagas livres' : undefined
                }
                onClick={() => {
                  limparAvisos()
                  setMatriculando(true)
                }}
              >
                Matricular alunos
              </Button>
            </section>

            {sucesso && (
              <p className={styles.sucesso} role="status">
                {sucesso}
              </p>
            )}
            {erroAcao && (
              <p className={styles.alerta} role="alert">
                {erroAcao}
              </p>
            )}

            <div className={styles.filtros} role="group" aria-label="Filtrar por situação">
              {FILTROS.map((f) => {
                const quantidade = f.valor ? daTurma.filter((m) => m.status === f.valor).length : daTurma.length
                return (
                  <button
                    key={f.rotulo}
                    type="button"
                    aria-pressed={filtro === f.valor}
                    className={`${styles.filtro} ${filtro === f.valor ? styles.filtroAtivo : ''}`}
                    onClick={() => setFiltro(f.valor)}
                  >
                    {f.rotulo}
                    <span className={styles.filtroQuantidade}>{quantidade}</span>
                  </button>
                )
              })}
            </div>

            <MatriculasTable
              matriculas={visiveis}
              mensagemVazia={
                daTurma.length === 0
                  ? 'Nenhum aluno matriculado nesta turma ainda.'
                  : `Nenhuma matrícula ${filtro ? ROTULOS_STATUS_MATRICULA[filtro].toLowerCase() : ''} nesta turma.`
              }
              onVer={setVendo}
              onAlterar={(m) => {
                limparAvisos()
                setAlterando(m)
              }}
            />
          </div>
        </div>
      )}

      {matriculando && turma && (
        <MatricularAlunosModal
          turma={turma}
          alunos={alunos}
          matriculas={matriculas}
          vagasLivres={vagasLivres}
          onFechar={() => setMatriculando(false)}
          onMatricular={matricular}
          onConcluido={handleMatriculados}
        />
      )}

      {vendo && <DetalhesMatriculaModal matricula={vendo} onFechar={() => setVendo(null)} />}

      {alterando && (
        <AlterarMatriculaModal
          matricula={alterando}
          turmas={turmas}
          matriculas={matriculas}
          onFechar={() => setAlterando(null)}
          onAtualizar={atualizar}
          onConcluido={handleAlterado}
        />
      )}
    </SecretariaLayout>
  )
}
