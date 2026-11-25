import { useCallback, useEffect, useState } from 'react'
import { useSearchParams } from 'react-router'
import { rotuloDoPeriodo } from '../../../../components/formulario/CampoData/datas'
import { CampoPeriodo, type Periodo } from '../../../../components/formulario/CampoPeriodo/CampoPeriodo'
import { Badge } from '../../../../components/ui/Badge/Badge'
import { Button } from '../../../../components/ui/Button/Button'
import { Icon } from '../../../../components/ui/Icon/Icon'
import { Paginacao } from '../../../../components/ui/Paginacao/Paginacao'
import { StatCard, StatCards } from '../../../../components/ui/StatCard/StatCard'
import { useListaPaginada, type PaginaComResumo } from '../../../../hooks/usePaginacao'
import { ApiError } from '../../../../services/api'
import {
  listarCategoriasDespesa,
  listarPaginaDeDespesas,
  mudarSituacaoDespesa,
  type PeriodoDespesas,
} from '../../../../services/financeiro/despesaService'
import type {
  CategoriaDespesa,
  Despesa,
  FiltroSituacaoDespesa,
  ResumoDespesas,
  SituacaoDespesa,
} from '../../../../types/financeiro/despesa'
import { formatarData } from '../../../../utils/formatacao'
import { formatarMoeda, hojeIso } from '../../../../utils/formatters'
import { ModalCategorias } from '../../components/ModalCategorias/ModalCategorias'
import { ModalDespesa } from '../../components/ModalDespesa/ModalDespesa'
import { FinanceiroLayout } from '../../layouts/FinanceiroLayout'
import styles from './Despesas.module.css'

/** "2026-10" -> "Outubro de 2026". */
function rotuloDoMes(anoMes: string): string {
  const [ano, mes] = anoMes.split('-').map(Number)
  const rotulo = new Date(ano, mes - 1, 1).toLocaleDateString('pt-BR', { month: 'long', year: 'numeric' })
  return rotulo.charAt(0).toUpperCase() + rotulo.slice(1)
}

/** "2026-02" -> do dia 01 ao 28 de fevereiro, como o backend filtra. */
function periodoDoMes(anoMes: string): PeriodoDespesas {
  const [ano, mes] = anoMes.split('-').map(Number)
  // Dia 0 do mês seguinte é o último deste.
  const ultimoDia = new Date(ano, mes, 0).getDate()
  return { vencimentoDe: `${anoMes}-01`, vencimentoAte: `${anoMes}-${String(ultimoDia).padStart(2, '0')}` }
}

/** Período da lista: o mês atual (padrão), todas as datas ou um intervalo escolhido no calendário. */
type PeriodoDaLista = { tipo: 'MES' } | { tipo: 'TODAS' } | { tipo: 'INTERVALO'; periodo: Periodo }

const ATALHOS_DE_PERIODO: { tipo: 'MES' | 'TODAS'; rotulo: string }[] = [
  { tipo: 'MES', rotulo: 'Este mês' },
  { tipo: 'TODAS', rotulo: 'Todas as datas' },
]

/** O intervalo de vencimento que o período pede ao backend; "todas as datas" não manda nenhum. */
function consultaDoPeriodo(periodo: PeriodoDaLista, mesAtual: string): PeriodoDespesas {
  if (periodo.tipo === 'MES') return periodoDoMes(mesAtual)
  if (periodo.tipo === 'TODAS') return {}
  return { vencimentoDe: periodo.periodo.de, vencimentoAte: periodo.periodo.ate }
}

/** Nome do período, para os cards: "Novembro de 2026", "Todas as datas" ou "05/10/2026 – 20/10/2026". */
function rotuloDoPeriodoDaLista(periodo: PeriodoDaLista, mesAtual: string): string {
  if (periodo.tipo === 'MES') return rotuloDoMes(mesAtual)
  if (periodo.tipo === 'TODAS') return 'Todas as datas'
  return rotuloDoPeriodo(periodo.periodo)
}

/** Até onde o calendário vai: despesas futuras (parcelas, contratos) também são lançadas. */
function ultimoDiaDoCalendario(hoje: string): string {
  return `${Number(hoje.slice(0, 4)) + 2}-12-31`
}

function mensagemDeErro(e: unknown, padrao: string): string {
  return e instanceof ApiError ? (e.detalhe ?? e.message) : padrao
}

const FILTROS: { valor: FiltroSituacaoDespesa; rotulo: string }[] = [
  { valor: 'TODAS', rotulo: 'Todas' },
  { valor: 'A_PAGAR', rotulo: 'A pagar' },
  { valor: 'PAGAS', rotulo: 'Pagas' },
]

/** O que cada pílula pede ao backend. "Todas" deixa as canceladas de fora. */
const SITUACOES_DO_FILTRO: Record<FiltroSituacaoDespesa, SituacaoDespesa[]> = {
  TODAS: ['A_PAGAR', 'PAGA'],
  A_PAGAR: ['A_PAGAR'],
  PAGAS: ['PAGA'],
}

/** A despesa entra na lista com o filtro escolhido? */
function passaNoFiltro(despesa: Despesa, filtro: FiltroSituacaoDespesa): boolean {
  return SITUACOES_DO_FILTRO[filtro].includes(despesa.situacao)
}

/** Soma e quantidade de uma situação no resumo do backend. */
function totalDa(resumo: ResumoDespesas | null, situacao: SituacaoDespesa) {
  const total = resumo?.porSituacao.find((t) => t.situacao === situacao)
  return { valor: total?.valor ?? 0, quantidade: total?.quantidade ?? 0 }
}

function plural(quantidade: number, singular: string, varias: string): string {
  return `${quantidade} ${quantidade === 1 ? singular : varias}`
}

/** O modal aberto: lançar uma nova, ou editar a despesa escolhida. */
type ModalAberto = { tipo: 'nova' } | { tipo: 'editar'; despesa: Despesa }

/**
 * Despesas do financeiro (IU21): por padrão as do mês atual, uma página por
 * vez (GET /despesas/pagina), com os totais do período somados no backend. O
 * filtro de data troca o período: todas as datas ou um intervalo do
 * calendário. Lançar e editar abrem o mesmo modal; marcar como paga fica junto
 * da situação de cada despesa.
 */
export function Despesas() {
  const [categorias, setCategorias] = useState<CategoriaDespesa[]>([])
  const [erroCategorias, setErroCategorias] = useState<string | null>(null)
  const [filtro, setFiltro] = useState<FiltroSituacaoDespesa>('TODAS')
  const [periodo, setPeriodo] = useState<PeriodoDaLista>({ tipo: 'MES' })
  const [atualizandoId, setAtualizandoId] = useState<string | null>(null)
  const [erroAcao, setErroAcao] = useState<string | null>(null)
  const [searchParams, setSearchParams] = useSearchParams()
  // ?nova=1: vem do atalho "Lançar uma despesa" do Início e já abre o lançamento.
  const [modal, setModal] = useState<ModalAberto | null>(() => (searchParams.has('nova') ? { tipo: 'nova' } : null))
  const [avisoLista, setAvisoLista] = useState<string | null>(null)
  // Quem lança várias despesas seguidas costuma repetir o responsável: o próximo modal já abre com ele.
  const [ultimoResponsavel, setUltimoResponsavel] = useState('')
  const [gerenciandoCategorias, setGerenciandoCategorias] = useState(false)

  // Tira o ?nova= já lido, para o modal não abrir de novo ao recarregar a página.
  useEffect(() => {
    if (!searchParams.has('nova')) return
    setSearchParams(
      (atuais) => {
        const proximos = new URLSearchParams(atuais)
        proximos.delete('nova')
        return proximos
      },
      { replace: true },
    )
  }, [searchParams, setSearchParams])

  useEffect(() => {
    const controller = new AbortController()
    listarCategoriasDespesa(controller.signal)
      .then(setCategorias)
      .catch((e: unknown) => {
        if (!controller.signal.aborted) {
          setErroCategorias(mensagemDeErro(e, 'Não foi possível carregar as categorias.'))
        }
      })
    return () => controller.abort()
  }, [])

  // Hoje e o mês atual (AAAA-MM), no fuso da escola. A lista abre no mês atual.
  const hoje = hojeIso()
  const mesAtual = hoje.slice(0, 7)

  // Tudo o que sai do período é lido aqui, antes da busca: daqui para baixo só as datas e o rótulo.
  const { vencimentoDe, vencimentoAte } = consultaDoPeriodo(periodo, mesAtual)
  const rotuloPeriodo = rotuloDoPeriodoDaLista(periodo, mesAtual)
  const tipoPeriodo = periodo.tipo
  const intervalo = periodo.tipo === 'INTERVALO' ? periodo.periodo : null

  // Datas ou situação novas trocam a função, e a lista volta à primeira página.
  const buscarDespesas = useCallback(
    (pagina: number, tamanho: number, signal: AbortSignal) =>
      listarPaginaDeDespesas(
        { pagina, tamanho, vencimentoDe, vencimentoAte, situacoes: SITUACOES_DO_FILTRO[filtro] },
        signal,
      ),
    [vencimentoDe, vencimentoAte, filtro],
  )
  const lista = useListaPaginada<PaginaComResumo<Despesa, ResumoDespesas>>(
    buscarDespesas,
    'Não foi possível carregar as despesas.',
  )
  const { paginacao, topo, carregando, atualizando, erro: erroLista, recarregar } = lista

  // Os cards somam o período inteiro no backend, sem a pílula de situação; cancelada não entra.
  const pagas = totalDa(lista.dados?.resumo ?? null, 'PAGA')
  const aPagar = totalDa(lista.dados?.resumo ?? null, 'A_PAGAR')
  // "Já pago no mês", "Já pago no período" ou só "Já pago" (todas as datas).
  const recorte = tipoPeriodo === 'MES' ? 'mês' : tipoPeriodo === 'INTERVALO' ? 'período' : null
  const tituloDaLista =
    tipoPeriodo === 'MES' ? 'Despesas do mês' : tipoPeriodo === 'TODAS' ? 'Todas as despesas' : 'Despesas do período'
  const mensagemVazia =
    tipoPeriodo === 'MES'
      ? `Nenhuma despesa de ${rotuloDoMes(mesAtual).toLowerCase()} nesta situação.`
      : tipoPeriodo === 'TODAS'
        ? 'Nenhuma despesa nesta situação.'
        : 'Nenhuma despesa entre essas datas nesta situação.'

  function abrirModal(aberto: ModalAberto) {
    setAvisoLista(null)
    setErroAcao(null)
    setModal(aberto)
  }

  /** Marca como paga a despesa a pagar, ou desfaz o pagamento da paga. */
  async function handleAlternarSituacao(despesa: Despesa) {
    setErroAcao(null)
    setAvisoLista(null)
    setAtualizandoId(despesa.id)
    try {
      await mudarSituacaoDespesa(despesa.id, despesa.situacao === 'PAGA' ? 'A_PAGAR' : 'PAGA')
      // Os totais e a pílula escolhida mudam: a página vem de novo do backend.
      recarregar()
    } catch (e) {
      setErroAcao(mensagemDeErro(e, 'Não foi possível mudar a situação da despesa.'))
    } finally {
      setAtualizandoId(null)
    }
  }

  /**
   * Depois de lançar ou editar: confirma e lê a página e os totais de novo. A
   * lista só mostra o período e a situação filtrados, então avisa quando a
   * despesa fica de fora, para não parecer que ela sumiu.
   */
  function handleDespesaSalva(despesa: Despesa) {
    const nova = modal?.tipo === 'nova'
    setModal(null)
    if (despesa.responsavel) setUltimoResponsavel(despesa.responsavel)
    const salva = `Despesa "${despesa.descricao}" ${nova ? 'salva' : 'atualizada'}.`
    // Sem uma das pontas (todas as datas), ela não limita.
    const foraDoPeriodo =
      (vencimentoDe !== undefined && despesa.dataVencimento < vencimentoDe) ||
      (vencimentoAte !== undefined && despesa.dataVencimento > vencimentoAte)
    if (foraDoPeriodo) {
      if (tipoPeriodo === 'MES') {
        const mes = rotuloDoMes(despesa.dataVencimento.slice(0, 7)).toLowerCase()
        setAvisoLista(
          nova
            ? `${salva} Ela é de ${mes}, por isso não aparece na lista deste mês.`
            : `${salva} Agora ela é de ${mes}, por isso saiu da lista deste mês.`,
        )
      } else {
        const data = formatarData(despesa.dataVencimento)
        setAvisoLista(
          nova
            ? `${salva} Ela vence em ${data}, fora das datas escolhidas, por isso não aparece na lista.`
            : `${salva} Agora ela vence em ${data}, fora das datas escolhidas, por isso saiu da lista.`,
        )
      }
    } else if (!passaNoFiltro(despesa, filtro)) {
      setAvisoLista(`${salva} Ela não aparece com o filtro atual.`)
    } else {
      setAvisoLista(salva)
    }
    recarregar()
  }

  return (
    <FinanceiroLayout
      titulo="Despesas"
      subtitulo="Tudo o que a escola gasta: contas, salários, materiais e consertos."
      acao={
        <div className={styles.acoesTopo}>
          <Button onClick={() => setGerenciandoCategorias(true)}>Gerenciar categorias</Button>
          <Button variant="primary" icon="plus" onClick={() => abrirModal({ tipo: 'nova' })}>
            Nova despesa
          </Button>
        </div>
      }
    >
      <div className={styles.conteudo}>
        <StatCards rotulo="Resumo das despesas">
          <StatCard
            label={recorte ? `Já pago no ${recorte}` : 'Já pago'}
            value={formatarMoeda(pagas.valor)}
            descricao={plural(pagas.quantidade, 'despesa quitada', 'despesas quitadas')}
          />
          <StatCard
            label="Ainda falta pagar"
            value={formatarMoeda(aPagar.valor)}
            descricao={plural(aPagar.quantidade, 'despesa em aberto', 'despesas em aberto')}
            destaque
          />
          <StatCard
            label={recorte ? `Total do ${recorte}` : 'Total'}
            value={formatarMoeda(pagas.valor + aPagar.valor)}
            descricao={rotuloPeriodo}
          />
        </StatCards>

        <section className={styles.cartao} aria-labelledby="tituloDespesas">
          <header className={styles.cabecalho}>
            <h2 id="tituloDespesas" className={styles.tituloSecao}>
              {tituloDaLista}
            </h2>
            <div className={styles.filtros}>
              {/* Data: o mês atual por padrão, todas as datas ou um intervalo do calendário. */}
              <div className={styles.pilulas} role="group" aria-label="Filtrar despesas por data">
                {ATALHOS_DE_PERIODO.map((a) => (
                  <button
                    key={a.tipo}
                    type="button"
                    aria-pressed={tipoPeriodo === a.tipo}
                    className={`${styles.pilula} ${tipoPeriodo === a.tipo ? styles.pilulaAtiva : ''}`}
                    onClick={() => setPeriodo({ tipo: a.tipo })}
                  >
                    {a.rotulo}
                  </button>
                ))}
                <CampoPeriodo
                  valor={intervalo}
                  onChange={(escolhido) => setPeriodo({ tipo: 'INTERVALO', periodo: escolhido })}
                  placeholder="Escolher datas"
                  max={ultimoDiaDoCalendario(hoje)}
                  mesInicial={hoje}
                  aria-pressed={intervalo !== null}
                  className={`${styles.pilula} ${intervalo ? styles.pilulaAtiva : ''}`}
                />
              </div>
              <div className={styles.pilulas} role="group" aria-label="Filtrar despesas por situação">
                {FILTROS.map((f) => (
                  <button
                    key={f.valor}
                    type="button"
                    aria-pressed={filtro === f.valor}
                    className={`${styles.pilula} ${filtro === f.valor ? styles.pilulaAtiva : ''}`}
                    onClick={() => setFiltro(f.valor)}
                  >
                    {f.rotulo}
                  </button>
                ))}
              </div>
            </div>
          </header>

          {/* Sem as categorias, o modal não tem o que oferecer na escolha. */}
          {erroCategorias && (
            <p className={styles.erro} role="alert">
              {erroCategorias}
            </p>
          )}
          {erroAcao && (
            <p className={styles.erro} role="alert">
              {erroAcao}
            </p>
          )}
          {avisoLista && (
            <p className={styles.sucesso} role="status">
              {avisoLista}
            </p>
          )}

          <div ref={topo} className={styles.rolagem} aria-busy={atualizando}>
            {carregando ? (
              <p className={styles.vazio} role="status">
                Carregando despesas…
              </p>
            ) : erroLista || !paginacao ? (
              <div className={styles.vazio} role="alert">
                <span>{erroLista}</span>
                <Button size="sm" onClick={recarregar}>
                  Tentar novamente
                </Button>
              </div>
            ) : paginacao.total === 0 ? (
              <p className={styles.vazio}>{mensagemVazia}</p>
            ) : (
              <table className={styles.tabela}>
                <thead>
                  <tr>
                    <th scope="col">Descrição</th>
                    <th scope="col">Situação</th>
                    <th scope="col">Categoria</th>
                    <th scope="col">Responsável</th>
                    <th scope="col">Data</th>
                    <th scope="col" className={styles.numero}>
                      Valor
                    </th>
                    <th scope="col" className={styles.acao}>
                      <span className={styles.somenteLeitor}>Ação</span>
                    </th>
                  </tr>
                </thead>
                <tbody>
                  {paginacao.itensDaPagina.map((d) => (
                    <tr key={d.id}>
                      <td className={styles.colPrincipal}>
                        <span className={styles.descricao}>{d.descricao}</span>
                      </td>
                      {/* Pagar (ou desfazer) fica onde a situação aparece. */}
                      <td data-rotulo="Situação" className={styles.colSituacao}>
                        <div className={styles.situacao}>
                          <Badge tone={d.situacao === 'PAGA' ? 'success' : 'warning'}>
                            {d.situacao === 'PAGA' ? 'Paga' : 'A pagar'}
                          </Badge>
                          <button
                            type="button"
                            className={`${styles.marcar} ${d.situacao === 'PAGA' ? styles.desfazer : ''}`}
                            aria-label={
                              d.situacao === 'PAGA'
                                ? `Desfazer o pagamento de ${d.descricao}`
                                : `Marcar ${d.descricao} como paga`
                            }
                            disabled={atualizandoId !== null}
                            onClick={() => handleAlternarSituacao(d)}
                          >
                            {d.situacao === 'PAGA' ? null : <Icon name="check" size={14} strokeWidth={2.6} />}
                            {atualizandoId === d.id
                              ? 'Salvando…'
                              : d.situacao === 'PAGA'
                                ? 'Desfazer'
                                : 'Marcar como paga'}
                          </button>
                        </div>
                      </td>
                      <td data-rotulo="Categoria" className={styles.secundario}>
                        {d.categoriaNome}
                      </td>
                      <td data-rotulo="Responsável" className={styles.secundario}>
                        {/* Despesa antiga, lançada antes de o campo existir. */}
                        {d.responsavel ?? '—'}
                      </td>
                      <td data-rotulo="Data" className={styles.data}>
                        {formatarData(d.dataVencimento)}
                      </td>
                      <td data-rotulo="Valor" className={styles.numero}>
                        {formatarMoeda(d.valor)}
                      </td>
                      <td className={styles.acao}>
                        <Button
                          size="sm"
                          variant="outline"
                          icon="edit"
                          aria-label={`Editar ${d.descricao}`}
                          disabled={atualizandoId !== null}
                          onClick={() => abrirModal({ tipo: 'editar', despesa: d })}
                        >
                          Editar
                        </Button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            )}
          </div>
          {paginacao && !erroLista && (
            <Paginacao paginacao={paginacao} rotulo="Páginas das despesas" carregando={atualizando} />
          )}
        </section>
      </div>

      {modal && (
        <ModalDespesa
          despesa={modal.tipo === 'editar' ? modal.despesa : undefined}
          categorias={categorias}
          responsavelSugerido={ultimoResponsavel}
          onFechar={() => setModal(null)}
          onSalva={handleDespesaSalva}
        />
      )}
      {gerenciandoCategorias && (
        <ModalCategorias
          categorias={categorias}
          onCriada={(categoria) => setCategorias((atuais) => [...atuais, categoria])}
          onFechar={() => setGerenciandoCategorias(false)}
        />
      )}
    </FinanceiroLayout>
  )
}
