import { useEffect, useState } from 'react'
import { Button } from '../../../../components/Button/Button'
import { Campo } from '../../../../components/Campo/Campo'
import { ModalAlerta, ModalAviso } from '../../../../components/Modal/Modal'
import { Select } from '../../../../components/Select/Select'
import {
  alocarProfessor,
  desalocarProfessor,
  listarAlocacoesDaTurma,
  listarAlocacoesDoProfessor,
  listarTurmas,
} from '../../../../services/alocacaoService'
import { ApiError } from '../../../../services/api'
import { ROTULOS_TURNO, type Alocacao, type Turma } from '../../../../types/alocacao'
import styles from './AlocacoesDoProfessor.module.css'

type AlocacoesDoProfessorProps = {
  professorId: string
  /** O backend só aloca professor ativo; o inativo ainda pode ter alocações removidas. */
  professorAtivo: boolean
}

function mensagemDe(erro: unknown, padrao: string): string {
  return erro instanceof ApiError ? (erro.detalhe ?? erro.message) : padrao
}

function ordenar(alocacoes: Alocacao[]): Alocacao[] {
  return [...alocacoes].sort(
    (a, b) =>
      a.turmaNome.localeCompare(b.turmaNome, 'pt-BR', { numeric: true }) ||
      a.disciplinaNome.localeCompare(b.disciplinaNome, 'pt-BR'),
  )
}

/**
 * Seção "Turmas e disciplinas" da edição do professor. Cada inclusão ou
 * remoção é gravada na hora (POST e DELETE de /professores/{id}/alocacoes),
 * sem depender do "Salvar alterações" do cadastro. Cada disciplina tem um
 * professor por turma: as que já têm outro não entram nas opções.
 */
export function AlocacoesDoProfessor({ professorId, professorAtivo }: AlocacoesDoProfessorProps) {
  const [alocacoes, setAlocacoes] = useState<Alocacao[]>([])
  const [turmas, setTurmas] = useState<Turma[]>([])
  const [carregando, setCarregando] = useState(true)
  const [erroCarga, setErroCarga] = useState<string | null>(null)
  const [versao, setVersao] = useState(0)

  const [turmaId, setTurmaId] = useState('')
  const [disciplinaId, setDisciplinaId] = useState('')
  /** Alocações da turma escolhida, de qualquer professor; nulo enquanto carrega. */
  const [alocacoesDaTurma, setAlocacoesDaTurma] = useState<Alocacao[] | null>(null)
  const [erroTurma, setErroTurma] = useState<string | null>(null)
  const [versaoTurma, setVersaoTurma] = useState(0)

  /** Alocação com a remoção esperando confirmação. */
  const [confirmandoId, setConfirmandoId] = useState<string | null>(null)
  const [gravando, setGravando] = useState(false)
  const [erroAcao, setErroAcao] = useState<string | null>(null)

  useEffect(() => {
    const controller = new AbortController()
    const { signal } = controller
    Promise.all([listarAlocacoesDoProfessor(professorId, signal), listarTurmas(signal)])
      .then(([doProfessor, daEscola]) => {
        setAlocacoes(doProfessor)
        // Turma inativa não recebe alocação, então não entra nas opções.
        setTurmas(daEscola.filter((t) => t.ativa))
        setErroCarga(null)
      })
      .catch((e: unknown) => {
        if (signal.aborted) return
        setErroCarga(mensagemDe(e, 'Erro inesperado ao carregar as turmas e disciplinas.'))
      })
      .finally(() => {
        if (!signal.aborted) setCarregando(false)
      })
    return () => controller.abort()
  }, [professorId, versao])

  useEffect(() => {
    if (!turmaId) return
    const controller = new AbortController()
    listarAlocacoesDaTurma(Number(turmaId), controller.signal)
      .then(setAlocacoesDaTurma)
      .catch((e: unknown) => {
        if (controller.signal.aborted) return
        setErroTurma(mensagemDe(e, 'Erro inesperado ao carregar as disciplinas da turma.'))
      })
    return () => controller.abort()
  }, [turmaId, versaoTurma])

  function tentarCarregarDeNovo() {
    setCarregando(true)
    setErroCarga(null)
    setVersao((v) => v + 1)
  }

  function escolherTurma(id: string) {
    setTurmaId(id)
    setDisciplinaId('')
    setAlocacoesDaTurma(null)
    setErroTurma(null)
    setErroAcao(null)
  }

  async function incluir() {
    setErroAcao(null)
    setConfirmandoId(null)
    if (!disciplinaId) {
      setErroAcao('Escolha a turma e a disciplina.')
      return
    }
    setGravando(true)
    try {
      const criada = await alocarProfessor(professorId, {
        turmaId: Number(turmaId),
        disciplinaId: Number(disciplinaId),
      })
      setAlocacoes((atuais) => [...atuais, criada])
      setDisciplinaId('')
    } catch (e) {
      if (e instanceof ApiError && e.status === 409) {
        // Outra pessoa alocou nesse meio-tempo: a disciplina sai das opções.
        setDisciplinaId('')
        setErroAcao('Esta disciplina já tem professor nesta turma.')
      } else {
        setErroAcao(mensagemDe(e, 'Erro inesperado ao incluir a alocação.'))
      }
    } finally {
      setGravando(false)
      setVersaoTurma((v) => v + 1)
    }
  }

  async function remover(alocacao: Alocacao) {
    setErroAcao(null)
    setGravando(true)
    try {
      await desalocarProfessor(professorId, alocacao.id)
      setAlocacoes((atuais) => atuais.filter((a) => a.id !== alocacao.id))
    } catch (e) {
      if (e instanceof ApiError && e.status === 404) {
        setAlocacoes((atuais) => atuais.filter((a) => a.id !== alocacao.id))
        setErroAcao('Esta alocação já tinha sido removida.')
      } else {
        setErroAcao(mensagemDe(e, 'Erro inesperado ao remover a alocação.'))
      }
    } finally {
      setGravando(false)
      setConfirmandoId(null)
      setVersaoTurma((v) => v + 1)
    }
  }

  const turma = turmas.find((t) => String(t.id) === turmaId)
  const professorDaDisciplina = new Map((alocacoesDaTurma ?? []).map((a) => [a.disciplinaId, a]))
  const livres = (turma?.disciplinas ?? []).filter((d) => !professorDaDisciplina.has(d.id))
  const comOutroProfessor = (alocacoesDaTurma ?? []).filter((a) => a.professorId !== professorId)

  return (
    <section className={styles.secao} aria-labelledby="titulo-turmas-disciplinas">
      <h3 id="titulo-turmas-disciplinas" className={styles.titulo}>
        Turmas e disciplinas
      </h3>
      <p className={styles.dica}>Incluir e remover é gravado na hora, sem precisar de "Salvar alterações".</p>

      {carregando && <ModalAviso>Carregando as turmas e disciplinas…</ModalAviso>}

      {erroCarga && (
        <div className={styles.erroCarga}>
          <ModalAlerta>{erroCarga}</ModalAlerta>
          <Button size="sm" onClick={tentarCarregarDeNovo}>
            Tentar novamente
          </Button>
        </div>
      )}

      {!carregando && !erroCarga && (
        <>
          {alocacoes.length === 0 ? (
            <p className={styles.vazio}>Este professor ainda não dá aula em nenhuma turma.</p>
          ) : (
            <ul className={styles.lista}>
              {ordenar(alocacoes).map((a) => (
                <li key={a.id} className={styles.item}>
                  <span className={styles.itemTexto}>
                    <strong>{a.disciplinaNome}</strong>
                    <span className={styles.secundario}>
                      {a.turmaNome} · {a.periodoLetivoNome} · {ROTULOS_TURNO[a.turno]}
                    </span>
                    {confirmandoId === a.id && (
                      <span className={styles.aviso} role="alert">
                        As aulas desta disciplina na grade de horários da turma também serão apagadas.
                      </span>
                    )}
                  </span>
                  <span className={styles.itemAcoes}>
                    {confirmandoId === a.id ? (
                      <>
                        <Button size="sm" disabled={gravando} onClick={() => setConfirmandoId(null)}>
                          Manter
                        </Button>
                        <Button size="sm" variant="danger" disabled={gravando} onClick={() => void remover(a)}>
                          {gravando ? 'Removendo…' : 'Confirmar remoção'}
                        </Button>
                      </>
                    ) : (
                      <Button
                        size="sm"
                        variant="dangerOutline"
                        disabled={gravando}
                        aria-label={`Remover ${a.disciplinaNome} de ${a.turmaNome}`}
                        onClick={() => setConfirmandoId(a.id)}
                      >
                        Remover
                      </Button>
                    )}
                  </span>
                </li>
              ))}
            </ul>
          )}

          {!professorAtivo ? (
            <p className={styles.dica}>Professor inativo não recebe novas alocações. Reative o cadastro para incluir.</p>
          ) : turmas.length === 0 ? (
            <p className={styles.dica}>A escola não tem turmas ativas para alocar.</p>
          ) : (
            <div className={styles.inclusao}>
              <Campo label="Turma" className={styles.campo}>
                {(props) => (
                  <Select<string>
                    {...props}
                    name="alocacaoTurma"
                    opcoes={turmas.map((t) => ({
                      valor: String(t.id),
                      rotulo: `${t.nome} · ${t.periodoLetivoNome} · ${ROTULOS_TURNO[t.turno]}`,
                    }))}
                    valor={turmaId}
                    placeholder="Selecione"
                    onChange={escolherTurma}
                  />
                )}
              </Campo>
              <Campo label="Disciplina" className={styles.campo}>
                {(props) => (
                  <Select<string>
                    {...props}
                    name="alocacaoDisciplina"
                    opcoes={livres.map((d) => ({ valor: String(d.id), rotulo: d.nome }))}
                    valor={disciplinaId}
                    placeholder={!turmaId ? 'Escolha a turma' : 'Selecione'}
                    onChange={setDisciplinaId}
                  />
                )}
              </Campo>
              <Button
                variant="accent"
                icon="plus"
                className={styles.incluir}
                disabled={gravando || !disciplinaId}
                onClick={() => void incluir()}
              >
                Incluir
              </Button>
            </div>
          )}

          {professorAtivo && turma && erroTurma && <ModalAlerta>{erroTurma}</ModalAlerta>}
          {professorAtivo && turma && !erroTurma && alocacoesDaTurma && (
            <>
              {turma.disciplinas.length === 0 && (
                <p className={styles.dica}>Esta turma ainda não tem disciplinas na grade.</p>
              )}
              {turma.disciplinas.length > 0 && livres.length === 0 && (
                <p className={styles.dica}>Todas as disciplinas desta turma já têm professor.</p>
              )}
              {comOutroProfessor.length > 0 && (
                <p className={styles.dica}>
                  Já têm professor: {comOutroProfessor.map((a) => `${a.disciplinaNome} (${a.professorNome})`).join(', ')}.
                </p>
              )}
            </>
          )}

          {erroAcao && <ModalAlerta>{erroAcao}</ModalAlerta>}
        </>
      )}
    </section>
  )
}
