import { useCallback, useEffect, useRef, useState, type FormEvent } from 'react'
import { Campo } from '../../../../components/formulario/Campo/Campo'
import { CampoData } from '../../../../components/formulario/CampoData/CampoData'
import { Badge } from '../../../../components/ui/Badge/Badge'
import { Button } from '../../../../components/ui/Button/Button'
import { Paginacao } from '../../../../components/ui/Paginacao/Paginacao'
import { StatCard, StatCards } from '../../../../components/ui/StatCard/StatCard'
import { useListaPaginada, type PaginaComResumo } from '../../../../hooks/usePaginacao'
import { ApiError } from '../../../../services/api'
import {
  criarDespesa,
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
import { ModalEditarDespesa } from '../../components/ModalEditarDespesa/ModalEditarDespesa'
import { FinanceiroLayout } from '../../layouts/FinanceiroLayout'
import { textoParaValor } from '../../valorEmReais'
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

/** Campo do formulário que a validação recusou; o valor é o `name` do controle. */
type CampoDespesa = 'descricao' | 'responsavel' | 'valor' | 'data'
type ErrosDespesa = Partial<Record<CampoDespesa, string>>

/** Mesmo limite da coluna RESPONSAVEL no backend. */
const MAX_RESPONSAVEL = 120

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

const SITUACOES_DO_LANCAMENTO: { valor: SituacaoDespesa; rotulo: string }[] = [
  { valor: 'A_PAGAR', rotulo: 'A pagar' },
  { valor: 'PAGA', rotulo: 'Paga' },
]

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

/**
 * Despesas do financeiro (IU21): as do mês atual, uma página por vez
 * (GET /despesas/pagina), com os totais do mês somados no backend, e o
 * lançamento ao lado.
 */
export function Despesas() {
  const [categorias, setCategorias] = useState<CategoriaDespesa[]>([])
  const [erroCategorias, setErroCategorias] = useState<string | null>(null)
  const [filtro, setFiltro] = useState<FiltroSituacaoDespesa>('TODAS')
  const [atualizandoId, setAtualizandoId] = useState<string | null>(null)
  const [erroAcao, setErroAcao] = useState<string | null>(null)
  const [editando, setEditando] = useState<Despesa | null>(null)
  const [avisoLista, setAvisoLista] = useState<string | null>(null)

  // Estado do formulário "Lançar uma despesa"
  const formRef = useRef<HTMLFormElement>(null)
  const [descricao, setDescricao] = useState('')
  const [responsavel, setResponsavel] = useState('')
  const [categoriaId, setCategoriaId] = useState<string | null>(null)
  const [valor, setValor] = useState('')
  const [dataVencimento, setDataVencimento] = useState(hojeIso)
  const [situacao, setSituacao] = useState<SituacaoDespesa>('A_PAGAR')
  const [salvando, setSalvando] = useState(false)
  const [erros, setErros] = useState<ErrosDespesa>({})
  const [erroForm, setErroForm] = useState<string | null>(null)
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

  /** Depois de editar: confirma e lê a página e os totais de novo (o valor ou o mês podem ter mudado). */
  function handleDespesaEditada(despesa: Despesa) {
    setEditando(null)
    setErroAcao(null)
    const salva = `Despesa "${despesa.descricao}" atualizada.`
    setAvisoLista(
      despesa.dataVencimento.startsWith(mesAtual)
        ? salva
        : `${salva} Agora ela é de ${rotuloDoMes(despesa.dataVencimento.slice(0, 7)).toLowerCase()}, por isso saiu da lista deste mês.`,
    )
    recarregar()
  }

  /** Ao editar o formulário, a confirmação anterior sai; ao corrigir o campo recusado, o erro dele também. */
  function editou(campo: CampoDespesa) {
    setSucessoForm(null)
    if (erros[campo]) setErros((atuais) => ({ ...atuais, [campo]: undefined }))
  }

  /** Erro em cada campo recusado; vazio quando o formulário pode ir ao backend. */
  function validar(): ErrosDespesa {
    const novos: ErrosDespesa = {}
    if (!descricao.trim()) novos.descricao = 'Informe a descrição do que foi pago.'
    if (!responsavel.trim()) novos.responsavel = 'Informe o responsável pela despesa.'
    if (!(textoParaValor(valor) > 0)) novos.valor = 'Informe um valor maior que zero (ex.: 150,00).'
    if (!dataVencimento) novos.data = 'Informe a data da despesa.'
    return novos
  }

  async function handleSalvarDespesa(e: FormEvent) {
    e.preventDefault()
    setErroForm(null)
    setSucessoForm(null)

    const novos = validar()
    setErros(novos)
    const primeiro = (Object.keys(novos) as CampoDespesa[])[0]
    if (primeiro) {
      formRef.current?.querySelector<HTMLElement>(`[name="${primeiro}"]`)?.focus()
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
        responsavel: responsavel.trim(),
        categoriaId,
        valor: textoParaValor(valor),
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

  return (
    <FinanceiroLayout
      titulo="Despesas"
      subtitulo="Tudo o que a escola gasta: contas, salários, materiais e consertos."
    >
      <div className={styles.conteudoGrid}>
        {/* Coluna esquerda: resumo e despesas do mês */}
        <div className={styles.colunaEsquerda}>
          <StatCards rotulo="Resumo das despesas">
            <StatCard
              label="Já pago no mês"
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
              label="Total do mês"
              value={formatarMoeda(pagas.valor + aPagar.valor)}
              descricao={rotuloDoMes(mesAtual)}
            />
          </StatCards>

          <section className={styles.cartao} aria-labelledby="tituloDespesas">
            <header className={styles.cabecalho}>
              <h2 id="tituloDespesas" className={styles.tituloSecao}>
                Despesas do mês
              </h2>
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
            </header>

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
                <p className={styles.vazio}>Nenhuma despesa de {rotuloDoMes(mesAtual).toLowerCase()} nesta situação.</p>
              ) : (
                <table className={styles.tabela}>
                  <thead>
                    <tr>
                      <th scope="col">Descrição</th>
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
                          <div className={styles.descricaoEBadge}>
                            <span className={styles.descricao}>{d.descricao}</span>
                            <Badge tone={d.situacao === 'PAGA' ? 'success' : 'warning'}>
                              {d.situacao === 'PAGA' ? 'Paga' : 'A pagar'}
                            </Badge>
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
                          <div className={styles.acoes}>
                          <Button
                            size="sm"
                            variant="outline"
                            icon="edit"
                            aria-label={`Editar ${d.descricao}`}
                            disabled={atualizandoId !== null}
                            onClick={() => {
                              setAvisoLista(null)
                              setEditando(d)
                            }}
                          >
                            Editar
                          </Button>
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
                          </div>
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

        {/* Coluna direita: lançar uma despesa */}
        <aside className={styles.colunaDireita}>
          <section className={styles.cartao} aria-labelledby="tituloLancar">
            <h2 id="tituloLancar" className={styles.tituloSecao}>
              Lançar uma despesa
            </h2>
            <form ref={formRef} onSubmit={handleSalvarDespesa} className={styles.formulario} noValidate>
              <Campo label="O que foi pago" erro={erros.descricao}>
                {(props) => (
                  <input
                    {...props}
                    name="descricao"
                    type="text"
                    placeholder="Ex.: conta de água"
                    value={descricao}
                    onChange={(e) => {
                      setDescricao(e.target.value)
                      editou('descricao')
                    }}
                  />
                )}
              </Campo>

              <Campo label="Responsável" erro={erros.responsavel}>
                {(props) => (
                  <input
                    {...props}
                    name="responsavel"
                    type="text"
                    placeholder="Ex.: Administração"
                    maxLength={MAX_RESPONSAVEL}
                    value={responsavel}
                    onChange={(e) => {
                      setResponsavel(e.target.value)
                      editou('responsavel')
                    }}
                  />
                )}
              </Campo>

              <div className={styles.grupo}>
                <span id="rotuloCategoria" className={styles.rotulo}>
                  Categoria
                </span>
                <div className={styles.escolhas} role="group" aria-labelledby="rotuloCategoria">
                  {categorias.map((cat) => (
                    <button
                      key={cat.id}
                      type="button"
                      aria-pressed={categoriaId === cat.id}
                      className={`${styles.escolha} ${categoriaId === cat.id ? styles.escolhaAtiva : ''}`}
                      onClick={() => setCategoriaId(cat.id)}
                    >
                      {cat.nome}
                    </button>
                  ))}
                </div>
                {erroCategorias && (
                  <p className={styles.erro} role="alert">
                    {erroCategorias}
                  </p>
                )}
              </div>

              <div className={styles.gridDuplo}>
                <Campo label="Valor" erro={erros.valor}>
                  {(props) => (
                    <input
                      {...props}
                      name="valor"
                      type="text"
                      inputMode="decimal"
                      placeholder="R$ 0,00"
                      value={valor}
                      onChange={(e) => {
                        setValor(e.target.value)
                        editou('valor')
                      }}
                    />
                  )}
                </Campo>
                <Campo label="Data" erro={erros.data}>
                  {(props) => (
                    <CampoData
                      {...props}
                      name="data"
                      valor={dataVencimento}
                      onChange={(data) => {
                        setDataVencimento(data)
                        editou('data')
                      }}
                    />
                  )}
                </Campo>
              </div>

              <div className={styles.grupo}>
                <span id="rotuloSituacao" className={styles.rotulo}>
                  Situação
                </span>
                <div className={`${styles.escolhas} ${styles.duasEscolhas}`} role="group" aria-labelledby="rotuloSituacao">
                  {SITUACOES_DO_LANCAMENTO.map((opcao) => (
                    <button
                      key={opcao.valor}
                      type="button"
                      aria-pressed={situacao === opcao.valor}
                      className={`${styles.escolha} ${situacao === opcao.valor ? styles.escolhaAtiva : ''}`}
                      onClick={() => setSituacao(opcao.valor)}
                    >
                      {opcao.rotulo}
                    </button>
                  ))}
                </div>
              </div>

              {erroForm && (
                <p className={styles.erro} role="alert">
                  {erroForm}
                </p>
              )}
              {sucessoForm && (
                <p className={styles.sucesso} role="status">
                  {sucessoForm}
                </p>
              )}

              <Button type="submit" variant="accent" className={styles.salvar} disabled={salvando}>
                {salvando ? 'Salvando…' : 'Salvar despesa'}
              </Button>
              <button type="button" className={styles.linkCategorias} onClick={() => setGerenciandoCategorias(true)}>
                Gerenciar categorias
              </button>
            </form>
          </section>
        </aside>
      </div>

      {editando && (
        <ModalEditarDespesa
          despesa={editando}
          categorias={categorias}
          onFechar={() => setEditando(null)}
          onSalva={handleDespesaEditada}
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
