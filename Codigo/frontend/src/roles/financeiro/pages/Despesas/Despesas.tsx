import { useCallback, useEffect, useState, type FormEvent } from 'react'
import { Button } from '../../../../components/Button/Button'
import { Paginacao } from '../../../../components/Paginacao/Paginacao'
import { useListaPaginada, type PaginaComResumo } from '../../../../hooks/usePaginacao'
import { ApiError } from '../../../../services/api'
import {
  criarDespesa,
  listarCategoriasDespesa,
  listarPaginaDeDespesas,
  mudarSituacaoDespesa,
  type PeriodoDespesas,
} from '../../../../services/despesaService'
import type {
  CategoriaDespesa,
  Despesa,
  FiltroSituacaoDespesa,
  ResumoDespesas,
  SituacaoDespesa,
} from '../../../../types/despesa'
import { formatarMoeda, hojeIso } from '../../../../utils/formatters'
import { ModalCategorias } from '../../components/ModalCategorias/ModalCategorias'
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

function mensagemDeErro(e: unknown, padrao: string): string {
  return e instanceof ApiError ? (e.detalhe ?? e.message) : padrao
}

/** Campo do formulário que a validação recusou; o valor é o id do controle. */
type CampoDespesa = 'descricao' | 'valor' | 'data'

const ERRO_FORM_ID = 'erroDespesa'

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

/**
 * Despesas do financeiro (IU21): as do mês atual, uma página por vez
 * (GET /despesas/pagina), com os totais do mês somados no backend.
 */
export function Despesas() {
  const [categorias, setCategorias] = useState<CategoriaDespesa[]>([])
  const [erroCategorias, setErroCategorias] = useState<string | null>(null)
  const [filtro, setFiltro] = useState<FiltroSituacaoDespesa>('TODAS')
  const [atualizandoId, setAtualizandoId] = useState<string | null>(null)
  const [erroAcao, setErroAcao] = useState<string | null>(null)

  // Estado do formulário "Lançar uma despesa"
  const [descricao, setDescricao] = useState('')
  const [categoriaId, setCategoriaId] = useState<string | null>(null)
  const [valor, setValor] = useState('')
  const [dataVencimento, setDataVencimento] = useState(hojeIso)
  const [situacao, setSituacao] = useState<SituacaoDespesa>('A_PAGAR')
  const [salvando, setSalvando] = useState(false)
  const [erroForm, setErroForm] = useState<string | null>(null)
  const [campoInvalido, setCampoInvalido] = useState<CampoDespesa | null>(null)
  const [sucessoForm, setSucessoForm] = useState<string | null>(null)
  const [gerenciandoCategorias, setGerenciandoCategorias] = useState(false)

  useEffect(() => {
    const controller = new AbortController()
    listarCategoriasDespesa(controller.signal)
      .then((dadosCategorias) => {
        setCategorias(dadosCategorias)
        setCategoriaId(dadosCategorias[0]?.id ?? null)
      })
      .catch((e: unknown) => {
        if (!controller.signal.aborted) {
          setErroCategorias(mensagemDeErro(e, 'Não foi possível carregar as categorias.'))
        }
      })
    return () => controller.abort()
  }, [])

  // Mês atual (AAAA-MM), no fuso da escola. A tela é a lista do mês: o histórico fica no backend.
  const mesAtual = hojeIso().slice(0, 7)

  const buscarDespesas = useCallback(
    (pagina: number, tamanho: number, signal: AbortSignal) =>
      listarPaginaDeDespesas(
        { pagina, tamanho, ...periodoDoMes(mesAtual), situacoes: SITUACOES_DO_FILTRO[filtro] },
        signal,
      ),
    [mesAtual, filtro],
  )
  const lista = useListaPaginada<PaginaComResumo<Despesa, ResumoDespesas>>(
    buscarDespesas,
    'Não foi possível carregar as despesas.',
  )
  const { paginacao, topo, carregando, atualizando, erro: erroLista, recarregar } = lista

  // Os cards somam o mês inteiro no backend, sem a pílula escolhida; cancelada não entra.
  const pagas = totalDa(lista.dados?.resumo ?? null, 'PAGA')
  const aPagar = totalDa(lista.dados?.resumo ?? null, 'A_PAGAR')
  const totais = {
    pago: pagas.valor,
    qtdPagas: pagas.quantidade,
    faltaPagar: aPagar.valor,
    qtdAPagar: aPagar.quantidade,
    total: pagas.valor + aPagar.valor,
  }

  /** Marca como paga a despesa a pagar, ou desfaz o pagamento da paga. */
  async function handleAlternarSituacao(despesa: Despesa) {
    setErroAcao(null)
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

  /** Mostra o erro, marca o campo e leva o foco até ele. */
  function recusar(campo: CampoDespesa, mensagem: string) {
    setErroForm(mensagem)
    setCampoInvalido(campo)
    document.getElementById(campo)?.focus()
  }

  /** Ao editar o formulário, a confirmação anterior sai; ao corrigir o campo recusado, o erro também. */
  function editou(campo: CampoDespesa) {
    setSucessoForm(null)
    if (campoInvalido !== campo) return
    setErroForm(null)
    setCampoInvalido(null)
  }

  /** Atributos de acessibilidade do controle, conforme a validação. */
  function invalidez(campo: CampoDespesa) {
    const invalido = campoInvalido === campo
    return { 'aria-invalid': invalido, 'aria-describedby': invalido ? ERRO_FORM_ID : undefined }
  }

  async function handleSalvarDespesa(e: FormEvent) {
    e.preventDefault()
    setErroForm(null)
    setCampoInvalido(null)
    setSucessoForm(null)

    if (!descricao.trim()) {
      recusar('descricao', 'Informe a descrição do que foi pago.')
      return
    }

    const valorLimpo = valor
      .replace(/^R\$\s*/i, '')
      .replace(/\./g, '')
      .replace(',', '.')
      .trim()
    const valorNumerico = parseFloat(valorLimpo)

    if (isNaN(valorNumerico) || valorNumerico <= 0) {
      recusar('valor', 'Informe um valor maior que zero (ex.: 150,00).')
      return
    }

    if (!dataVencimento) {
      recusar('data', 'Informe a data da despesa.')
      return
    }

    if (!categoriaId) {
      setErroForm('Escolha a categoria da despesa.')
      return
    }

    setSalvando(true)
    try {
      const nova = await criarDespesa({
        descricao: descricao.trim(),
        categoriaId,
        valor: valorNumerico,
        dataVencimento,
        paga: situacao === 'PAGA',
      })

      recarregar()
      setDescricao('')
      setValor('')
      // A lista só mostra o mês atual e a situação filtrada: avisa quando a
      // despesa salva fica de fora, para não parecer que ela sumiu.
      const salva = `Despesa "${nova.descricao}" salva.`
      if (!nova.dataVencimento.startsWith(mesAtual)) {
        const mes = rotuloDoMes(nova.dataVencimento.slice(0, 7)).toLowerCase()
        setSucessoForm(`${salva} Ela é de ${mes}, por isso não aparece na lista deste mês.`)
      } else if (!passaNoFiltro(nova, filtro)) {
        setSucessoForm(`${salva} Ela não aparece com o filtro atual.`)
      } else {
        setSucessoForm(salva)
      }
    } catch (e) {
      setErroForm(mensagemDeErro(e, 'Erro ao salvar despesa. Tente novamente.'))
    } finally {
      setSalvando(false)
    }
  }

  function formatarDataBr(isoDate: string): string {
    if (!isoDate) return ''
    const partes = isoDate.split('-')
    if (partes.length === 3) return `${partes[2]}/${partes[1]}/${partes[0]}`
    return isoDate
  }

  return (
    <FinanceiroLayout
      titulo="Despesas"
      subtitulo="Tudo o que a escola gasta: contas, salários, materiais e consertos."
    >
      <div className={styles.conteudoGrid}>
        {/* Coluna Esquerda: Estatísticas e Tabela de Despesas */}
        <div className={styles.colunaEsquerda}>
          {/* Cards de Resumo Estatístico */}
          <section className={styles.statsGrid} aria-label="Resumo das despesas">
            <div className={styles.statCard}>
              <span className={styles.statLabel}>Já pago no mês</span>
              <strong className={styles.statValor}>{formatarMoeda(totais.pago)}</strong>
              <span className={styles.statSubtitulo}>
                {totais.qtdPagas} {totais.qtdPagas === 1 ? 'despesa quitada' : 'despesas quitadas'}
              </span>
            </div>

            <div className={styles.statCard}>
              <span className={styles.statLabel}>Ainda falta pagar</span>
              <strong className={`${styles.statValor} ${styles.statValorDestaque}`}>
                {formatarMoeda(totais.faltaPagar)}
              </strong>
              <span className={styles.statSubtitulo}>
                {totais.qtdAPagar} {totais.qtdAPagar === 1 ? 'despesa em aberto' : 'despesas em aberto'}
              </span>
            </div>

            <div className={styles.statCard}>
              <span className={styles.statLabel}>Total do mês</span>
              <strong className={styles.statValor}>{formatarMoeda(totais.total)}</strong>
              <span className={styles.statSubtitulo}>{rotuloDoMes(mesAtual)}</span>
            </div>
          </section>

          {/* Tabela de Despesas do Mês */}
          <section className={styles.tabelaCard}>
            <header className={styles.tabelaHeader}>
              <h2 className={styles.tabelaTitulo}>Despesas do mês</h2>
              <div className={styles.filtros} role="group" aria-label="Filtrar despesas por situação">
                {FILTROS.map((f) => (
                  <button
                    key={f.valor}
                    type="button"
                    aria-pressed={filtro === f.valor}
                    className={`${styles.filtroBtn} ${filtro === f.valor ? styles.filtroAtivo : ''}`}
                    onClick={() => setFiltro(f.valor)}
                  >
                    {f.rotulo}
                  </button>
                ))}
              </div>
            </header>

            {erroAcao && (
              <p className={styles.erroForm} role="alert">
                {erroAcao}
              </p>
            )}

            <div ref={topo} className={styles.tabelaWrapper} aria-busy={atualizando}>
              {carregando ? (
                <div className={styles.vazio} role="status">
                  Carregando despesas…
                </div>
              ) : erroLista || !paginacao ? (
                <div className={styles.vazio} role="alert">
                  <span>{erroLista}</span>
                  <Button size="sm" onClick={recarregar}>
                    Tentar novamente
                  </Button>
                </div>
              ) : paginacao.total === 0 ? (
                <div className={styles.vazio}>Nenhuma despesa de {rotuloDoMes(mesAtual).toLowerCase()} nesta situação.</div>
              ) : (
                <table className={styles.tabela}>
                  <thead>
                    <tr>
                      <th scope="col" className={styles.thDescricao}>
                        Descrição
                      </th>
                      <th scope="col" className={styles.thCategoria}>
                        Categoria
                      </th>
                      <th scope="col" className={styles.thData}>
                        Data
                      </th>
                      <th scope="col" className={styles.thValor}>
                        Valor
                      </th>
                      <th scope="col" className={styles.thAcao}>
                        <span className={styles.somenteLeitor}>Ação</span>
                      </th>
                    </tr>
                  </thead>
                  <tbody>
                    {paginacao.itensDaPagina.map((d) => (
                      <tr key={d.id} className={styles.tr}>
                        <td className={styles.tdDescricao}>
                          <div className={styles.descricaoEBadge}>
                            <span className={styles.descricaoTexto}>{d.descricao}</span>
                            <span
                              className={`${styles.badge} ${
                                d.situacao === 'PAGA' ? styles.badgePaga : styles.badgeAPagar
                              }`}
                            >
                              {d.situacao === 'PAGA' ? 'Paga' : 'A pagar'}
                            </span>
                          </div>
                        </td>
                        <td className={styles.tdCategoria} data-rotulo="Categoria">
                          {d.categoriaNome}
                        </td>
                        <td className={styles.tdData} data-rotulo="Data">
                          {formatarDataBr(d.dataVencimento)}
                        </td>
                        <td className={styles.tdValor} data-rotulo="Valor">
                          {formatarMoeda(d.valor)}
                        </td>
                        <td className={styles.tdAcao}>
                          <Button
                            size="sm"
                            variant={d.situacao === 'PAGA' ? 'outline' : 'primary'}
                            disabled={atualizandoId !== null}
                            onClick={() => handleAlternarSituacao(d)}
                          >
                            {atualizandoId === d.id
                              ? 'Salvando…'
                              : d.situacao === 'PAGA'
                                ? 'Desfazer'
                                : 'Marcar como paga'}
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

        {/* Coluna Direita: Formulário Lançar uma despesa */}
        <aside className={styles.colunaDireita}>
          <div className={styles.formCard}>
            <h2 className={styles.formTitulo}>Lançar uma despesa</h2>
            <form onSubmit={handleSalvarDespesa} className={styles.formulario} noValidate>
              {/* O que foi pago */}
              <div className={styles.campo}>
                <label htmlFor="descricao" className={styles.label}>
                  O que foi pago
                </label>
                <input
                  id="descricao"
                  type="text"
                  className={styles.input}
                  placeholder="Ex.: conta de água"
                  value={descricao}
                  onChange={(e) => {
                    setDescricao(e.target.value)
                    editou('descricao')
                  }}
                  required
                  {...invalidez('descricao')}
                />
              </div>

              {/* Categoria */}
              <div className={styles.campo}>
                <span id="rotuloCategoria" className={styles.label}>
                  Categoria
                </span>
                <div className={styles.categoriasGrid} role="group" aria-labelledby="rotuloCategoria">
                  {categorias.map((cat) => (
                    <button
                      key={cat.id}
                      type="button"
                      aria-pressed={categoriaId === cat.id}
                      className={`${styles.catBtn} ${categoriaId === cat.id ? styles.catBtnAtivo : ''}`}
                      onClick={() => setCategoriaId(cat.id)}
                    >
                      {cat.nome}
                    </button>
                  ))}
                </div>
                {erroCategorias && (
                  <p className={styles.erroForm} role="alert">
                    {erroCategorias}
                  </p>
                )}
              </div>

              {/* Valor e Data */}
              <div className={styles.gridDuplo}>
                <div className={styles.campo}>
                  <label htmlFor="valor" className={styles.label}>
                    Valor
                  </label>
                  <input
                    id="valor"
                    type="text"
                    inputMode="decimal"
                    className={styles.input}
                    placeholder="R$ 0,00"
                    value={valor}
                    onChange={(e) => {
                      setValor(e.target.value)
                      editou('valor')
                    }}
                    required
                    {...invalidez('valor')}
                  />
                </div>

                <div className={styles.campo}>
                  <label htmlFor="data" className={styles.label}>
                    Data
                  </label>
                  <input
                    id="data"
                    type="date"
                    className={styles.input}
                    value={dataVencimento}
                    onChange={(e) => {
                      setDataVencimento(e.target.value)
                      editou('data')
                    }}
                    required
                    {...invalidez('data')}
                  />
                </div>
              </div>

              {/* Situação */}
              <div className={styles.campo}>
                <span id="rotuloSituacao" className={styles.label}>
                  Situação
                </span>
                <div className={styles.situacaoGrid} role="group" aria-labelledby="rotuloSituacao">
                  <button
                    type="button"
                    aria-pressed={situacao === 'A_PAGAR'}
                    className={`${styles.situacaoBtn} ${
                      situacao === 'A_PAGAR' ? styles.situacaoBtnAtivo : ''
                    }`}
                    onClick={() => setSituacao('A_PAGAR')}
                  >
                    A pagar
                  </button>
                  <button
                    type="button"
                    aria-pressed={situacao === 'PAGA'}
                    className={`${styles.situacaoBtn} ${
                      situacao === 'PAGA' ? styles.situacaoBtnAtivo : ''
                    }`}
                    onClick={() => setSituacao('PAGA')}
                  >
                    Paga
                  </button>
                </div>
              </div>

              {erroForm && (
                <p id={ERRO_FORM_ID} className={styles.erroForm} role="alert">
                  {erroForm}
                </p>
              )}
              {sucessoForm && (
                <p className={styles.sucessoForm} role="status">
                  {sucessoForm}
                </p>
              )}

              <button
                type="submit"
                className={styles.botaoSalvar}
                disabled={salvando}
              >
                {salvando ? 'Salvando…' : 'Salvar despesa'}
              </button>
              <button type="button" className={styles.linkCategorias} onClick={() => setGerenciandoCategorias(true)}>
                Gerenciar categorias
              </button>
            </form>
          </div>
        </aside>
      </div>

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

