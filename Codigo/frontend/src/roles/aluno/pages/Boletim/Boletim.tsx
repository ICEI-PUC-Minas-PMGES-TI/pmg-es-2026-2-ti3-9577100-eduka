import { useSearchParams } from 'react-router'
import { Badge } from '../../../../components/Badge/Badge'
import { Button } from '../../../../components/Button/Button'
import { StatCard, StatCards } from '../../../../components/StatCard/StatCard'
import { buscarBoletim } from '../../../../services/portalAlunoService'
import { ROTULOS_STATUS_MATRICULA } from '../../../../types/matricula'
import {
  ROTULOS_TIPO_AVALIACAO,
  type BoletimDisciplina,
  type BoletimTurma,
} from '../../../../types/portalAluno'
import { formatarPontos } from '../../../../utils/formatters'
import { atingiuMedia } from '../../../professor/notas'
import { formatarData } from '../../../secretaria/components/PainelSecretaria/formatacao'
import { AgendaAvaliacoes } from '../../components/AgendaAvaliacoes/AgendaAvaliacoes'
import { aproveitamento } from '../../formatacao'
import { useDadosDoAluno } from '../../hooks/useDadosDoAluno'
import { AlunoLayout } from '../../layouts/AlunoLayout'
import styles from './Boletim.module.css'

const SEM_TURMAS: BoletimTurma[] = []

type Aba = 'boletim' | 'provas'

const ABAS: { id: Aba; rotulo: string }[] = [
  { id: 'boletim', rotulo: 'Boletim' },
  { id: 'provas', rotulo: 'Tarefas e provas' },
]

/** Disciplina com nota abaixo da média nos pontos já avaliados; sem nota ainda, não conta. */
function emRecuperacao(disciplina: BoletimDisciplina): boolean {
  return disciplina.pontosAvaliados > 0 && !atingiuMedia(disciplina.pontosObtidos, disciplina.pontosAvaliados)
}

/** Boletim do aluno, com a agenda de tarefas e provas numa segunda aba (?aba=provas). */
export function Boletim() {
  const [params, setParams] = useSearchParams()
  const aba: Aba = params.get('aba') === 'provas' ? 'provas' : 'boletim'

  return (
    <AlunoLayout titulo="Boletim" subtitulo="Suas notas, disciplinas, tarefas e provas do período.">
      <div className={styles.abas} role="tablist" aria-label="Boletim">
        {ABAS.map((a) => (
          <button
            key={a.id}
            type="button"
            role="tab"
            id={`aba-${a.id}`}
            aria-selected={aba === a.id}
            aria-controls="painel-boletim"
            className={`${styles.aba} ${aba === a.id ? styles.abaAtiva : ''}`}
            onClick={() => setParams(a.id === 'boletim' ? {} : { aba: a.id }, { replace: true })}
          >
            {a.rotulo}
          </button>
        ))}
      </div>

      <section id="painel-boletim" role="tabpanel" aria-labelledby={`aba-${aba}`} className={styles.painel}>
        {aba === 'boletim' ? <Notas /> : <AgendaAvaliacoes />}
      </section>
    </AlunoLayout>
  )
}

function Notas() {
  const { dados: turmas, carregando, erro, recarregar } = useDadosDoAluno(buscarBoletim, SEM_TURMAS)

  // Resumo só da turma (ou turmas) em que o aluno está cursando agora.
  const disciplinasAtivas = turmas
    .filter((t) => t.situacaoMatricula === 'ATIVA')
    .flatMap((t) => t.disciplinas)
  const avaliacoes = disciplinasAtivas.flatMap((d) => d.avaliacoes)
  const comNota = avaliacoes.filter((a) => a.nota !== null).length
  const geral = aproveitamento(
    disciplinasAtivas.reduce((soma, d) => soma + d.pontosObtidos, 0),
    disciplinasAtivas.reduce((soma, d) => soma + d.pontosAvaliados, 0),
  )
  const recuperacao = disciplinasAtivas.filter(emRecuperacao)

  return (
    <>
      {carregando && <div className={styles.mensagem} role="status">Carregando seu boletim…</div>}

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
          <strong>Você ainda não está matriculado em nenhuma turma.</strong>
          <span>Assim que a secretaria fizer sua matrícula, o boletim aparece aqui.</span>
        </div>
      )}

      {!carregando && !erro && turmas.length > 0 && (
        <>
          {disciplinasAtivas.length > 0 && (
            <StatCards rotulo="Resumo do boletim">
              <StatCard
                label="Aproveitamento"
                value={geral === null ? '—' : `${geral}%`}
                descricao="Dos pontos já avaliados"
                destaque
              />
              <StatCard
                label="Disciplinas em recuperação"
                value={recuperacao.length}
                descricao={
                  recuperacao.length === 0
                    ? 'Todas na média'
                    : recuperacao
                        .map((d) => `${d.disciplinaNome} · ${aproveitamento(d.pontosObtidos, d.pontosAvaliados)}%`)
                        .join(', ')
                }
                alerta={recuperacao.length > 0}
              />
              <StatCard label="Disciplinas" value={disciplinasAtivas.length} descricao="Na sua turma atual" />
              <StatCard
                label="Avaliações com nota"
                value={`${comNota} de ${avaliacoes.length}`}
                descricao="Provas, trabalhos e tarefas"
              />
            </StatCards>
          )}

          {turmas.map((turma) => (
            <section key={turma.turmaId} className={styles.turma} aria-label={`Boletim da turma ${turma.turmaNome}`}>
              <header className={styles.turmaTopo}>
                <div>
                  <h2 className={styles.turmaNome}>{turma.turmaNome}</h2>
                  <span className={styles.turmaInfo}>
                    {turma.cursoNome} · {turma.periodoLetivoNome}
                  </span>
                </div>
                {turma.situacaoMatricula !== 'ATIVA' && (
                  <Badge tone="neutral">{ROTULOS_STATUS_MATRICULA[turma.situacaoMatricula]}</Badge>
                )}
              </header>

              {turma.disciplinas.length === 0 ? (
                <div className={styles.mensagem}>A grade desta turma ainda não tem disciplinas.</div>
              ) : (
                <div className={styles.disciplinas}>
                  {turma.disciplinas.map((disciplina) => (
                    <CartaoDisciplina key={disciplina.disciplinaId} disciplina={disciplina} />
                  ))}
                </div>
              )}
            </section>
          ))}
        </>
      )}
    </>
  )
}

function CartaoDisciplina({ disciplina }: { disciplina: BoletimDisciplina }) {
  const percentual = aproveitamento(disciplina.pontosObtidos, disciplina.pontosAvaliados)

  return (
    <article className={styles.cartao}>
      <header className={styles.cartaoTopo}>
        <div className={styles.cartaoTitulo}>
          <h3>{disciplina.disciplinaNome}</h3>
          <span>{disciplina.professorNome ?? 'Professor a definir'}</span>
          {percentual !== null && (
            <span className={styles.situacao}>
              {emRecuperacao(disciplina) ? (
                <Badge tone="danger">Recuperação</Badge>
              ) : (
                <Badge tone="success">Na média</Badge>
              )}
            </span>
          )}
        </div>
        <div className={styles.total}>
          <strong>
            {formatarPontos(disciplina.pontosObtidos)}
            <small> / {formatarPontos(disciplina.pontosAvaliados)} pts</small>
          </strong>
          <span>
            {percentual === null ? 'Sem notas ainda' : `${percentual}% de aproveitamento`} ·{' '}
            {formatarPontos(disciplina.pontosDistribuidos)} pts distribuídos
          </span>
        </div>
      </header>

      {disciplina.avaliacoes.length === 0 ? (
        <p className={styles.vazio}>Nenhuma avaliação cadastrada ainda.</p>
      ) : (
        <div className={styles.rolagem}>
          <table className={styles.tabela}>
            <thead>
              <tr>
                <th scope="col">Data</th>
                <th scope="col">Avaliação</th>
                <th scope="col" className={styles.numero}>
                  Valor
                </th>
                <th scope="col" className={styles.numero}>
                  Nota
                </th>
              </tr>
            </thead>
            <tbody>
              {disciplina.avaliacoes.map((avaliacao) => (
                <tr key={avaliacao.id}>
                  <td className={styles.secundario}>{formatarData(avaliacao.data)}</td>
                  <td>
                    <span className={styles.tipo}>{ROTULOS_TIPO_AVALIACAO[avaliacao.tipo]}</span>
                    {avaliacao.titulo}
                    {avaliacao.observacao && <span className={styles.observacao}>{avaliacao.observacao}</span>}
                  </td>
                  <td className={`${styles.numero} ${styles.secundario}`}>{formatarPontos(avaliacao.valor)}</td>
                  <td className={styles.numero}>
                    {avaliacao.nota === null ? (
                      <span className={styles.secundario}>—</span>
                    ) : (
                      <strong>{formatarPontos(avaliacao.nota)}</strong>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </article>
  )
}
