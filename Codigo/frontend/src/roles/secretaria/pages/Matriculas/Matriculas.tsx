import { useMemo, useState } from 'react'
import { useNavigate } from 'react-router'
import { Button } from '../../../../components/Button/Button'
import { StatCard, StatCards } from '../../../../components/StatCard/StatCard'
import { useCarregamento } from '../../../../hooks/useCarregamento'
import { useRotasAcademicas } from '../../../../routes/areas'
import { atualizarMatricula, listarMatriculas, matricularAluno } from '../../../../services/matriculaService'
import type { TurmaAcademica } from '../../../../types/academico'
import { ROTULOS_TURNO } from '../../../../types/alocacao'
import {
  ROTULOS_STATUS_MATRICULA,
  type AtualizarMatriculaRequest,
  type Matricula,
  type MatriculaRequest,
  type StatusMatricula,
} from '../../../../types/matricula'
import { plural } from '../../../../utils/formatters'
import { AlterarMatriculaModal } from '../../components/AlterarMatriculaModal/AlterarMatriculaModal'
import { DetalhesMatriculaModal } from '../../components/DetalhesMatriculaModal/DetalhesMatriculaModal'
import { MatricularAlunos, type ResultadoMatriculas } from '../../components/MatricularAlunos/MatricularAlunos'
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

/** Abas do painel da turma: quem já está nela e quem pode entrar. */
type AbaTurma = 'alunos' | 'matricular'

// Estável: o useCarregamento pede o valor inicial sempre igual.
const SEM_MATRICULAS: Matricula[] = []

function porNomeDoAluno(a: Matricula, b: Matricula): number {
  return (a.alunoNome ?? '').localeCompare(b.alunoNome ?? '', 'pt-BR')
}

export function Matriculas() {
  const navigate = useNavigate()
  const rotas = useRotasAcademicas()
  const { turmas, alunos, resumo, carregando, erro, recarregar, atualizarContagens } = useMatriculas()
  const [turmaId, setTurmaId] = useState<number | null>(null)
  const [filtro, setFiltro] = useState<StatusMatricula | ''>('')
  const [aba, setAba] = useState<AbaTurma>('alunos')
  const [alterando, setAlterando] = useState<Matricula | null>(null)
  const [vendo, setVendo] = useState<Matricula | null>(null)
  const [sucesso, setSucesso] = useState<string | null>(null)
  const [erroAcao, setErroAcao] = useState<string | null>(null)

  // Sem escolha feita (ou com a turma escolhida fora da lista), abre na primeira ativa.
  const turma = turmas.find((t) => t.id === turmaId) ?? turmas.find((t) => t.ativa) ?? turmas[0] ?? null

  // Só as matrículas da turma escolhida, em qualquer situação: trocar de turma busca as da outra.
  const idDaTurma = turma?.id ?? null
  const buscarDaTurma = useMemo(
    () => (idDaTurma === null ? null : (signal: AbortSignal) => listarMatriculas({ turmaId: idDaTurma }, signal)),
    [idDaTurma],
  )
  const daTurmaCarregadas = useCarregamento(buscarDaTurma, SEM_MATRICULAS, 'A turma não existe mais.')
  const daTurma = [...daTurmaCarregadas.dados].sort(porNomeDoAluno)
  const visiveis = filtro ? daTurma.filter((m) => m.status === filtro) : daTurma

  // A turma escolhida conta pelas matrículas já na tela, que mudam na hora; as outras, pelo backend.
  const ativas = !turma
    ? 0
    : daTurmaCarregadas.carregando
      ? turma.matriculasAtivas
      : daTurma.filter((m) => m.status === 'ATIVA').length
  const ativasPorTurma = new Map(turmas.map((t) => [t.id, t.id === turma?.id ? ativas : t.matriculasAtivas]))
  const vagasLivres = turma ? Math.max(turma.vagas - ativas, 0) : 0
  // Quem pode entrar na turma: sem registro nela e com cadastro ativo (o mesmo corte da aba Matricular).
  const naTurma = new Set(daTurma.map((m) => m.alunoId))
  const paraMatricular = alunos.filter((a) => !naTurma.has(a.id) && a.situacao !== 'ANONIMIZADO').length

  // Resumo da escola inteira, acima da turma escolhida, contado no backend.
  const totalAtivas = resumo?.matriculasPorSituacao.ATIVA ?? 0
  const trancadas = resumo?.matriculasPorSituacao.TRANCADA ?? 0
  const alunosSemTurma = resumo?.alunosSemTurma ?? 0
  const vagasLivresEscola = resumo?.vagas.livres ?? 0

  /** Matricula um aluno na turma escolhida; lança ApiError se o backend recusar. */
  async function matricular(dados: MatriculaRequest) {
    const criada = await matricularAluno(dados)
    daTurmaCarregadas.atualizar((atuais) => [...atuais, criada])
    return criada
  }

  /** Muda situação, turma e/ou observação; lança ApiError se o backend recusar. */
  async function atualizar(id: string, dados: AtualizarMatriculaRequest) {
    const atualizada = await atualizarMatricula(id, dados)
    // Transferida para outra turma, sai da lista desta.
    daTurmaCarregadas.atualizar((atuais) =>
      atualizada.turmaId === idDaTurma
        ? atuais.map((m) => (m.id === atualizada.id ? atualizada : m))
        : atuais.filter((m) => m.id !== atualizada.id),
    )
    atualizarContagens()
    return atualizada
  }

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
    if (matriculados.length > 0) {
      atualizarContagens()
      // Mostra o resultado: os recém-matriculados na lista da turma.
      setFiltro('')
      setAba('alunos')
    }
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
    <SecretariaLayout titulo="Matrículas" subtitulo="Coloque os alunos nas turmas do período letivo.">
      {carregando && <p className={styles.estado} role="status">Carregando turmas, alunos e matrículas…</p>}

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
          <Button size="sm" onClick={() => navigate(`${rotas.academico}?aba=turmas`)}>
            Ir para Turmas
          </Button>
        </div>
      )}

      {!carregando && !erro && turma && (
        <StatCards rotulo="Resumo das matrículas da escola">
          <StatCard label="Matrículas ativas" value={totalAtivas} descricao="Em todas as turmas" destaque />
          <StatCard label="Trancadas" value={trancadas} descricao="Podem ser reativadas" />
          <StatCard
            label="Alunos sem turma"
            value={alunosSemTurma}
            descricao={alunosSemTurma > 0 ? 'Sem matrícula ativa' : 'Todos matriculados'}
            atencao={alunosSemTurma > 0}
          />
          <StatCard label="Vagas livres" value={vagasLivresEscola} descricao="Nas turmas ativas" />
        </StatCards>
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

          <section className={styles.painel} aria-label={`Turma ${turma.nome}`}>
            <header className={styles.cabecalho}>
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
            </header>

            <div className={styles.abas} role="tablist" aria-label={`Alunos da turma ${turma.nome}`}>
              {(
                [
                  { id: 'alunos', rotulo: 'Alunos da turma', quantidade: daTurma.length },
                  { id: 'matricular', rotulo: 'Matricular alunos', quantidade: paraMatricular },
                ] as const
              ).map((a) => (
                <button
                  key={a.id}
                  type="button"
                  role="tab"
                  id={`aba-turma-${a.id}`}
                  aria-selected={aba === a.id}
                  aria-controls="painel-turma"
                  className={`${styles.aba} ${aba === a.id ? styles.abaAtiva : ''}`}
                  onClick={() => setAba(a.id)}
                >
                  {a.rotulo}
                  <span className={styles.abaQuantidade}>{a.quantidade}</span>
                </button>
              ))}
            </div>

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

            <div id="painel-turma" role="tabpanel" aria-labelledby={`aba-turma-${aba}`} className={styles.conteudo}>
              {aba === 'matricular' ? (
                <MatricularAlunos
                  // Remonta ao trocar de turma, para não levar a seleção da anterior.
                  key={turma.id}
                  turma={turma}
                  alunos={alunos}
                  matriculasDaTurma={daTurma}
                  vagasLivres={vagasLivres}
                  onMatricular={matricular}
                  onConcluido={handleMatriculados}
                />
              ) : (
                <>
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

                  {daTurmaCarregadas.carregando ? (
                    <p className={styles.estadoSecao} role="status">
                      Carregando as matrículas da turma…
                    </p>
                  ) : daTurmaCarregadas.erro ? (
                    <div className={styles.estadoSecao}>
                      <span>{daTurmaCarregadas.erro}</span>
                      <Button size="sm" onClick={daTurmaCarregadas.recarregar}>
                        Tentar novamente
                      </Button>
                    </div>
                  ) : (
                    <MatriculasTable
                      matriculas={visiveis}
                      reiniciarCom={`${turma.id}|${filtro}`}
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
                  )}
                </>
              )}
            </div>
          </section>
        </div>
      )}

      {vendo && <DetalhesMatriculaModal matricula={vendo} onFechar={() => setVendo(null)} />}

      {alterando && (
        <AlterarMatriculaModal
          matricula={alterando}
          turmas={turmas}
          onFechar={() => setAlterando(null)}
          onAtualizar={atualizar}
          onConcluido={handleAlterado}
        />
      )}
    </SecretariaLayout>
  )
}
