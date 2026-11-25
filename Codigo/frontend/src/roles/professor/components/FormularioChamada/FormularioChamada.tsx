import { useEffect, useState, type FormEvent } from 'react'
import { Badge } from '../../../../components/ui/Badge/Badge'
import { Button } from '../../../../components/ui/Button/Button'
import { Campo } from '../../../../components/formulario/Campo/Campo'
import { Icon } from '../../../../components/ui/Icon/Icon'
import { paraData } from '../../../../components/formulario/CampoData/datas'
import { ApiError } from '../../../../services/api'
import type { AlunoDaChamada, Aula, DadosAula } from '../../../../types/academico/aula'
import { ROTULOS_STATUS_MATRICULA, type StatusMatricula } from '../../../../types/academico/matricula'
import { hojeIso, plural } from '../../../../utils/formatters'
import { formatarData } from '../../../../utils/formatacao'
import styles from './FormularioChamada.module.css'

// Limite da coluna CONTEUDO de AULA (V35).
const MAX_CONTEUDO = 2000

type Linha = {
  alunoId: string
  alunoNome: string
  alunoNumeroMatricula: string
  situacaoMatricula: StatusMatricula | null
  presente: boolean
  /** Entrou na turma depois de a aula ser registrada: ainda não tem presença gravada. */
  semRegistro: boolean
}

/**
 * Quem já tem presença na aula mais quem entra na chamada do dia. O backend
 * exige todos estes na correção, então o aluno que ficou de fora do registro
 * original aparece aqui, marcado para o professor conferir.
 */
function montarLinhas(alunos: AlunoDaChamada[], aula: Aula | null): Linha[] {
  const registradas: Linha[] = (aula?.chamada ?? []).map((p) => ({ ...p, semRegistro: false }))
  const jaNaChamada = new Set(registradas.map((l) => l.alunoId))
  const demais: Linha[] = alunos
    .filter((a) => !jaNaChamada.has(a.alunoId))
    .map((a) => ({ ...a, situacaoMatricula: 'ATIVA', presente: true, semRegistro: aula !== null }))
  return [...registradas, ...demais].sort((a, b) => a.alunoNome.localeCompare(b.alunoNome, 'pt-BR'))
}

const DIA_DA_SEMANA = new Intl.DateTimeFormat('pt-BR', { weekday: 'long' })

/** "2026-11-14" -> "Sexta-feira". */
function diaDaSemana(data: string): string {
  const dia = paraData(data)
  if (!dia) return ''
  const nome = DIA_DA_SEMANA.format(dia)
  return nome.charAt(0).toUpperCase() + nome.slice(1)
}

/** Texto de tela para a recusa do backend; o motivo vem no `detail`, sem acento. */
function mensagemErro(erro: unknown): string {
  if (!(erro instanceof ApiError)) return 'Erro inesperado. Tente de novo.'
  const detalhe = (erro.detalhe ?? '').toLowerCase()
  if (detalhe.includes('chamada incompleta') || detalhe.includes('sem matricula ativa')) {
    return 'A lista de alunos da turma mudou. Recarregue a página e faça a chamada de novo.'
  }
  if (detalhe.includes('nao ha aula')) return 'Não há aula desta disciplina na turma neste dia da semana.'
  if (detalhe.includes('futura')) return 'A data da aula não pode ser futura.'
  if (detalhe.includes('nao alocada')) return 'Você não está mais alocado nesta turma/disciplina.'
  if (erro.status === 404) return 'Esta aula não existe mais. Recarregue a página.'
  return erro.message
}

type FormularioChamadaProps = {
  data: string
  /** Nomes da turma e da disciplina, para o cabeçalho da aula. */
  turma: string
  disciplina: string
  /** Quem entra na chamada deste dia. */
  alunos: AlunoDaChamada[]
  /** Aula aberta para correção; sem ela, a chamada é nova e todos começam presentes. */
  aula: Aula | null
  /** Avisa se há alteração não salva, para a página não deixar sair sem querer. */
  onPendente: (pendente: boolean) => void
  /** Deve lançar ApiError em caso de falha. */
  onSalvar: (dados: DadosAula) => Promise<void>
  /** Volta para a aula já registrada neste dia, quando há uma. */
  onCancelar?: () => void
  onOutraAula?: () => void
  onExcluir?: () => void
}

/**
 * Chamada de uma aula: o conteúdo e presente/falta de cada aluno. Montado com
 * key = aula, então trocar de aula começa do zero.
 */
export function FormularioChamada({
  data,
  turma,
  disciplina,
  alunos,
  aula,
  onPendente,
  onSalvar,
  onCancelar,
  onOutraAula,
  onExcluir,
}: FormularioChamadaProps) {
  const [linhasIniciais] = useState(() => montarLinhas(alunos, aula))
  const [linhas, setLinhas] = useState(linhasIniciais)
  const conteudoInicial = aula?.conteudo ?? ''
  const [conteudo, setConteudo] = useState(conteudoInicial)
  const [erroConteudo, setErroConteudo] = useState<string | null>(null)
  const [erroEnvio, setErroEnvio] = useState<string | null>(null)
  const [salvando, setSalvando] = useState(false)

  const pendente =
    conteudo !== conteudoInicial || linhas.some((l, i) => l.presente !== linhasIniciais[i].presente)
  useEffect(() => onPendente(pendente), [pendente, onPendente])
  useEffect(() => () => onPendente(false), [onPendente])

  const presentes = linhas.filter((l) => l.presente).length
  const faltas = linhas.length - presentes
  const hoje = data === hojeIso()
  const percentual = linhas.length === 0 ? 0 : Math.round((presentes / linhas.length) * 100)

  function marcar(alunoId: string, presente: boolean) {
    setLinhas((atuais) => atuais.map((l) => (l.alunoId === alunoId ? { ...l, presente } : l)))
  }

  function descartar() {
    setLinhas(linhasIniciais)
    setConteudo(conteudoInicial)
    setErroConteudo(null)
    setErroEnvio(null)
  }

  async function enviar(evento: FormEvent) {
    evento.preventDefault()
    setErroEnvio(null)
    if (!conteudo.trim()) {
      setErroConteudo('Informe o conteúdo da aula.')
      return
    }
    setSalvando(true)
    try {
      await onSalvar({
        conteudo: conteudo.trim(),
        chamada: linhas.map((l) => ({ alunoId: l.alunoId, presente: l.presente })),
      })
    } catch (e) {
      setErroEnvio(mensagemErro(e))
      setSalvando(false)
    }
  }

  return (
    <form className={styles.formulario} onSubmit={enviar} noValidate>
      {/* Um cartão só: a aula à esquerda e, à direita, o resumo da chamada que vai sendo marcada. */}
      <section className={styles.cabecalho} aria-label="Aula do dia">
        <div className={styles.aula}>
          <span className={styles.contexto}>
            {diaDaSemana(data)} · {turma} · {disciplina}
          </span>
          <h2 className={styles.titulo}>{hoje ? 'Aula de hoje' : `Aula de ${formatarData(data)}`}</h2>
          <span>
            <Badge tone={aula ? 'success' : 'warning'} icon={aula ? 'check' : 'clock'}>
              {aula ? 'Chamada registrada' : 'Chamada por registrar'}
            </Badge>
          </span>
        </div>

        <div className={styles.resumo} role="region" aria-label="Resumo da chamada">
          <div className={styles.totais}>
            <p className={styles.presentes}>
              <strong className={styles.numeroPresentes}>{presentes}</strong> de {linhas.length}{' '}
              {plural(presentes, 'presente', 'presentes')}
            </p>
            <span className={styles.percentual}>{percentual}%</span>
          </div>
          <div
            className={styles.barra}
            role="img"
            aria-label={`${presentes} ${plural(presentes, 'presente', 'presentes')} e ${faltas} ${plural(faltas, 'falta', 'faltas')}`}
          >
            {presentes > 0 && <span className={styles.barraPresentes} style={{ flexGrow: presentes }} />}
            {faltas > 0 && <span className={styles.barraFaltas} style={{ flexGrow: faltas }} />}
          </div>
          {faltas === 0 ? (
            <p className={styles.semFaltas}>
              <Icon name="check" size={15} strokeWidth={2.4} />
              Turma completa, nenhuma falta
            </p>
          ) : (
            <p className={styles.comFaltas}>
              {faltas} {plural(faltas, 'falta', 'faltas')}
            </p>
          )}
        </div>
      </section>

      <section className={styles.card} aria-label="Chamada dos alunos">
        <Campo label="Conteúdo da aula" obrigatorio erro={erroConteudo ?? undefined} largo>
          {(props) => (
            <textarea
              {...props}
              name="conteudo"
              rows={3}
              maxLength={MAX_CONTEUDO}
              placeholder="O que foi trabalhado nesta aula"
              value={conteudo}
              disabled={salvando}
              onChange={(e) => {
                setConteudo(e.target.value)
                setErroConteudo(null)
              }}
            />
          )}
        </Campo>

        <div className={styles.topoLista}>
          <h3 className={styles.subtitulo}>Alunos</h3>
          <Button
            size="sm"
            icon="check"
            disabled={salvando || faltas === 0}
            onClick={() => setLinhas((atuais) => atuais.map((l) => ({ ...l, presente: true })))}
          >
            Marcar todos presentes
          </Button>
        </div>

        <ul className={styles.lista}>
          {linhas.map((l) => (
            <li key={l.alunoId} className={`${styles.linha} ${l.presente ? '' : styles.linhaFalta}`}>
              <div className={styles.aluno}>
                <span className={styles.nome}>{l.alunoNome}</span>{' '}
                {l.situacaoMatricula !== 'ATIVA' && (
                  <Badge tone="neutral">
                    {l.situacaoMatricula ? ROTULOS_STATUS_MATRICULA[l.situacaoMatricula] : 'Saiu da turma'}
                  </Badge>
                )}
                {l.semRegistro && <Badge tone="warning">Sem registro nesta aula</Badge>}
                <span className={styles.numero}>Nº {l.alunoNumeroMatricula}</span>
              </div>
              <div className={styles.presenca} role="group" aria-label={`Presença de ${l.alunoNome}`}>
                <button
                  type="button"
                  aria-pressed={l.presente}
                  className={`${styles.opcao} ${l.presente ? styles.opcaoPresente : ''}`}
                  disabled={salvando}
                  onClick={() => marcar(l.alunoId, true)}
                >
                  Presente
                </button>
                <button
                  type="button"
                  aria-pressed={!l.presente}
                  className={`${styles.opcao} ${l.presente ? '' : styles.opcaoFalta}`}
                  disabled={salvando}
                  onClick={() => marcar(l.alunoId, false)}
                >
                  Falta
                </button>
              </div>
            </li>
          ))}
        </ul>

        <div className={styles.rodape}>
          <div className={styles.acoesAula}>
            {onExcluir && (
              <Button size="sm" icon="trash" variant="dangerOutline" disabled={salvando} onClick={onExcluir}>
                Excluir aula
              </Button>
            )}
            {onOutraAula && (
              <Button size="sm" icon="plus" disabled={salvando} onClick={onOutraAula}>
                Registrar outra aula neste dia
              </Button>
            )}
          </div>
          <div className={styles.botoes}>
            {erroEnvio && (
              <span className={styles.erroEnvio} role="alert">
                {erroEnvio}
              </span>
            )}
            {onCancelar && (
              <Button disabled={salvando} onClick={onCancelar}>
                Cancelar
              </Button>
            )}
            {aula && pendente && (
              <Button disabled={salvando} onClick={descartar}>
                Descartar alterações
              </Button>
            )}
            <Button type="submit" variant="accent" disabled={salvando}>
              {salvando ? 'Salvando…' : aula ? 'Salvar alterações' : 'Salvar chamada'}
            </Button>
          </div>
        </div>
      </section>
    </form>
  )
}
