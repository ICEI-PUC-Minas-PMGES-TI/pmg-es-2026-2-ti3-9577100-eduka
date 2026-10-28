import { useEffect, useMemo, useState, type FormEvent } from 'react'
import { ApiError } from '../../../../services/api'
import {
  criarDespesa,
  listarCategoriasDespesa,
  listarDespesas,
} from '../../../../services/despesaService'
import type {
  CategoriaDespesa,
  Despesa,
  FiltroSituacaoDespesa,
  SituacaoDespesa,
} from '../../../../types/despesa'
import { formatarMoeda, hojeIso } from '../../../../utils/formatters'
import { FinanceiroLayout } from '../../layouts/FinanceiroLayout'
import styles from './Despesas.module.css'

/** "2026-10" -> "Outubro de 2026". */
function rotuloDoMes(anoMes: string): string {
  const [ano, mes] = anoMes.split('-').map(Number)
  const rotulo = new Date(ano, mes - 1, 1).toLocaleDateString('pt-BR', { month: 'long', year: 'numeric' })
  return rotulo.charAt(0).toUpperCase() + rotulo.slice(1)
}

function mensagemDeErro(e: unknown, padrao: string): string {
  return e instanceof ApiError ? (e.detalhe ?? e.message) : padrao
}

export function Despesas() {
  const [despesas, setDespesas] = useState<Despesa[]>([])
  const [categorias, setCategorias] = useState<CategoriaDespesa[]>([])
  const [carregando, setCarregando] = useState(true)
  const [erroLista, setErroLista] = useState<string | null>(null)
  const [filtro, setFiltro] = useState<FiltroSituacaoDespesa>('TODAS')

  // Estado do formulário "Lançar uma despesa"
  const [descricao, setDescricao] = useState('')
  const [categoriaId, setCategoriaId] = useState<string | null>(null)
  const [valor, setValor] = useState('')
  const [dataVencimento, setDataVencimento] = useState(hojeIso)
  const [situacao, setSituacao] = useState<SituacaoDespesa>('A_PAGAR')
  const [salvando, setSalvando] = useState(false)
  const [erroForm, setErroForm] = useState<string | null>(null)

  useEffect(() => {
    const controller = new AbortController()
    Promise.all([listarDespesas(controller.signal), listarCategoriasDespesa(controller.signal)])
      .then(([dadosDespesas, dadosCategorias]) => {
        setDespesas(dadosDespesas)
        setCategorias(dadosCategorias)
        setCategoriaId(dadosCategorias[0]?.id ?? null)
      })
      .catch((e: unknown) => {
        if (!controller.signal.aborted) setErroLista(mensagemDeErro(e, 'Não foi possível carregar as despesas.'))
      })
      .finally(() => {
        if (!controller.signal.aborted) setCarregando(false)
      })

    return () => controller.abort()
  }, [])

  // Totais calculados dinamicamente; despesa cancelada não entra
  const totais = useMemo(() => {
    let pago = 0
    let qtdPagas = 0
    let faltaPagar = 0
    let qtdAPagar = 0
    // Só as despesas que vencem no mês atual (AAAA-MM), no fuso da escola
    const mesAtual = hojeIso().slice(0, 7)
    let totalMes = 0

    for (const d of despesas) {
      if (d.situacao !== 'CANCELADA' && d.dataVencimento.startsWith(mesAtual)) {
        totalMes += d.valor
      }

      if (d.situacao === 'PAGA') {
        pago += d.valor
        qtdPagas += 1
      } else if (d.situacao === 'A_PAGAR') {
        faltaPagar += d.valor
        qtdAPagar += 1
      }
    }

    return {
      pago,
      qtdPagas,
      faltaPagar,
      qtdAPagar,
      totalMes,
      rotuloMes: rotuloDoMes(mesAtual),
    }
  }, [despesas])

  // Despesas filtradas pela pílula selecionada e ordenadas por data
  const despesasFiltradas = useMemo(() => {
    let lista = despesas.filter((d) => d.situacao !== 'CANCELADA')

    if (filtro === 'A_PAGAR') {
      lista = lista.filter((d) => d.situacao === 'A_PAGAR')
    } else if (filtro === 'PAGAS') {
      lista = lista.filter((d) => d.situacao === 'PAGA')
    }

    // Datas ISO (AAAA-MM-DD) ordenam como texto
    return lista.sort((a, b) => a.dataVencimento.localeCompare(b.dataVencimento))
  }, [despesas, filtro])

  async function handleSalvarDespesa(e: FormEvent) {
    e.preventDefault()
    setErroForm(null)

    if (!descricao.trim()) {
      setErroForm('Informe a descrição do que foi pago.')
      return
    }

    const valorLimpo = valor
      .replace(/^R\$\s*/i, '')
      .replace(/\./g, '')
      .replace(',', '.')
      .trim()
    const valorNumerico = parseFloat(valorLimpo)

    if (isNaN(valorNumerico) || valorNumerico <= 0) {
      setErroForm('Informe um valor monetário maior que zero.')
      return
    }

    if (!dataVencimento) {
      setErroForm('Informe a data da despesa.')
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

      setDespesas((prev) => [...prev, nova])
      setDescricao('')
      setValor('')
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
              <strong className={styles.statValor}>{formatarMoeda(totais.totalMes)}</strong>
              <span className={styles.statSubtitulo}>{totais.rotuloMes}</span>
            </div>
          </section>

          {/* Tabela de Despesas do Mês */}
          <section className={styles.tabelaCard}>
            <header className={styles.tabelaHeader}>
              <h2 className={styles.tabelaTitulo}>Despesas do mês</h2>
              <div className={styles.filtros} role="tablist" aria-label="Filtrar despesas por situação">
                <button
                  type="button"
                  role="tab"
                  aria-selected={filtro === 'TODAS'}
                  className={`${styles.filtroBtn} ${filtro === 'TODAS' ? styles.filtroAtivo : ''}`}
                  onClick={() => setFiltro('TODAS')}
                >
                  Todas
                </button>
                <button
                  type="button"
                  role="tab"
                  aria-selected={filtro === 'A_PAGAR'}
                  className={`${styles.filtroBtn} ${filtro === 'A_PAGAR' ? styles.filtroAtivo : ''}`}
                  onClick={() => setFiltro('A_PAGAR')}
                >
                  A pagar
                </button>
                <button
                  type="button"
                  role="tab"
                  aria-selected={filtro === 'PAGAS'}
                  className={`${styles.filtroBtn} ${filtro === 'PAGAS' ? styles.filtroAtivo : ''}`}
                  onClick={() => setFiltro('PAGAS')}
                >
                  Pagas
                </button>
              </div>
            </header>

            <div className={styles.tabelaWrapper}>
              {carregando ? (
                <div className={styles.vazio}>Carregando despesas…</div>
              ) : erroLista ? (
                <div className={styles.vazio} role="alert">
                  {erroLista}
                </div>
              ) : despesasFiltradas.length === 0 ? (
                <div className={styles.vazio}>Nenhuma despesa encontrada para esta situação.</div>
              ) : (
                <table className={styles.tabela}>
                  <thead>
                    <tr>
                      <th scope="col" className={styles.thDescricao}>
                        DESCRIÇÃO
                      </th>
                      <th scope="col" className={styles.thCategoria}>
                        CATEGORIA
                      </th>
                      <th scope="col" className={styles.thData}>
                        DATA
                      </th>
                      <th scope="col" className={styles.thValor}>
                        VALOR
                      </th>
                    </tr>
                  </thead>
                  <tbody>
                    {despesasFiltradas.map((d) => (
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
                        <td className={styles.tdCategoria}>{d.categoriaNome}</td>
                        <td className={styles.tdData}>{formatarDataBr(d.dataVencimento)}</td>
                        <td className={styles.tdValor}>{formatarMoeda(d.valor)}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              )}
            </div>
          </section>
        </div>

        {/* Coluna Direita: Formulário Lançar uma despesa */}
        <aside className={styles.colunaDireita}>
          <div className={styles.formCard}>
            <h2 className={styles.formTitulo}>Lançar uma despesa</h2>
            <form onSubmit={handleSalvarDespesa} className={styles.formulario}>
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
                  onChange={(e) => setDescricao(e.target.value)}
                  required
                />
              </div>

              {/* Categoria */}
              <div className={styles.campo}>
                <span className={styles.label}>Categoria</span>
                <div className={styles.categoriasGrid}>
                  {categorias.map((cat) => (
                    <button
                      key={cat.id}
                      type="button"
                      className={`${styles.catBtn} ${categoriaId === cat.id ? styles.catBtnAtivo : ''}`}
                      onClick={() => setCategoriaId(cat.id)}
                    >
                      {cat.nome}
                    </button>
                  ))}
                </div>
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
                    className={styles.input}
                    placeholder="R$ 0,00"
                    value={valor}
                    onChange={(e) => setValor(e.target.value)}
                    required
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
                    onChange={(e) => setDataVencimento(e.target.value)}
                    required
                  />
                </div>
              </div>

              {/* Situação */}
              <div className={styles.campo}>
                <span className={styles.label}>Situação</span>
                <div className={styles.situacaoGrid}>
                  <button
                    type="button"
                    className={`${styles.situacaoBtn} ${
                      situacao === 'A_PAGAR' ? styles.situacaoBtnAtivo : ''
                    }`}
                    onClick={() => setSituacao('A_PAGAR')}
                  >
                    A pagar
                  </button>
                  <button
                    type="button"
                    className={`${styles.situacaoBtn} ${
                      situacao === 'PAGA' ? styles.situacaoBtnAtivo : ''
                    }`}
                    onClick={() => setSituacao('PAGA')}
                  >
                    Paga
                  </button>
                </div>
              </div>

              {erroForm && <p className={styles.erroForm}>{erroForm}</p>}

              <button
                type="submit"
                className={styles.botaoSalvar}
                disabled={salvando}
              >
                {salvando ? 'Salvando…' : 'Salvar despesa'}
              </button>
            </form>
          </div>
        </aside>
      </div>
    </FinanceiroLayout>
  )
}

