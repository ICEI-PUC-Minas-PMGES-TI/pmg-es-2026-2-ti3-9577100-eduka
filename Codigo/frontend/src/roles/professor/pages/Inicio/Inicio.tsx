import type { ReactNode } from 'react'
import { useNavigate } from 'react-router'
import { Button } from '../../../../components/Button/Button'
import { Atalhos, Colunas, EstadoDashboard, Painel, PainelVazio } from '../../../../components/Dashboard/Dashboard'
import { StatCard, StatCards } from '../../../../components/StatCard/StatCard'
import { useCarregamento } from '../../../../hooks/useCarregamento'
import { paths, type EstadoNovaAvaliacao } from '../../../../routes/paths'
import { obterDashboardProfessor } from '../../../../services/portalProfessorService'
import type { AvaliacaoResumo, DashboardProfessor } from '../../../../types/dashboard'
import { ROTULOS_TIPO_AVALIACAO } from '../../../../types/portalAluno'
import { formatarPontos, hojeIso, plural } from '../../../../utils/formatters'
import { ProfessorLayout } from '../../layouts/ProfessorLayout'
import { Dica } from '../../../../components/Dica/Dica'
import styles from './Inicio.module.css'

const MESES = ['jan', 'fev', 'mar', 'abr', 'mai', 'jun', 'jul', 'ago', 'set', 'out', 'nov', 'dez']

/** Início do PROFESSOR: turmas, alunos, próximas avaliações e notas a lançar. */
export function Inicio() {
  const { dados, carregando, erro, recarregar } = useCarregamento<DashboardProfessor | null>(
    obterDashboardProfessor,
    null,
    // O 404 aqui é o login sem cadastro de PROFESSOR vinculado na escola.
    'Seu login não está vinculado a um cadastro de professor. Fale com a secretaria da escola.',
  )

  return (
    <ProfessorLayout titulo="Início" subtitulo="Suas turmas, seus alunos e o que vem pela frente.">
      <EstadoDashboard carregando={carregando} erro={erro} onTentarDeNovo={recarregar} />
      {dados && <Resumo dados={dados} />}
    </ProfessorLayout>
  )
}

function Resumo({ dados }: { dados: DashboardProfessor }) {
  const navigate = useNavigate()
  const { proximasAvaliacoes, avaliacoesPendentes } = dados
  const hoje = hojeIso()

  function abrirAvaliacoes(avaliacao: AvaliacaoResumo) {
    const params = new URLSearchParams({
      turma: String(avaliacao.turmaId),
      disciplina: String(avaliacao.disciplinaId),
    })
    navigate(`${paths.professor.avaliacoes}?${params}`)
  }

  function lancarNotas(avaliacao: AvaliacaoResumo) {
    const params = new URLSearchParams({
      turma: String(avaliacao.turmaId),
      disciplina: String(avaliacao.disciplinaId),
      avaliacao: avaliacao.id,
    })
    navigate(`${paths.professor.avaliacoes}?${params}`)
  }

  return (
    <>
      <StatCards rotulo="Resumo das suas turmas">
        <StatCard
          label="Turmas"
          value={dados.turmas}
          descricao={`${dados.disciplinas} ${plural(dados.disciplinas, 'disciplina', 'disciplinas')}`}
          destaque
        />
        <StatCard label="Alunos" value={dados.alunos} descricao="Com matrícula ativa" />
        <StatCard label="Avaliações" value={dados.avaliacoes} descricao="Cadastradas nas suas turmas" />
        <StatCard
          label="Notas a lançar"
          value={avaliacoesPendentes.length}
          descricao={
            avaliacoesPendentes.length > 0
              ? plural(avaliacoesPendentes.length, 'Avaliação com nota faltando', 'Avaliações com nota faltando')
              : 'Tudo em dia'
          }
          alerta={avaliacoesPendentes.length > 0}
        />
      </StatCards>

      {dados.turmas === 0 && (
        <Painel titulo="Você ainda não tem turmas">
          <PainelVazio>
            Assim que a secretaria alocar você em uma turma, o resumo dela aparece aqui.
          </PainelVazio>
        </Painel>
      )}

      {dados.turmas > 0 && (
        <Atalhos
          titulo="Atalhos do dia"
          horizontal
          itens={[
            { rotulo: 'Fazer chamada', icon: 'calendar', onClick: () => navigate(paths.professor.chamada) },
            { rotulo: 'Lançar notas', icon: 'check', onClick: () => navigate(paths.professor.avaliacoes) },
            {
              rotulo: 'Criar uma avaliação',
              icon: 'plus',
              onClick: () =>
                navigate(paths.professor.avaliacoes, { state: { abrirCadastro: true } satisfies EstadoNovaAvaliacao }),
            },
            { rotulo: 'Ver minhas turmas', icon: 'users', onClick: () => navigate(paths.professor.turmas) },
          ]}
        />
      )}

      {dados.turmas > 0 && (
        <Colunas>
          <Painel
            titulo="Próximas avaliações"
            descricao="Nos próximos 30 dias."
            acao={
              <Button size="sm" icon="users" onClick={() => navigate(paths.professor.turmas)}>
                Minhas turmas
              </Button>
            }
          >
            {proximasAvaliacoes.length === 0 ? (
              <PainelVazio>Nenhuma avaliação marcada para os próximos 30 dias.</PainelVazio>
            ) : (
              <ul className={styles.lista}>
                {proximasAvaliacoes.map((avaliacao) => (
                  <ItemAvaliacao
                    key={avaliacao.id}
                    avaliacao={avaliacao}
                    detalhe={
                      avaliacao.data === hoje ? 'Hoje' : `${formatarPontos(avaliacao.valor)} pts`
                    }
                    acao={
                      <Dica texto="Abrir avaliações" descrever={false}>
                        <Button
                          size="sm"
                          icon="arrowRight"
                          aria-label={`Abrir as avaliações de ${avaliacao.turmaNome} · ${avaliacao.disciplinaNome}`}
                          onClick={() => abrirAvaliacoes(avaliacao)}
                        />
                      </Dica>
                    }
                  />
                ))}
              </ul>
            )}
          </Painel>

          <Painel titulo="Notas a lançar" descricao="Avaliações já realizadas com aluno sem nota.">
            {avaliacoesPendentes.length === 0 ? (
              <PainelVazio>Nenhuma nota pendente. Tudo em dia!</PainelVazio>
            ) : (
              <ul className={styles.lista}>
                {avaliacoesPendentes.map((avaliacao) => (
                  <ItemAvaliacao
                    key={avaliacao.id}
                    avaliacao={avaliacao}
                    detalhe={`${avaliacao.notasFaltando} ${plural(avaliacao.notasFaltando, 'aluno sem nota', 'alunos sem nota')}`}
                    alerta
                    acao={
                      <Button size="sm" variant="primary" icon="check" className={styles.acaoLarga} onClick={() => lancarNotas(avaliacao)}>
                        Lançar
                      </Button>
                    }
                  />
                ))}
              </ul>
            )}
          </Painel>
        </Colunas>
      )}
    </>
  )
}

type ItemAvaliacaoProps = {
  avaliacao: AvaliacaoResumo
  /** Linha curta à direita do tipo: pontos, "Hoje" ou quantas notas faltam. */
  detalhe: string
  /** Detalhe em vermelho, para nota pendente. */
  alerta?: boolean
  acao: ReactNode
}

function ItemAvaliacao({ avaliacao, detalhe, alerta = false, acao }: ItemAvaliacaoProps) {
  // "AAAA-MM-DD" lido direto, sem Date, para o fuso não mudar o dia.
  const [, mes, dia] = avaliacao.data.split('-')

  return (
    <li className={styles.item}>
      {/* role="img" para o leitor de tela ler "Dia dd/mm" no lugar de "07 out". */}
      <div className={styles.data} role="img" aria-label={`Dia ${dia}/${mes}`}>
        <strong>{dia}</strong>
        <span>{MESES[Number(mes) - 1]}</span>
      </div>
      <div className={styles.corpo}>
        <span className={styles.tipo}>
          {ROTULOS_TIPO_AVALIACAO[avaliacao.tipo]} ·{' '}
          <span className={alerta ? styles.alerta : styles.detalhe}>{detalhe}</span>
        </span>
        <strong className={styles.titulo}>{avaliacao.titulo}</strong>
        <span className={styles.contexto}>
          {avaliacao.turmaNome} · {avaliacao.disciplinaNome}
        </span>
      </div>
      {acao}
    </li>
  )
}
