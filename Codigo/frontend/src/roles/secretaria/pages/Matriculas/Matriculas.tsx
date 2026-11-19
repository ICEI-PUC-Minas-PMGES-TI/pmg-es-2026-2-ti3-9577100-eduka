import { useMemo, useState } from 'react'
import { useNavigate } from 'react-router'
import { Button } from '../../../../components/ui/Button/Button'
import { StatCard, StatCards } from '../../../../components/ui/StatCard/StatCard'
import { useCarregamento } from '../../../../hooks/useCarregamento'
import { useListaPaginada, type PaginaComResumo } from '../../../../hooks/usePaginacao'
import { useRotasAcademicas } from '../../../../routes/areas'
import {
  atualizarMatricula,
  listarMatriculas,
  listarPaginaDeMatriculas,
  matricularAluno,
} from '../../../../services/academico/matriculaService'
import type { TurmaAcademica } from '../../../../types/academico/academico'
import { ROTULOS_TURNO } from '../../../../types/academico/alocacao'
import {
  ROTULOS_STATUS_MATRICULA,
  type AtualizarMatriculaRequest,
  type Matricula,
  type MatriculaRequest,
  type ResumoMatriculas,
  type StatusMatricula,
} from '../../../../types/academico/matricula'
import { plural } from '../../../../utils/formatters'
import { AlterarMatriculaModal } from '../../components/matriculas/AlterarMatriculaModal/AlterarMatriculaModal'
import { DetalhesMatriculaModal } from '../../components/matriculas/DetalhesMatriculaModal/DetalhesMatriculaModal'
import { MatricularAlunos, type ResultadoMatriculas } from '../../components/matriculas/MatricularAlunos/MatricularAlunos'
import { MatriculasTable } from '../../components/matriculas/MatriculasTable/MatriculasTable'
import { TurmasMatricula } from '../../components/matriculas/TurmasMatricula/TurmasMatricula'
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

/**
 * Matrículas (IU11). A lista da turma vem do backend uma página por vez, já
 * filtrada pela situação e com o resumo da turma inteira nos contadores.
 */
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

  // Uma página das matrículas da turma escolhida, na situação do filtro: trocar
  // de turma ou de filtro volta para a primeira página.
  const idDaTurma = turma?.id ?? null
  const [resumoDaTurma, setResumoDaTurma] = useState<{ turmaId: number; resumo: ResumoMatriculas } | null>(null)
  const buscarDaTurma = useMemo(
    () =>
      idDaTurma === null
        ? null
        : async (pagina: number, tamanho: number, signal: AbortSignal) => {
            const resposta = await listarPaginaDeMatriculas(
              { turmaId: idDaTurma, status: filtro || undefined, pagina, tamanho },
              signal,
            )
            setResumoDaTurma({ turmaId: idDaTurma, resumo: resposta.resumo })
            return resposta
          },
    [idDaTurma, filtro],
  )
  const daTurma = useListaPaginada<PaginaComResumo<Matricula, ResumoMatriculas>>(
    buscarDaTurma,
    'Não foi possível carregar as matrículas da turma.',
    'A turma não existe mais.',
  )
  // O resumo vale só para a turma que o pediu: ao trocar, a outra não mostra os números da anterior.
  const resumoTurma = resumoDaTurma?.turmaId === idDaTurma ? resumoDaTurma.resumo : null

  // A aba Matricular precisa saber quem já tem registro na turma (em qualquer
  // situação): busca a lista dela só enquanto a aba está aberta.
  const buscarRegistros = useMemo(
    () =>
      aba !== 'matricular' || idDaTurma === null
        ? null
        : (signal: AbortSignal) => listarMatriculas({ turmaId: idDaTurma }, signal),
    [aba, idDaTurma],
  )
  const registros = useCarregamento(buscarRegistros, SEM_MATRICULAS, 'A turma não existe mais.')

  // A turma escolhida conta pelo resumo da página (relido a cada mudança); as outras, pela lista de turmas.
  const ativas = !turma ? 0 : (resumoTurma?.porSituacao.ATIVA ?? turma.matriculasAtivas)
  const ativasPorTurma = new Map(turmas.map((t) => [t.id, t.id === turma?.id ? ativas : t.matriculasAtivas]))
  const vagasLivres = turma ? Math.max(turma.vagas - ativas, 0) : 0
  const totalNaTurma = resumoTurma?.total ?? 0
  const paraMatricular = resumoTurma?.paraMatricular ?? 0

  // Resumo da escola inteira, acima da turma escolhida, contado no backend.
  const totalAtivas = resumo?.matriculasPorSituacao.ATIVA ?? 0
  const trancadas = resumo?.matriculasPorSituacao.TRANCADA ?? 0
  const alunosSemTurma = resumo?.alunosSemTurma ?? 0
  const vagasLivresEscola = resumo?.vagas.livres ?? 0

  /** Matricula um aluno na turma escolhida; lança ApiError se o backend recusar. */
  async function matricular(dados: MatriculaRequest) {
    const criada = await matricularAluno(dados)
    // Já sai da lista de quem pode entrar, mesmo se outra do lote falhar.
    registros.atualizar((atuais) => [...atuais, criada])
    return criada
  }

  /** Muda situação, turma e/ou observação; lança ApiError se o backend recusar. */
  async function atualizar(id: string, dados: AtualizarMatriculaRequest) {
    const atualizada = await atualizarMatricula(id, dados)
    // Relê a página (a transferida sai dela) e as contagens.
    daTurma.recarregar()
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
      daTurma.recarregar()
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
                  { id: 'alunos', rotulo: 'Alunos da turma', quantidade: totalNaTurma },
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
                registros.carregando ? (
                  <p className={styles.estadoSecao} role="status">
                    Carregando os alunos que podem entrar na turma…
                  </p>
                ) : registros.erro ? (
                  <div className={styles.estadoSecao}>
                    <span>{registros.erro}</span>
                    <Button size="sm" onClick={registros.recarregar}>
                      Tentar novamente
                    </Button>
                  </div>
                ) : (
                  <MatricularAlunos
                    // Remonta ao trocar de turma, para não levar a seleção da anterior.
                    key={turma.id}
                    turma={turma}
                    alunos={alunos}
                    matriculasDaTurma={registros.dados}
                    vagasLivres={vagasLivres}
                    onMatricular={matricular}
                    onConcluido={handleMatriculados}
                  />
                )
              ) : (
                <>
                  <div className={styles.filtros} role="group" aria-label="Filtrar por situação">
                    {FILTROS.map((f) => {
                      const quantidade = f.valor ? (resumoTurma?.porSituacao[f.valor] ?? 0) : totalNaTurma
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
                    lista={daTurma}
                    mensagemVazia={
                      totalNaTurma === 0
                        ? 'Nenhum aluno matriculado nesta turma ainda.'
                        : `Nenhuma matrícula ${filtro ? ROTULOS_STATUS_MATRICULA[filtro].toLowerCase() : ''} nesta turma.`
                    }
                    onVer={setVendo}
                    onAlterar={(m) => {
                      limparAvisos()
                      setAlterando(m)
                    }}
                  />
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
