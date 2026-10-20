import { useEffect, useMemo, useState, type FormEvent } from 'react'
import {
  criarDespesa,
  listarDespesas,
} from '../../../../services/despesaService'
import {
  CATEGORIAS_DESPESA,
  DESPESAS_MOCK_INICIAIS,
  type CategoriaDespesa,
  type Despesa,
  type FiltroSituacaoDespesa,
  type SituacaoDespesa,
} from '../../../../types/despesa'
import { formatarMoeda } from '../../../../utils/formatters'
import { FinanceiroLayout } from '../../layouts/FinanceiroLayout'
import styles from './Despesas.module.css'

export function Despesas() {
  const [despesas, setDespesas] = useState<Despesa[]>(DESPESAS_MOCK_INICIAIS)
  const [filtro, setFiltro] = useState<FiltroSituacaoDespesa>('TODAS')

  // Estado do formulário "Lançar uma despesa"
  const [descricao, setDescricao] = useState('')
  const [categoria, setCategoria] = useState<CategoriaDespesa>('Contas')
  const [valor, setValor] = useState('')
  const [data, setData] = useState('2026-09-30')
  const [situacao, setSituacao] = useState<SituacaoDespesa>('A_PAGAR')
  const [salvando, setSalvando] = useState(false)
  const [erroForm, setErroForm] = useState<string | null>(null)

  useEffect(() => {
    const controller = new AbortController()
    listarDespesas(controller.signal)
      .then((dados) => {
        setDespesas(dados)
      })
      .catch(() => {
        // Fallback local garantido
      })

    return () => controller.abort()
  }, [])

  // Totais calculados dinamicamente
  const totais = useMemo(() => {
    let pago = 0
    let qtdPagas = 0
    let faltaPagar = 0
    let qtdAPagar = 0

    for (const d of despesas) {
      if (d.situacao === 'PAGA') {
        pago += d.valor
        qtdPagas += 1
      } else {
        faltaPagar += d.valor
        qtdAPagar += 1
      }
    }

    return {
      pago,
      qtdPagas,
      faltaPagar,
      qtdAPagar,
      total: pago + faltaPagar,
    }
  }, [despesas])

  // Despesas filtradas pela pílula selecionada e ordenadas por data
  const despesasFiltradas = useMemo(() => {
    let lista = [...despesas]

    if (filtro === 'A_PAGAR') {
      lista = lista.filter((d) => d.situacao === 'A_PAGAR')
    } else if (filtro === 'PAGAS') {
      lista = lista.filter((d) => d.situacao === 'PAGA')
    }

    return lista.sort((a, b) => new Date(a.data).getTime() - new Date(b.data).getTime())
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

    if (!data) {
      setErroForm('Informe a data da despesa.')
      return
    }

    setSalvando(true)
    try {
      const nova = await criarDespesa({
        descricao: descricao.trim(),
        categoria,
        valor: valorNumerico,
        data,
        situacao,
      })

      setDespesas((prev) => [...prev, nova])
      setDescricao('')
      setValor('')
    } catch {
      setErroForm('Erro ao salvar despesa. Tente novamente.')
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
              <span className={styles.statSubtitulo}>Setembro de 2026</span>
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
              {despesasFiltradas.length === 0 ? (
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
                        <td className={styles.tdCategoria}>{d.categoria}</td>
                        <td className={styles.tdData}>{formatarDataBr(d.data)}</td>
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
                  {CATEGORIAS_DESPESA.map((cat) => (
                    <button
                      key={cat}
                      type="button"
                      className={`${styles.catBtn} ${categoria === cat ? styles.catBtnAtivo : ''}`}
                      onClick={() => setCategoria(cat)}
                    >
                      {cat}
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
                    value={data}
                    onChange={(e) => setData(e.target.value)}
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

