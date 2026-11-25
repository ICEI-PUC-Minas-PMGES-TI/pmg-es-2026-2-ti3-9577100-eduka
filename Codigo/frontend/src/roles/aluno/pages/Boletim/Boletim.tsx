import { Fragment, useMemo, useState } from 'react'
import { useSearchParams } from 'react-router'
import { Campo } from '../../../../components/formulario/Campo/Campo'
import { Select } from '../../../../components/formulario/Select/Select'
import { Badge } from '../../../../components/ui/Badge/Badge'
import { Button } from '../../../../components/ui/Button/Button'
import { Icon } from '../../../../components/ui/Icon/Icon'
import { StatCard, StatCards } from '../../../../components/ui/StatCard/StatCard'
import { temModulo } from '../../../../services/acesso/modulosSessao'
import { buscarBoletim } from '../../../../services/portal/portalAlunoService'
import { ROTULOS_STATUS_MATRICULA } from '../../../../types/academico/matricula'
import {
  ROTULOS_TIPO_AVALIACAO,
  type AvaliacaoDoAluno,
  type BoletimTurma,
  type FrequenciaBoletim,
} from '../../../../types/portal/portalAluno'
import { formatarDiaMes, formatarPercentual, formatarPontos, hojeIso, plural } from '../../../../utils/formatters'
import { atingiuMedia, PERCENTUAL_APROVACAO } from '../../../professor/notas'
import { AgendaAvaliacoes } from '../../components/AgendaAvaliacoes/AgendaAvaliacoes'
import { aproveitamento } from '../../formatacao'
import { useDadosDoAluno } from '../../hooks/useDadosDoAluno'
import { AlunoLayout } from '../../layouts/AlunoLayout'
import { IndicadorAjuda } from '../../../../components/ajuda/IndicadorAjuda/IndicadorAjuda'
import {
  agruparPorAno,
  avaliacoesNoRecorte,
  bimestreDaData,
  noRecorte,
  quadroDoAno,
  resumir,
  situacao,
  type AnoLetivo,
  type LinhaDisciplina,
  type Pontos,
  type Situacao,
} from './boletimAnual'
import styles from './Boletim.module.css'

const SEM_TURMAS: BoletimTurma[] = []

type Aba = 'boletim' | 'provas'

const ABAS: { id: Aba; rotulo: string }[] = [
  { id: 'boletim', rotulo: 'Boletim' },
  { id: 'provas', rotulo: 'Tarefas e provas' },
]

const ROTULO_SITUACAO: Record<Situacao, string> = {
  'na-media': 'Na média',
  abaixo: 'Abaixo da média',
  'sem-notas': 'Sem notas',
}

const TOM_SITUACAO: Record<Situacao, 'success' | 'danger' | 'neutral'> = {
  'na-media': 'success',
  abaixo: 'danger',
  'sem-notas': 'neutral',
}

function nomeDoBimestre(numero: number): string {
  return `${numero}º bimestre`
}

/**
 * Boletim do aluno por ano letivo e bimestre (?ano=2026&bimestre=2), com a
 * agenda de tarefas e provas numa segunda aba (?aba=provas).
 */
export function Boletim() {
  const [params, setParams] = useSearchParams()
  const aba: Aba = params.get('aba') === 'provas' ? 'provas' : 'boletim'

  // O bimestre escolhido vale nas duas abas; o ano é só do boletim.
  function trocarAba(nova: Aba) {
    const novos = new URLSearchParams()
    if (nova === 'provas') novos.set('aba', 'provas')
    const bimestre = params.get('bimestre')
    if (bimestre) novos.set('bimestre', bimestre)
    setParams(novos, { replace: true })
  }

  return (
    <AlunoLayout
      titulo="Boletim"
      subtitulo={
        // Sem o controle de frequência contratado, a escola não registra chamada: não há frequência a mostrar.
        temModulo('CONTROLE_FREQUENCIA')
          ? 'Suas notas por ano e bimestre, a frequência e as tarefas e provas.'
          : 'Suas notas por ano e bimestre e as tarefas e provas.'
      }
    >
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
            onClick={() => trocarAba(a.id)}
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
  const [params, setParams] = useSearchParams()
  const anos = useMemo(() => agruparPorAno(turmas), [turmas])

  if (carregando) {
    return (
      <div className={styles.mensagem} role="status">
        Carregando seu boletim…
      </div>
    )
  }

  if (erro) {
    return (
      <div className={styles.mensagem} role="alert">
        <span>{erro}</span>
        <Button size="sm" onClick={recarregar}>
          Tentar novamente
        </Button>
      </div>
    )
  }

  if (anos.length === 0) {
    return (
      <div className={styles.mensagem}>
        <strong>Você ainda não está matriculado em nenhuma turma.</strong>
        <span>Assim que a secretaria fizer sua matrícula, o boletim aparece aqui.</span>
      </div>
    )
  }

  // Sem escolha na URL: o ano que o aluno está cursando, senão o mais recente.
  const ano = anos.find((a) => a.chave === params.get('ano')) ?? anos.find((a) => a.emCurso) ?? anos[0]
  const pedido = Number(params.get('bimestre'))
  const bimestre = ano.bimestres.some((b) => b.numero === pedido) ? pedido : null

  function escolher(mudancas: Record<string, string | null>) {
    const novos = new URLSearchParams(params)
    for (const [chave, valor] of Object.entries(mudancas)) {
      if (valor === null) novos.delete(chave)
      else novos.set(chave, valor)
    }
    setParams(novos, { replace: true })
  }

  return (
    <BoletimDoAno
      key={ano.chave}
      anos={anos}
      ano={ano}
      bimestre={bimestre}
      onAno={(chave) => escolher({ ano: chave, bimestre: null })}
      onBimestre={(numero) => escolher({ bimestre: numero === null ? null : String(numero) })}
    />
  )
}

type BoletimDoAnoProps = {
  anos: AnoLetivo[]
  ano: AnoLetivo
  /** Nulo: o ano todo. */
  bimestre: number | null
  onAno: (chave: string) => void
  onBimestre: (numero: number | null) => void
}

function BoletimDoAno({ anos, ano, bimestre, onAno, onBimestre }: BoletimDoAnoProps) {
  const comFrequencia = temModulo('CONTROLE_FREQUENCIA')
  const linhas = useMemo(() => quadroDoAno(ano), [ano])
  const resumo = resumir(ano, linhas, bimestre)
  const bimestreDeHoje = bimestreDaData(ano, hojeIso())
  const textoDoRecorte = bimestre === null ? `no ano de ${ano.rotulo}` : `no ${nomeDoBimestre(bimestre)}`
  const percentual = aproveitamento(resumo.pontos.obtidos, resumo.pontos.avaliados)

  return (
    <>
      <section className={styles.cabecalho} aria-label="Ano letivo e bimestre">
        <div className={styles.cabecalhoTopo}>
          <div className={styles.identificacao}>
            <span className={styles.sobretitulo}>Boletim escolar</span>
            <h2 className={styles.anoTitulo}>{ano.rotulo}</h2>
            <ul className={styles.turmas} aria-label="Turmas do ano">
              {ano.turmas.map((turma) => (
                <li key={turma.turmaId}>
                  <Icon name="graduationCap" size={15} />
                  <span>
                    <strong>{turma.turmaNome}</strong> · {turma.cursoNome} · {turma.periodoLetivoNome}
                  </span>
                  {turma.situacaoMatricula !== 'ATIVA' && (
                    <Badge tone="neutral">{ROTULOS_STATUS_MATRICULA[turma.situacaoMatricula]}</Badge>
                  )}
                </li>
              ))}
            </ul>
          </div>
          {anos.length > 1 && (
            <Campo label="Ano letivo" className={styles.campoAno}>
              {(campo) => (
                <Select
                  {...campo}
                  opcoes={anos.map((a) => ({ valor: a.chave, rotulo: a.emCurso ? `${a.rotulo} (atual)` : a.rotulo }))}
                  valor={ano.chave}
                  onChange={onAno}
                />
              )}
            </Campo>
          )}
        </div>

        {ano.bimestres.length > 0 && (
          <div className={styles.recortes} role="group" aria-label="Ver o boletim de">
            <button
              type="button"
              aria-pressed={bimestre === null}
              className={`${styles.recorte} ${bimestre === null ? styles.recorteAtivo : ''}`}
              onClick={() => onBimestre(null)}
            >
              <span className={styles.recorteNome}>Ano todo</span>
              <span className={styles.recorteDatas}>
                {ano.bimestres.length} {plural(ano.bimestres.length, 'bimestre', 'bimestres')}
              </span>
            </button>
            {ano.bimestres.map((b) => (
              <button
                key={b.numero}
                type="button"
                aria-pressed={bimestre === b.numero}
                className={`${styles.recorte} ${bimestre === b.numero ? styles.recorteAtivo : ''}`}
                onClick={() => onBimestre(b.numero)}
              >
                <span className={styles.recorteNome}>
                  {nomeDoBimestre(b.numero)}
                  {bimestreDeHoje === b.numero && <span className={styles.agora}>agora</span>}
                </span>
                <span className={styles.recorteDatas}>
                  {formatarDiaMes(b.inicio)} a {formatarDiaMes(b.fim)}
                </span>
              </button>
            ))}
          </div>
        )}
      </section>

      <StatCards rotulo="Resumo do boletim">
        <StatCard
          label="Aproveitamento"
          value={percentual === null ? '—' : `${percentual}%`}
          descricao={
            percentual === null
              ? `Sem notas lançadas ${textoDoRecorte}`
              : `${formatarPontos(resumo.pontos.obtidos)} de ${formatarPontos(resumo.pontos.avaliados)} pontos avaliados`
          }
          destaque
          alerta={percentual !== null && !atingiuMedia(resumo.pontos.obtidos, resumo.pontos.avaliados)}
        />
        {comFrequencia && (
          <StatCard
            label="Frequência"
            value={resumo.frequencia === null ? '—' : formatarPercentual(resumo.frequencia.percentualFrequencia)}
            descricao={textoFrequencia(resumo.frequencia)}
            alerta={resumo.frequencia?.alertaFaltas}
          />
        )}
        <StatCard
          label="Abaixo da média"
          value={resumo.abaixoDaMedia.length}
          descricao={
            resumo.abaixoDaMedia.length === 0
              ? resumo.pontos.avaliados === 0
                ? 'Nenhuma nota ainda'
                : 'Todas na média'
              : resumo.abaixoDaMedia.map((l) => l.nome).join(', ')
          }
          alerta={resumo.abaixoDaMedia.length > 0}
        />
        <StatCard
          label="Avaliações com nota"
          value={`${resumo.avaliacoesComNota} de ${resumo.avaliacoes}`}
          descricao="Provas, trabalhos e tarefas"
        />
      </StatCards>

      <QuadroDeNotas ano={ano} linhas={linhas} bimestre={bimestre} />

      <p className={styles.regras}>
        <Icon name="info" size={16} />
        <span>
          <strong>Regras de aprovação:</strong> média mínima de {formatarPontos(PERCENTUAL_APROVACAO * 100)}% dos
          pontos
          {comFrequencia && <> e limite de {formatarPontos(ano.limiteFaltasPercentual)}% de faltas nas aulas dadas</>}.
          A nota de cada bimestre é a soma das avaliações com data dentro dele.
        </span>
      </p>

      <IndicadorAjuda>Alguma nota ou falta parece errada? Veja como falar com a escola</IndicadorAjuda>
    </>
  )
}

function textoFrequencia(frequencia: FrequenciaBoletim | null): string {
  if (frequencia === null) return 'Sem aulas registradas ainda'
  const { faltas, totalAulas, alertaFaltas } = frequencia
  return `${faltas} ${plural(faltas, 'falta', 'faltas')} em ${totalAulas} ${plural(totalAulas, 'aula', 'aulas')}${alertaFaltas ? ' · acima do limite de faltas' : ''}`
}

type QuadroDeNotasProps = {
  ano: AnoLetivo
  linhas: LinhaDisciplina[]
  bimestre: number | null
}

/** O boletim em si: uma linha por disciplina, uma coluna por bimestre, e as avaliações ao abrir a linha. */
function QuadroDeNotas({ ano, linhas, bimestre }: QuadroDeNotasProps) {
  const [abertas, setAbertas] = useState<Set<number>>(() => new Set())
  const todasAbertas = linhas.length > 0 && linhas.every((l) => abertas.has(l.disciplinaId))
  const comFrequencia = temModulo('CONTROLE_FREQUENCIA')
  const colunas = ano.bimestres.length + (comFrequencia ? 4 : 3)

  function alternar(id: number) {
    setAbertas((atual) => {
      const nova = new Set(atual)
      if (nova.has(id)) nova.delete(id)
      else nova.add(id)
      return nova
    })
  }

  return (
    <section className={styles.quadro} aria-labelledby="titulo-quadro">
      <header className={styles.quadroTopo}>
        <div>
          <h2 id="titulo-quadro" className={styles.quadroTitulo}>
            Quadro de notas
          </h2>
          <p className={styles.quadroDica}>Toque numa disciplina para ver as provas, trabalhos e tarefas.</p>
        </div>
        <div className={styles.quadroAcoes}>
          <ul className={styles.legenda} aria-label="Legenda">
            <li>
              <span className={`${styles.ponto} ${styles['na-media']}`} aria-hidden="true" /> Na média
            </li>
            <li>
              <span className={`${styles.ponto} ${styles.abaixo}`} aria-hidden="true" /> Abaixo da média
            </li>
            <li>
              <span className={`${styles.ponto} ${styles['sem-notas']}`} aria-hidden="true" /> Sem nota
            </li>
          </ul>
          {linhas.length > 0 && (
            <Button
              size="sm"
              variant="outline"
              onClick={() => setAbertas(todasAbertas ? new Set() : new Set(linhas.map((l) => l.disciplinaId)))}
            >
              {todasAbertas ? 'Fechar todas' : 'Abrir todas'}
            </Button>
          )}
        </div>
      </header>

      {linhas.length === 0 ? (
        <div className={styles.mensagem}>A grade deste ano ainda não tem disciplinas.</div>
      ) : (
        <div className={styles.rolagem}>
          <table className={styles.tabela}>
            <thead>
              <tr>
                <th scope="col">Disciplina</th>
                {ano.bimestres.map((b) => (
                  <th
                    key={b.numero}
                    scope="col"
                    className={`${styles.colNota} ${bimestre === b.numero ? styles.colAtiva : ''}`}
                  >
                    {b.numero}º bim.
                  </th>
                ))}
                <th scope="col" className={`${styles.colNota} ${bimestre === null ? styles.colAtiva : ''}`}>
                  Total
                </th>
                {comFrequencia && (
                  <th scope="col" className={styles.colNota}>
                    Frequência
                  </th>
                )}
                <th scope="col">Situação</th>
              </tr>
            </thead>
            <tbody>
              {linhas.map((linha) => {
                const aberta = abertas.has(linha.disciplinaId)
                const doRecorte = noRecorte(linha, bimestre)
                const idDetalhe = `avaliacoes-${linha.disciplinaId}`
                const sit = situacao(doRecorte)
                return (
                  <Fragment key={linha.disciplinaId}>
                    <tr className={`${styles.linha} ${aberta ? styles.linhaAberta : ''}`}>
                      <th scope="row" className={styles.colDisciplina}>
                        <button
                          type="button"
                          className={styles.disciplina}
                          aria-expanded={aberta}
                          aria-controls={idDetalhe}
                          onClick={() => alternar(linha.disciplinaId)}
                        >
                          <span className={styles.disciplinaTexto}>
                            <span className={styles.disciplinaNome}>{linha.nome}</span>
                            <span className={styles.professor}>{linha.professorNome ?? 'Professor a definir'}</span>
                          </span>
                          <Icon name="chevronDown" size={18} className={styles.seta} />
                        </button>
                      </th>
                      {ano.bimestres.map((b) => (
                        <CelulaNota
                          key={b.numero}
                          rotulo={`${b.numero}º bim.`}
                          pontos={noRecorte(linha, b.numero)}
                          ativa={bimestre === b.numero}
                        />
                      ))}
                      <CelulaNota rotulo="Total" pontos={linha.total} ativa={bimestre === null} total />
                      {comFrequencia && (
                        <td data-rotulo="Frequência" className={styles.colNota}>
                          <CelulaFrequencia frequencia={doRecorte.frequencia} />
                        </td>
                      )}
                      <td data-rotulo="Situação" className={styles.colSituacao}>
                        <span className={styles.situacao}>
                          <Badge tone={TOM_SITUACAO[sit]}>{ROTULO_SITUACAO[sit]}</Badge>
                          {comFrequencia && doRecorte.frequencia?.alertaFaltas && (
                            <Badge tone="danger" icon="alert">
                              Faltas acima do limite
                            </Badge>
                          )}
                        </span>
                      </td>
                    </tr>
                    {aberta && (
                      <tr id={idDetalhe} className={styles.detalhe}>
                        <td colSpan={colunas}>
                          <DetalheDisciplina linha={linha} bimestre={bimestre} />
                        </td>
                      </tr>
                    )}
                  </Fragment>
                )
              })}
            </tbody>
          </table>
        </div>
      )}
    </section>
  )
}

type CelulaNotaProps = {
  rotulo: string
  pontos: Pontos
  ativa: boolean
  total?: boolean
}

/** Nota de um bimestre (ou do ano): pontos obtidos sobre os avaliados, na cor da situação. */
function CelulaNota({ rotulo, pontos, ativa, total = false }: CelulaNotaProps) {
  const sit = situacao(pontos)
  const classes = [styles.colNota, ativa && styles.colAtiva, total && styles.colTotal].filter(Boolean).join(' ')
  return (
    <td data-rotulo={rotulo} className={classes}>
      {pontos.distribuidos === 0 ? (
        <span className={styles.semAvaliacao} title="Nenhuma avaliação">
          —
        </span>
      ) : sit === 'sem-notas' ? (
        <span className={`${styles.nota} ${styles['sem-notas']}`} title="Avaliações ainda sem nota">
          <strong>—</strong>
          <small>{formatarPontos(pontos.distribuidos)} pts</small>
        </span>
      ) : (
        <span className={`${styles.nota} ${styles[sit]}`}>
          <strong>{formatarPontos(pontos.obtidos)}</strong>
          <small>de {formatarPontos(pontos.avaliados)}</small>
        </span>
      )}
    </td>
  )
}

function CelulaFrequencia({ frequencia }: { frequencia: FrequenciaBoletim | null }) {
  if (frequencia === null) return <span className={styles.semAvaliacao}>Sem aulas</span>
  return (
    <span className={`${styles.frequencia} ${frequencia.alertaFaltas ? styles.frequenciaAlerta : ''}`}>
      <strong>{formatarPercentual(frequencia.percentualFrequencia)}</strong>
      <small>
        {frequencia.faltas} {plural(frequencia.faltas, 'falta', 'faltas')} em {frequencia.totalAulas}
      </small>
    </span>
  )
}

/** As avaliações da disciplina no recorte, com a nota e uma barra do quanto valia. */
function DetalheDisciplina({ linha, bimestre }: { linha: LinhaDisciplina; bimestre: number | null }) {
  const avaliacoes = avaliacoesNoRecorte(linha, bimestre)
  const pontos = noRecorte(linha, bimestre)
  const pendentes = pontos.distribuidos - pontos.avaliados

  return (
    <div className={styles.detalheConteudo}>
      <div className={styles.detalheTopo}>
        <strong>
          {bimestre === null ? 'Avaliações do ano' : `Avaliações do ${nomeDoBimestre(bimestre)}`} · {linha.nome}
        </strong>
        <span>
          {formatarPontos(pontos.distribuidos)} pts distribuídos
          {pendentes > 0 && ` · ${formatarPontos(pendentes)} ainda sem nota`}
        </span>
      </div>
      {avaliacoes.length === 0 ? (
        <p className={styles.vazio}>
          {bimestre === null ? 'Nenhuma avaliação cadastrada ainda.' : 'Nenhuma avaliação neste bimestre.'}
        </p>
      ) : (
        <ul className={styles.avaliacoes}>
          {avaliacoes.map((avaliacao) => (
            <ItemAvaliacao key={avaliacao.id} avaliacao={avaliacao} mostrarBimestre={bimestre === null} />
          ))}
        </ul>
      )}
    </div>
  )
}

function ItemAvaliacao({ avaliacao, mostrarBimestre }: { avaliacao: AvaliacaoDoAluno; mostrarBimestre: boolean }) {
  const comNota = avaliacao.nota !== null
  const sit: Situacao = !comNota ? 'sem-notas' : atingiuMedia(avaliacao.nota!, avaliacao.valor) ? 'na-media' : 'abaixo'
  const largura = comNota && avaliacao.valor > 0 ? Math.min(100, (avaliacao.nota! / avaliacao.valor) * 100) : 0
  const [dia, mes] = formatarDiaMes(avaliacao.data).split('/')

  return (
    <li className={styles.avaliacao}>
      <span className={styles.data} aria-label={`Data: ${dia}/${mes}`}>
        <strong>{dia}</strong>
        <small>{NOMES_MESES[Number(mes) - 1]}</small>
      </span>
      <span className={styles.avaliacaoTexto}>
        <span className={styles.avaliacaoEtiquetas}>
          <span className={styles.tipo}>{ROTULOS_TIPO_AVALIACAO[avaliacao.tipo]}</span>
          {mostrarBimestre && avaliacao.bimestre !== null && (
            <span className={styles.etiquetaBimestre}>{avaliacao.bimestre}º bim.</span>
          )}
        </span>
        <span className={styles.avaliacaoTitulo}>{avaliacao.titulo}</span>
        {avaliacao.observacao && <span className={styles.observacao}>{avaliacao.observacao}</span>}
      </span>
      <span className={styles.avaliacaoNota}>
        <span className={`${styles.nota} ${styles[sit]}`}>
          <strong>{comNota ? formatarPontos(avaliacao.nota!) : '—'}</strong>
          <small>de {formatarPontos(avaliacao.valor)}</small>
        </span>
        <span className={styles.barra} aria-hidden="true">
          <span className={`${styles.barraValor} ${styles[sit]}`} style={{ width: `${largura}%` }} />
        </span>
      </span>
    </li>
  )
}

const NOMES_MESES = ['jan', 'fev', 'mar', 'abr', 'mai', 'jun', 'jul', 'ago', 'set', 'out', 'nov', 'dez']
