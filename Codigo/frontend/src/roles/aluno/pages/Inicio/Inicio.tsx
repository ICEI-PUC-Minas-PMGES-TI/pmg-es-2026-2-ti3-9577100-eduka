import { useState } from 'react'
import { useNavigate } from 'react-router'
import { Button } from '../../../../components/Button/Button'
import { StatCard, StatCards } from '../../../../components/StatCard/StatCard'
import { paths } from '../../../../routes/paths'
import { buscarHistoricoFinanceiro } from '../../../../services/pagamentoAlunoService'
import { buscarBoletim, listarMinhasAvaliacoes } from '../../../../services/portalAlunoService'
import { podePagar, type CobrancaDoHistorico, type HistoricoFinanceiro } from '../../../../types/pagamentoAluno'
import type { AvaliacaoDoAluno, BoletimTurma } from '../../../../types/portalAluno'
import { formatarPontos, hojeIso, plural } from '../../../../utils/formatters'
import { PERCENTUAL_APROVACAO } from '../../../professor/notas'
import { SecaoAvaliacoes } from '../../components/AgendaAvaliacoes/AgendaAvaliacoes'
import { ModalPagamento } from '../../components/ModalPagamento/ModalPagamento'
import { ProximaMensalidade } from '../../components/ProximaMensalidade/ProximaMensalidade'
import { emRecuperacao, somarFrequencias } from '../../formatacao'
import { useDadosDoAluno } from '../../hooks/useDadosDoAluno'
import { AlunoLayout } from '../../layouts/AlunoLayout'
import styles from './Inicio.module.css'

const SEM_TURMAS: BoletimTurma[] = []
const NENHUMA: AvaliacaoDoAluno[] = []

/** Janela das próximas provas e tarefas (IU27). */
const DIAS_PROXIMAS = 15

/** "AAAA-MM-DD" mais `dias`, sem passar pelo fuso. */
function somarDias(iso: string, dias: number): string {
  const [ano, mes, dia] = iso.split('-').map(Number)
  return new Date(Date.UTC(ano, mes - 1, dia + dias)).toISOString().slice(0, 10)
}

/**
 * Início do ALUNO (IU27): a próxima mensalidade com o Pagar agora, a média e a
 * frequência das turmas que ele cursa e as provas e tarefas dos próximos 15 dias.
 * Cada bloco carrega sozinho: uma falha não esconde os outros.
 */
export function Inicio() {
  return (
    <AlunoLayout titulo="Início" subtitulo="Sua mensalidade, suas notas e o que vem pela frente.">
      <Mensalidade />
      <Desempenho />
      <ProximasAvaliacoes />
    </AlunoLayout>
  )
}

function Erro({ mensagem, onTentarDeNovo }: { mensagem: string; onTentarDeNovo: () => void }) {
  return (
    <div className={styles.mensagem} role="alert">
      <span>{mensagem}</span>
      <Button size="sm" onClick={onTentarDeNovo}>
        Tentar novamente
      </Button>
    </div>
  )
}

/** Some quando não há cobrança em aberto. */
function Mensalidade() {
  const { dados, carregando, erro, recarregar } = useDadosDoAluno<HistoricoFinanceiro | null>(
    buscarHistoricoFinanceiro,
    null,
  )
  const [pagando, setPagando] = useState<CobrancaDoHistorico | null>(null)
  const [pagou, setPagou] = useState(false)

  // A que vence (ou venceu) primeiro, como no destaque de Pagamentos.
  const proxima = (dados?.cobrancas ?? [])
    .filter((c) => podePagar(c.status))
    .sort((a, b) => a.dataVencimento.localeCompare(b.dataVencimento))[0]

  if (carregando) return null
  if (erro) return <Erro mensagem={`Não foi possível carregar sua mensalidade. ${erro}`} onTentarDeNovo={recarregar} />

  return (
    <>
      {proxima && <ProximaMensalidade cobranca={proxima} onPagar={setPagando} rotuloBotao="Pagar agora" />}
      {pagando && (
        <ModalPagamento
          cobranca={pagando}
          onPago={() => setPagou(true)}
          onFechar={() => {
            setPagando(null)
            if (pagou) {
              setPagou(false)
              recarregar()
            }
          }}
        />
      )}
    </>
  )
}

/** Média geral (0 a 10) e frequência das turmas que o aluno cursa agora. */
function Desempenho() {
  const navigate = useNavigate()
  const { dados: turmas, carregando, erro, recarregar } = useDadosDoAluno(buscarBoletim, SEM_TURMAS)

  if (carregando) {
    return (
      <div className={styles.mensagem} role="status">
        Carregando suas notas…
      </div>
    )
  }
  if (erro) return <Erro mensagem={erro} onTentarDeNovo={recarregar} />

  const ativas = turmas.filter((t) => t.situacaoMatricula === 'ATIVA')
  const disciplinas = ativas.flatMap((t) => t.disciplinas)
  if (disciplinas.length === 0) {
    return (
      <div className={styles.mensagem}>
        <strong>Você ainda não está cursando nenhuma turma.</strong>
        <span>Assim que a secretaria fizer sua matrícula, suas notas e sua frequência aparecem aqui.</span>
      </div>
    )
  }

  const avaliados = disciplinas.reduce((soma, d) => soma + d.pontosAvaliados, 0)
  const obtidos = disciplinas.reduce((soma, d) => soma + d.pontosObtidos, 0)
  // As avaliações valem pontos: a média é a proporção do que já foi avaliado, numa escala de 0 a 10.
  const media = avaliados > 0 ? Math.round((obtidos / avaliados) * 100) / 10 : null
  const recuperacao = disciplinas.filter(emRecuperacao)

  const frequencia = somarFrequencias(
    ativas.map((t) => t.frequencia),
    ativas[0]?.limiteFaltasPercentual ?? 0,
  )
  const maisFaltas = disciplinas
    .filter((d) => (d.frequencia?.faltas ?? 0) > 0)
    .sort((a, b) => (b.frequencia?.faltas ?? 0) - (a.frequencia?.faltas ?? 0))[0]

  return (
    <>
      <StatCards rotulo="Seu desempenho">
        <StatCard
          label="Média geral"
          value={media === null ? '—' : formatarPontos(media)}
          descricao={
            media === null
              ? 'Sem notas lançadas ainda'
              : recuperacao.length === 0
                ? 'De 10 · todas as disciplinas na média'
                : `De 10 · em recuperação: ${recuperacao.map((d) => d.disciplinaNome).join(', ')}`
          }
          alerta={media !== null && media < PERCENTUAL_APROVACAO * 10}
          destaque
        />
        <StatCard
          label="Frequência"
          value={frequencia === null ? '—' : `${formatarPontos(frequencia.percentualFrequencia)}%`}
          descricao={
            frequencia === null
              ? 'Sem aulas registradas ainda'
              : maisFaltas
                ? `Mais faltas em ${maisFaltas.disciplinaNome}: ${maisFaltas.frequencia?.faltas} ${plural(maisFaltas.frequencia?.faltas ?? 0, 'falta', 'faltas')}`
                : 'Nenhuma falta até agora'
          }
          alerta={frequencia?.alertaFaltas}
        />
      </StatCards>
      <div className={styles.acoes}>
        <Button icon="book" onClick={() => navigate(paths.aluno.boletim)}>
          Ver boletim
        </Button>
      </div>
    </>
  )
}

function ProximasAvaliacoes() {
  const navigate = useNavigate()
  const { dados: avaliacoes, carregando, erro, recarregar } = useDadosDoAluno(listarMinhasAvaliacoes, NENHUMA)

  if (carregando) return null
  if (erro) return <Erro mensagem={erro} onTentarDeNovo={recarregar} />

  const hoje = hojeIso()
  const limite = somarDias(hoje, DIAS_PROXIMAS)
  // O backend já manda por data.
  const proximas = avaliacoes.filter((a) => a.data >= hoje && a.data <= limite)

  return (
    <div className={styles.proximas}>
      <SecaoAvaliacoes
        titulo="Próximas provas e tarefas"
        vazio={`Nada marcado para os próximos ${DIAS_PROXIMAS} dias.`}
        avaliacoes={proximas}
      />
      <div className={styles.acoes}>
        <Button size="sm" icon="calendar" onClick={() => navigate(`${paths.aluno.boletim}?aba=provas`)}>
          Ver todas as provas e tarefas
        </Button>
      </div>
    </div>
  )
}
