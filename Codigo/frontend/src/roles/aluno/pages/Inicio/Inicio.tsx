import { useState } from 'react'
import { useNavigate } from 'react-router'
import { Button } from '../../../../components/ui/Button/Button'
import { paths } from '../../../../routes/paths'
import { temModulo } from '../../../../services/acesso/modulosSessao'
import { buscarHistoricoFinanceiro } from '../../../../services/financeiro/pagamentoAlunoService'
import { buscarBoletim, listarMinhasAvaliacoes } from '../../../../services/portal/portalAlunoService'
import { podePagar, type CobrancaDoHistorico, type HistoricoFinanceiro } from '../../../../types/financeiro/pagamentoAluno'
import type { AvaliacaoDoAluno, BoletimTurma } from '../../../../types/portal/portalAluno'
import { formatarDiaMes, formatarPontos, hojeIso, plural } from '../../../../utils/formatters'
import { PERCENTUAL_APROVACAO } from '../../../professor/notas'
import { SecaoAvaliacoes } from '../../components/AgendaAvaliacoes/AgendaAvaliacoes'
import { ModalPagamento } from '../../components/ModalPagamento/ModalPagamento'
import { ProximaMensalidade } from '../../components/ProximaMensalidade/ProximaMensalidade'
import { aproveitamento, emRecuperacao, somarFrequencias } from '../../formatacao'
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
 * Início do ALUNO (IU27): a próxima mensalidade com o Pagar agora, as provas e
 * tarefas dos próximos 15 dias agrupadas por dia e, ao lado, a média, a
 * frequência e o aproveitamento de cada disciplina das turmas que ele cursa.
 * Cada bloco carrega sozinho: uma falha não esconde os outros.
 */
export function Inicio() {
  const comFinanceiro = temModulo('GESTAO_FINANCEIRA')
  return (
    <AlunoLayout
      titulo="Início"
      subtitulo={
        comFinanceiro ? 'Sua mensalidade, suas notas e o que vem pela frente.' : 'Suas notas e o que vem pela frente.'
      }
    >
      {comFinanceiro && <Mensalidade />}
      <div className={styles.grade}>
        <div className={styles.principal}>
          <ProximasAvaliacoes />
        </div>
        <aside className={styles.lateral}>
          <Desempenho />
        </aside>
      </div>
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

type MetricaProps = {
  rotulo: string
  valor: string
  sufixo?: string
  /** Largura da barra, de 0 a 100; nula sem dado. */
  percentual: number | null
  descricao: string
  alerta?: boolean
}

/** Uma métrica do painel: número grande, barra de 0 a 100% e uma linha de explicação. */
function Metrica({ rotulo, valor, sufixo, percentual, descricao, alerta = false }: MetricaProps) {
  return (
    <div className={styles.metrica}>
      <span className={styles.metricaRotulo}>{rotulo}</span>
      <span className={`${styles.metricaValor} ${alerta ? styles.alerta : ''}`}>
        <strong>{valor}</strong>
        {sufixo && <small>{sufixo}</small>}
      </span>
      <span className={styles.barra} aria-hidden="true">
        <span
          className={`${styles.barraValor} ${alerta ? styles.barraAlerta : ''}`}
          style={{ width: `${Math.max(0, Math.min(100, percentual ?? 0))}%` }}
        />
      </span>
      <span className={styles.metricaDescricao}>{descricao}</span>
    </div>
  )
}

/**
 * Painel "Seu desempenho": média geral (0 a 10) e frequência das turmas que o
 * aluno cursa agora, o bimestre em andamento e o aproveitamento de cada disciplina.
 */
function Desempenho() {
  const navigate = useNavigate()
  // Sem o controle de frequência contratado, a escola não registra chamada.
  const comFrequencia = temModulo('CONTROLE_FREQUENCIA')
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
        <span>Assim que a secretaria fizer sua matrícula, suas notas aparecem aqui.</span>
      </div>
    )
  }

  const avaliados = disciplinas.reduce((soma, d) => soma + d.pontosAvaliados, 0)
  const obtidos = disciplinas.reduce((soma, d) => soma + d.pontosObtidos, 0)
  // As avaliações valem pontos: a média é a proporção do que já foi avaliado, numa escala de 0 a 10.
  const media = avaliados > 0 ? Math.round((obtidos / avaliados) * 100) / 10 : null
  const abaixo = disciplinas.filter(emRecuperacao)

  const frequencia = somarFrequencias(
    ativas.map((t) => t.frequencia),
    ativas[0]?.limiteFaltasPercentual ?? 0,
  )
  const maisFaltas = disciplinas
    .filter((d) => (d.frequencia?.faltas ?? 0) > 0)
    .sort((a, b) => (b.frequencia?.faltas ?? 0) - (a.frequencia?.faltas ?? 0))[0]
  const faltasDela = maisFaltas?.frequencia?.faltas ?? 0

  const hoje = hojeIso()
  const bimestreAtual = ativas.flatMap((t) => t.bimestres).find((b) => b.inicio <= hoje && hoje <= b.fim)

  // Pior aproveitamento primeiro: o que pede atenção fica em cima; sem nota, no fim.
  const porDisciplina = disciplinas
    .map((d) => ({
      id: d.disciplinaId,
      nome: d.disciplinaNome,
      percentual: aproveitamento(d.pontosObtidos, d.pontosAvaliados),
    }))
    .sort((a, b) => (a.percentual ?? 101) - (b.percentual ?? 101) || a.nome.localeCompare(b.nome, 'pt-BR'))

  return (
    <section className={styles.painel} aria-label="Seu desempenho">
      <header className={styles.painelTopo}>
        <h2 className={styles.painelTitulo}>Seu desempenho</h2>
        {bimestreAtual && (
          <span className={styles.agora}>
            {bimestreAtual.numero}º bimestre · até {formatarDiaMes(bimestreAtual.fim)}
          </span>
        )}
      </header>

      <div className={`${styles.metricas} ${comFrequencia ? '' : styles.metricaUnica}`}>
        <Metrica
          rotulo="Média geral"
          valor={media === null ? '—' : formatarPontos(media)}
          sufixo="de 10"
          percentual={media === null ? null : media * 10}
          descricao={
            media === null
              ? 'Sem notas lançadas ainda'
              : abaixo.length === 0
                ? 'Todas as disciplinas na média'
                : `Abaixo da média: ${abaixo.map((d) => d.disciplinaNome).join(', ')}`
          }
          alerta={media !== null && media < PERCENTUAL_APROVACAO * 10}
        />
        {comFrequencia && (
          <Metrica
            rotulo="Frequência"
            valor={frequencia === null ? '—' : `${formatarPontos(frequencia.percentualFrequencia)}%`}
            percentual={frequencia?.percentualFrequencia ?? null}
            descricao={
              frequencia === null
                ? 'Sem aulas registradas ainda'
                : maisFaltas
                  ? `Mais faltas em ${maisFaltas.disciplinaNome}: ${faltasDela} ${plural(faltasDela, 'falta', 'faltas')}`
                  : 'Nenhuma falta até agora'
            }
            alerta={frequencia?.alertaFaltas}
          />
        )}
      </div>

      <div className={styles.disciplinas}>
        <h3 className={styles.subtitulo}>Por disciplina</h3>
        <ul className={styles.listaDisciplinas}>
          {porDisciplina.map((d) => {
            const abaixoDaMedia = d.percentual !== null && d.percentual < PERCENTUAL_APROVACAO * 100
            return (
              <li key={d.id} className={styles.disciplina}>
                <span className={styles.disciplinaNome}>{d.nome}</span>
                <span className={styles.barraPequena} aria-hidden="true">
                  <span
                    className={`${styles.barraValor} ${abaixoDaMedia ? styles.barraAlerta : ''}`}
                    style={{ width: `${d.percentual ?? 0}%` }}
                  />
                </span>
                <span className={`${styles.disciplinaPercentual} ${abaixoDaMedia ? styles.alerta : ''}`}>
                  {d.percentual === null ? '—' : `${d.percentual}%`}
                </span>
              </li>
            )
          })}
        </ul>
      </div>

      <Button icon="book" onClick={() => navigate(paths.aluno.boletim)}>
        Ver boletim
      </Button>
    </section>
  )
}

function ProximasAvaliacoes() {
  const navigate = useNavigate()
  const { dados: avaliacoes, carregando, erro, recarregar } = useDadosDoAluno(listarMinhasAvaliacoes, NENHUMA)

  if (carregando) {
    return (
      <div className={styles.mensagem} role="status">
        Carregando suas provas e tarefas…
      </div>
    )
  }
  if (erro) return <Erro mensagem={erro} onTentarDeNovo={recarregar} />

  const hoje = hojeIso()
  const limite = somarDias(hoje, DIAS_PROXIMAS)
  // O backend já manda por data.
  const proximas = avaliacoes.filter((a) => a.data >= hoje && a.data <= limite)

  return (
    <SecaoAvaliacoes
      titulo="Próximas provas e tarefas"
      vazio={`Nada marcado para os próximos ${DIAS_PROXIMAS} dias.`}
      avaliacoes={proximas}
      resumo
      acao={
        <Button size="sm" icon="calendar" onClick={() => navigate(`${paths.aluno.boletim}?aba=provas`)}>
          Ver todas as provas e tarefas
        </Button>
      }
    />
  )
}
