import { Badge } from '../../../../components/Badge/Badge'
import { Button } from '../../../../components/Button/Button'
import { StatCard } from '../../../../components/StatCard/StatCard'
import { buscarBoletim } from '../../../../services/portalAlunoService'
import { ROTULOS_STATUS_MATRICULA } from '../../../../types/matricula'
import {
  ROTULOS_TIPO_AVALIACAO,
  type BoletimDisciplina,
  type BoletimTurma,
} from '../../../../types/portalAluno'
import { formatarData } from '../../../secretaria/components/PainelSecretaria/formatacao'
import { aproveitamento, formatarPontos } from '../../formatacao'
import { useDadosDoAluno } from '../../hooks/useDadosDoAluno'
import { AlunoLayout } from '../../layouts/AlunoLayout'
import styles from './Boletim.module.css'

const SEM_TURMAS: BoletimTurma[] = []

export function Boletim() {
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

  return (
    <AlunoLayout titulo="Boletim" subtitulo="Suas notas em cada disciplina, conforme os professores lançam.">
      {carregando && <div className={styles.mensagem}>Carregando seu boletim…</div>}

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
            <section className={styles.stats} aria-label="Resumo do boletim">
              <StatCard
                label="Aproveitamento"
                value={geral === null ? '—' : `${geral}%`}
                descricao="Dos pontos já avaliados"
                destaque
              />
              <StatCard label="Disciplinas" value={disciplinasAtivas.length} descricao="Na sua turma atual" />
              <StatCard
                label="Avaliações com nota"
                value={`${comNota} de ${avaliacoes.length}`}
                descricao="Provas, trabalhos e tarefas"
              />
            </section>
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
    </AlunoLayout>
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
