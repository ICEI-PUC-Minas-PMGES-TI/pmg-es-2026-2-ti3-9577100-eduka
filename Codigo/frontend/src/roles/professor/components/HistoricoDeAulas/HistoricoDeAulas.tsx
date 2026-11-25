import { useEffect, useId, useRef, useState } from 'react'
import { paraData, rotuloDoPeriodo } from '../../../../components/formulario/CampoData/datas'
import { CampoPeriodo, type Periodo } from '../../../../components/formulario/CampoPeriodo/CampoPeriodo'
import { Button } from '../../../../components/ui/Button/Button'
import { Icon } from '../../../../components/ui/Icon/Icon'
import type { AulaResumo, FiltroDeAulas } from '../../../../types/academico/aula'
import { hojeIso, plural } from '../../../../utils/formatters'
import { formatarData } from '../../../../utils/formatacao'
import { useHistoricoDeAulas } from '../../hooks/useHistoricoDeAulas'
import styles from './HistoricoDeAulas.module.css'

/** Filtro de datas: todas (padrão), o mês atual ou um intervalo do calendário. */
type PeriodoDoHistorico = { tipo: 'TODAS' } | { tipo: 'MES' } | { tipo: 'INTERVALO'; periodo: Periodo }

const DIAS_CURTOS = ['Dom', 'Seg', 'Ter', 'Qua', 'Qui', 'Sex', 'Sáb']
const NOME_DO_MES = new Intl.DateTimeFormat('pt-BR', { month: 'long' })
/** Espera da digitação antes de buscar. */
const ESPERA_BUSCA_MS = 300

function datasDoPeriodo(periodo: PeriodoDoHistorico, hoje: string): Pick<FiltroDeAulas, 'de' | 'ate'> {
  if (periodo.tipo === 'TODAS') return {}
  if (periodo.tipo === 'MES') return { de: `${hoje.slice(0, 7)}-01`, ate: hoje }
  return periodo.periodo
}

/** "2026-11" -> "novembro". */
function nomeDoMes(mes: string): string {
  return NOME_DO_MES.format(paraData(`${mes}-01`) as Date)
}

/** "2026-11" -> "Novembro 2026". */
function tituloDoMes(mes: string): string {
  const nome = nomeDoMes(mes)
  return `${nome.charAt(0).toUpperCase()}${nome.slice(1)} ${mes.slice(0, 4)}`
}

type HistoricoDeAulasProps = {
  turmaId: number
  disciplinaId: number
  /** Aula aberta no formulário, destacada na lista. */
  aulaAbertaId?: string
  /** Muda depois de salvar ou excluir uma aula, para o histórico ler de novo. */
  versao: number
  onAbrir: (aula: AulaResumo) => void
}

/**
 * Histórico de aulas da disciplina na turma, agrupado por mês (o mais recente
 * primeiro) e carregado mês a mês. Cada linha mostra o dia, o conteúdo e as
 * faltas, e abre a aula no formulário. Filtra por data e pelo conteúdo.
 */
export function HistoricoDeAulas({ turmaId, disciplinaId, aulaAbertaId, versao, onAbrir }: HistoricoDeAulasProps) {
  const hoje = hojeIso()
  const tituloId = useId()
  const [periodo, setPeriodo] = useState<PeriodoDoHistorico>({ tipo: 'TODAS' })
  const [buscaAberta, setBuscaAberta] = useState(false)
  const [textoBusca, setTextoBusca] = useState('')
  const [busca, setBusca] = useState('')
  const campoBusca = useRef<HTMLInputElement>(null)

  // Busca depois de uma pausa na digitação, não a cada tecla.
  useEffect(() => {
    const espera = setTimeout(() => setBusca(textoBusca.trim()), ESPERA_BUSCA_MS)
    return () => clearTimeout(espera)
  }, [textoBusca])

  useEffect(() => {
    if (buscaAberta) campoBusca.current?.focus()
  }, [buscaAberta])

  const historico = useHistoricoDeAulas(turmaId, disciplinaId, { ...datasDoPeriodo(periodo, hoje), busca })
  const { recarregar } = historico
  // Depois de salvar ou excluir, a página pede uma nova leitura.
  const primeiraVersao = useRef(versao)
  useEffect(() => {
    if (versao !== primeiraVersao.current) recarregar()
  }, [versao, recarregar])

  const meses = historico.meses ?? []
  const totalDeAulas = meses.reduce((total, m) => total + m.aulas, 0)
  const presencas = meses.reduce((total, m) => total + m.presentes, 0)
  const chamadas = presencas + meses.reduce((total, m) => total + m.faltas, 0)
  const frequencia = chamadas > 0 ? Math.round((presencas / chamadas) * 100) : null
  const intervalo = periodo.tipo === 'INTERVALO' ? periodo.periodo : null

  const vazio = busca
    ? `Nenhuma aula com "${busca}" no conteúdo${periodo.tipo === 'TODAS' ? '' : ' nessas datas'}.`
    : periodo.tipo === 'TODAS'
      ? 'Nenhuma aula registrada nesta turma ainda.'
      : periodo.tipo === 'MES'
        ? 'Nenhuma aula registrada neste mês.'
        : `Nenhuma aula registrada em ${rotuloDoPeriodo(periodo.periodo)}.`

  function fecharBusca() {
    setBuscaAberta(false)
    setTextoBusca('')
    setBusca('')
  }

  const segmentos: { tipo: 'TODAS' | 'MES'; rotulo: string }[] = [
    { tipo: 'TODAS', rotulo: 'Todas' },
    { tipo: 'MES', rotulo: 'Este mês' },
  ]

  // O mês do botão "Carregar": com o ano só quando muda de ano em relação ao último aberto.
  const ultimoAberto = meses[historico.carregados - 1]?.mes
  const proximo = historico.proximoMes?.mes
  const rotuloProximo = proximo
    ? `${nomeDoMes(proximo)}${ultimoAberto && ultimoAberto.slice(0, 4) !== proximo.slice(0, 4) ? ` de ${proximo.slice(0, 4)}` : ''}`
    : null

  return (
    <section
      className={`${styles.historico} ${historico.atualizando ? styles.atualizando : ''}`}
      aria-labelledby={tituloId}
      aria-busy={historico.carregando || historico.atualizando}
    >
      <header className={styles.cabecalho}>
        <div className={styles.titulos}>
          <h2 id={tituloId} className={styles.titulo}>
            Histórico de aulas
          </h2>
          {historico.meses && totalDeAulas > 0 && (
            <p className={styles.resumo}>
              {totalDeAulas} {plural(totalDeAulas, 'aula', 'aulas')}
              {frequencia !== null && ` · frequência média ${frequencia}%`}
            </p>
          )}
        </div>

        <div className={styles.filtros}>
          <div className={styles.segmentos} role="group" aria-label="Filtrar aulas por data">
            {segmentos.map((s) => (
              <button
                key={s.tipo}
                type="button"
                aria-pressed={periodo.tipo === s.tipo}
                className={`${styles.segmento} ${periodo.tipo === s.tipo ? styles.segmentoAtivo : ''}`}
                onClick={() => setPeriodo({ tipo: s.tipo })}
              >
                {s.rotulo}
              </button>
            ))}
            <CampoPeriodo
              valor={intervalo}
              onChange={(escolhido) => setPeriodo({ tipo: 'INTERVALO', periodo: escolhido })}
              placeholder="Período"
              max={hoje}
              mesInicial={hoje}
              aria-pressed={intervalo !== null}
              className={`${styles.segmento} ${intervalo ? styles.segmentoAtivo : ''}`}
            />
          </div>

          {buscaAberta ? (
            <label className={styles.busca}>
              <Icon name="search" size={15} />
              <input
                ref={campoBusca}
                type="search"
                placeholder="Buscar no conteúdo"
                aria-label="Buscar no conteúdo das aulas"
                maxLength={100}
                value={textoBusca}
                onChange={(e) => setTextoBusca(e.target.value)}
                onKeyDown={(e) => {
                  if (e.key === 'Escape') fecharBusca()
                }}
              />
              <button type="button" className={styles.fecharBusca} aria-label="Fechar busca" onClick={fecharBusca}>
                <Icon name="close" size={14} />
              </button>
            </label>
          ) : (
            <button type="button" className={styles.botaoBuscar} onClick={() => setBuscaAberta(true)}>
              <Icon name="search" size={15} />
              Buscar
            </button>
          )}
        </div>
      </header>

      {historico.carregando && (
        <p className={styles.aviso} role="status">
          Carregando o histórico…
        </p>
      )}

      {!historico.carregando && historico.erro && (
        <div className={styles.erro} role="alert">
          <span>{historico.erro}</span>
          <Button size="sm" onClick={recarregar}>
            Tentar novamente
          </Button>
        </div>
      )}

      {!historico.carregando && !historico.erro && historico.meses && meses.length === 0 && (
        <p className={styles.aviso}>{vazio}</p>
      )}

      {!historico.carregando &&
        meses.slice(0, historico.carregados).map((mes) => (
          <div key={mes.mes} className={styles.grupo}>
            <h3 className={styles.tituloMes}>
              {tituloDoMes(mes.mes)} · {mes.aulas} {plural(mes.aulas, 'aula', 'aulas')}
            </h3>
            <ul className={styles.lista}>
              {(historico.aulasPorMes[mes.mes] ?? []).map((aula) => {
                const dia = paraData(aula.data) as Date
                const aberta = aula.id === aulaAbertaId
                const detalheId = `${tituloId}-${aula.id}`
                return (
                  <li key={aula.id}>
                    <button
                      type="button"
                      className={`${styles.linha} ${aberta ? styles.aberta : ''}`}
                      aria-label={`Abrir a aula de ${formatarData(aula.data)}`}
                      aria-describedby={detalheId}
                      aria-current={aberta || undefined}
                      disabled={aberta}
                      onClick={() => onAbrir(aula)}
                    >
                      <span className={styles.dia} aria-hidden="true">
                        <strong>{dia.getDate()}</strong>
                        <span>{DIAS_CURTOS[dia.getDay()]}</span>
                      </span>
                      <span id={detalheId} className={styles.detalhe}>
                        <span className={styles.conteudo}>{aula.conteudo}</span>
                        {aula.faltas > 0 ? (
                          <span className={styles.faltas}>
                            {aula.faltas} {plural(aula.faltas, 'falta', 'faltas')}
                          </span>
                        ) : (
                          <span className={styles.todosPresentes}>Todos presentes</span>
                        )}
                      </span>
                      <Icon name="chevronRight" size={16} className={styles.chevron} />
                    </button>
                  </li>
                )
              })}
            </ul>
          </div>
        ))}

      {!historico.carregando && !historico.erro && rotuloProximo && (
        <button
          type="button"
          className={styles.carregarMais}
          disabled={historico.carregandoMais}
          onClick={() => void historico.carregarMais()}
        >
          {historico.carregandoMais ? 'Carregando…' : `Carregar ${rotuloProximo}`}
          <Icon name="chevronDown" size={16} />
        </button>
      )}
    </section>
  )
}
