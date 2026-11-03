import { useEffect, useMemo, useState, type FormEvent } from 'react'
import { Link, useNavigate } from 'react-router'
import { Button } from '../../../../components/Button/Button'
import { Icon } from '../../../../components/Icon/Icon'
import { useRotasFinanceiras } from '../../../../routes/areas'
import { type EstadoCobrancaCriada } from '../../../../routes/paths'
import { ApiError } from '../../../../services/api'
import { criarCobranca } from '../../../../services/cobrancaService'
import { listarAlunos } from '../../../../services/matriculaService'
import type { AlunoResumo } from '../../../../types/aluno'
import { hojeIso } from '../../../../utils/formatters'
import { FinanceiroLayout } from '../../layouts/FinanceiroLayout'
import styles from './NovaCobranca.module.css'

type TipoCobranca = 'Mensalidade' | 'Matrícula' | 'Material' | 'Outro'

const TIPOS_COBRANCA: TipoCobranca[] = ['Mensalidade', 'Matrícula', 'Material', 'Outro']

/** Cartões de aluno exibidos de uma vez; os demais aparecem pela busca. */
const LIMITE_ALUNOS = 12

/** Campo que a validação recusou, com o id do controle que recebe o foco. */
type CampoCobranca = 'aluno' | 'valor' | 'vencimento' | 'descricao'

const IDS: Record<CampoCobranca, string> = {
  aluno: 'buscaAlunoInput',
  valor: 'valorInput',
  vencimento: 'vencimentoInput',
  descricao: 'descricaoInput',
}

const ERRO_ID = 'erroNovaCobranca'

/** Descrição sugerida para o tipo, com o mês e o ano de hoje. */
function descricaoSugerida(tipo: TipoCobranca): string {
  const [ano, mes] = hojeIso().split('-').map(Number)
  switch (tipo) {
    case 'Mensalidade': {
      const nomeMes = new Date(ano, mes - 1, 1).toLocaleDateString('pt-BR', { month: 'long' })
      return `Mensalidade de ${nomeMes} de ${ano}`
    }
    case 'Matrícula':
      return `Taxa de matrícula ${ano}`
    case 'Material':
      return `Material didático ${ano}`
    case 'Outro':
      return 'Outras taxas e serviços escolares'
  }
}

export function NovaCobranca() {
  const navigate = useNavigate()
  const rotas = useRotasFinanceiras()

  const [alunos, setAlunos] = useState<AlunoResumo[]>([])
  const [carregandoAlunos, setCarregandoAlunos] = useState(true)
  const [erroAlunos, setErroAlunos] = useState<string | null>(null)
  // Cada incremento busca a lista de alunos de novo ("Tentar novamente").
  const [tentativaAlunos, setTentativaAlunos] = useState(0)
  const [buscaAluno, setBuscaAluno] = useState('')
  const [alunoSelecionadoId, setAlunoSelecionadoId] = useState<string | null>(null)
  const [tipoCobranca, setTipoCobranca] = useState<TipoCobranca>('Mensalidade')
  const [valor, setValor] = useState('')
  const [dataVencimento, setDataVencimento] = useState('')
  const [descricao, setDescricao] = useState(() => descricaoSugerida('Mensalidade'))
  // Depois que o usuário escreve a própria descrição, trocar o tipo não a apaga.
  const [descricaoPersonalizada, setDescricaoPersonalizada] = useState(false)
  const [isSubmitting, setIsSubmitting] = useState(false)
  const [erro, setErro] = useState<string | null>(null)
  const [campoInvalido, setCampoInvalido] = useState<CampoCobranca | null>(null)

  useEffect(() => {
    const controller = new AbortController()
    setCarregandoAlunos(true)
    setErroAlunos(null)
    listarAlunos(controller.signal)
      .then(setAlunos)
      .catch(() => {
        if (!controller.signal.aborted) setErroAlunos('Não foi possível carregar os alunos da escola.')
      })
      .finally(() => {
        if (!controller.signal.aborted) setCarregandoAlunos(false)
      })
    return () => controller.abort()
  }, [tentativaAlunos])

  const alunosFiltrados = useMemo(() => {
    const termo = buscaAluno.trim().toLowerCase()
    if (!termo) return alunos
    return alunos.filter(
      (a) => a.nome.toLowerCase().includes(termo) || a.matricula.toLowerCase().includes(termo),
    )
  }, [alunos, buscaAluno])
  const alunosVisiveis = alunosFiltrados.slice(0, LIMITE_ALUNOS)

  const alunoSelecionado = useMemo(
    () => alunos.find((a) => a.id === alunoSelecionadoId) ?? null,
    [alunos, alunoSelecionadoId],
  )

  /** Mostra o erro, marca o campo e leva o foco até ele. */
  function recusar(campo: CampoCobranca, mensagem: string) {
    setErro(mensagem)
    setCampoInvalido(campo)
    document.getElementById(IDS[campo])?.focus()
  }

  /** Ao corrigir o campo recusado, a mensagem sai. */
  function corrigiu(campo: CampoCobranca) {
    if (campoInvalido !== campo) return
    setErro(null)
    setCampoInvalido(null)
  }

  /** Atributos de acessibilidade do controle, conforme a validação. */
  function invalidez(campo: CampoCobranca) {
    const invalido = campoInvalido === campo
    return { 'aria-invalid': invalido, 'aria-describedby': invalido ? ERRO_ID : undefined }
  }

  function handleSelecionarAluno(id: string) {
    setAlunoSelecionadoId(id)
    corrigiu('aluno')
  }

  function handleSelecionarTipo(tipo: TipoCobranca) {
    setTipoCobranca(tipo)
    if (!descricaoPersonalizada) setDescricao(descricaoSugerida(tipo))
  }

  async function handleSubmit(e: FormEvent) {
    e.preventDefault()
    setErro(null)
    setCampoInvalido(null)

    if (!alunoSelecionado) {
      recusar('aluno', 'Selecione o aluno que vai receber a cobrança.')
      return
    }

    const valorLimpo = valor.replace(/\./g, '').replace(',', '.')
    const valorNumerico = parseFloat(valorLimpo)

    if (isNaN(valorNumerico) || valorNumerico <= 0) {
      recusar('valor', 'Informe um valor maior que zero (ex.: 890,00).')
      return
    }

    if (!dataVencimento) {
      recusar('vencimento', 'Informe a data de vencimento.')
      return
    }

    if (!descricao.trim()) {
      recusar('descricao', 'Informe a descrição da cobrança.')
      return
    }

    setIsSubmitting(true)
    try {
      await criarCobranca({
        alunoId: alunoSelecionado.id,
        valor: valorNumerico,
        dataVencimento,
        descricao: descricao.trim(),
      })

      const estado: EstadoCobrancaCriada = {
        cobrancaCriada: `Cobrança "${descricao.trim()}" criada para ${alunoSelecionado.nome}.`,
      }
      navigate(rotas.cobrancas, { state: estado })
    } catch (e) {
      setErro(
        e instanceof ApiError
          ? (e.detalhe ?? e.message)
          : 'Ocorreu um erro ao gerar a cobrança. Tente novamente.',
      )
      setIsSubmitting(false)
    }
  }

  return (
    <FinanceiroLayout
      titulo="Nova cobrança"
      subtitulo="Preencha os dados abaixo para gerar a cobrança do aluno."
      acao={
        <Link to={rotas.cobrancas} className={styles.botaoVoltar}>
          <span aria-hidden="true">←</span>
          Voltar
        </Link>
      }
    >
      <form onSubmit={handleSubmit} className={styles.container} noValidate>
        {/* Seção 1: Aluno */}
        <section className={styles.secao}>
          <h2 id="tituloAluno" className={styles.secaoTitulo}>
            Aluno
          </h2>
          <div className={styles.buscaWrapper}>
            <Icon name="search" size={18} className={styles.buscaIcone} />
            <input
              id={IDS.aluno}
              type="text"
              className={styles.buscaInput}
              placeholder="Procurar pelo nome ou pela matrícula"
              aria-label="Procurar aluno pelo nome ou pela matrícula"
              value={buscaAluno}
              onChange={(e) => setBuscaAluno(e.target.value)}
              {...invalidez('aluno')}
            />
          </div>

          {alunoSelecionado && (
            <p className={styles.helperBoleto} aria-live="polite">
              Aluno selecionado: <strong>{alunoSelecionado.nome}</strong> · Matrícula {alunoSelecionado.matricula}
            </p>
          )}

          {carregandoAlunos && (
            <p className={styles.helperBoleto} role="status">
              Carregando alunos…
            </p>
          )}

          {!carregandoAlunos && erroAlunos && (
            <div className={styles.avisoAlunos} role="alert">
              <span className={styles.mensagemErro}>{erroAlunos}</span>
              <Button size="sm" onClick={() => setTentativaAlunos((n) => n + 1)}>
                Tentar novamente
              </Button>
            </div>
          )}

          {!carregandoAlunos && !erroAlunos && (
            <>
              <div className={styles.alunosGrid} role="group" aria-labelledby="tituloAluno">
                {alunosVisiveis.map((aluno) => {
                  const ativo = alunoSelecionado?.id === aluno.id
                  return (
                    <button
                      key={aluno.id}
                      type="button"
                      aria-pressed={ativo}
                      className={`${styles.alunoCard} ${ativo ? styles.alunoCardAtivo : ''}`}
                      onClick={() => handleSelecionarAluno(aluno.id)}
                    >
                      <span className={styles.alunoNome}>{aluno.nome}</span>
                      <span className={styles.alunoDetalhe}>Matrícula {aluno.matricula}</span>
                    </button>
                  )
                })}
              </div>
              {alunos.length === 0 && <p className={styles.helperBoleto}>Nenhum aluno cadastrado na escola.</p>}
              {alunos.length > 0 && alunosFiltrados.length === 0 && (
                <p className={styles.helperBoleto}>Nenhum aluno encontrado para a busca.</p>
              )}
              {alunosFiltrados.length > LIMITE_ALUNOS && (
                <p className={styles.helperBoleto}>
                  Mostrando {LIMITE_ALUNOS} de {alunosFiltrados.length} alunos. Procure pelo nome ou pela
                  matrícula para encontrar os demais.
                </p>
              )}
            </>
          )}
        </section>

        {/* Seção 2: Tipo de cobrança */}
        <section className={styles.secao}>
          <h2 id="tituloTipo" className={styles.secaoTitulo}>
            Tipo de cobrança
          </h2>
          <div className={styles.tiposGrid} role="group" aria-labelledby="tituloTipo">
            {TIPOS_COBRANCA.map((tipo) => {
              const ativo = tipoCobranca === tipo
              return (
                <button
                  key={tipo}
                  type="button"
                  aria-pressed={ativo}
                  className={`${styles.tipoBotao} ${ativo ? styles.tipoBotaoAtivo : ''}`}
                  onClick={() => handleSelecionarTipo(tipo)}
                >
                  {tipo}
                </button>
              )
            })}
          </div>
        </section>

        {/* Seção 3: Valor e Vencimento */}
        <div className={styles.linhaDupla}>
          <section className={styles.secao}>
            <label htmlFor={IDS.valor} className={styles.secaoTitulo}>
              Valor
            </label>
            <div className={styles.inputMoedaWrapper}>
              <span className={styles.cifrao}>R$</span>
              <input
                id={IDS.valor}
                type="text"
                inputMode="decimal"
                className={styles.inputMoeda}
                value={valor}
                onChange={(e) => {
                  setValor(e.target.value.replace(/^R\$\s*/, ''))
                  corrigiu('valor')
                }}
                placeholder="0,00"
                required
                {...invalidez('valor')}
              />
            </div>
          </section>

          <section className={styles.secao}>
            <label htmlFor={IDS.vencimento} className={styles.secaoTitulo}>
              Vencimento
            </label>
            <input
              id={IDS.vencimento}
              type="date"
              className={styles.inputPadrao}
              value={dataVencimento}
              onChange={(e) => {
                setDataVencimento(e.target.value)
                corrigiu('vencimento')
              }}
              required
              {...invalidez('vencimento')}
            />
          </section>
        </div>

        {/* Seção 4: Descrição */}
        <section className={styles.secao}>
          <label htmlFor={IDS.descricao} className={styles.secaoTitulo}>
            Descrição
          </label>
          <input
            id={IDS.descricao}
            type="text"
            className={styles.inputPadrao}
            value={descricao}
            onChange={(e) => {
              setDescricao(e.target.value)
              setDescricaoPersonalizada(e.target.value.trim() !== '')
              corrigiu('descricao')
            }}
            placeholder="Ex: Mensalidade de outubro de 2026"
            required
            {...invalidez('descricao')}
          />
        </section>

        {/* Seção 5: Boleto. O envio do PDF ainda não existe no backend. */}
        <section className={styles.secao}>
          <span className={styles.secaoTitulo}>Boleto</span>
          <div className={`${styles.dropzone} ${styles.dropzoneEmBreve}`}>
            <div className={styles.dropzoneConteudo}>
              <Icon name="download" size={20} />
              <span>Anexar o PDF do boleto: em breve</span>
            </div>
          </div>
          <p className={styles.helperBoleto}>
            Por enquanto a cobrança é criada sem arquivo. O aluno paga com PIX ou boleto gerado na tela Pagamentos.
          </p>
        </section>

        {erro && (
          <p id={ERRO_ID} className={styles.mensagemErro} role="alert">
            {erro}
          </p>
        )}

        {/* Rodapé: Ações */}
        <div className={styles.acoesFormulario}>
          <button
            type="button"
            className={styles.botaoCancelar}
            onClick={() => navigate(rotas.cobrancas)}
            disabled={isSubmitting}
          >
            Cancelar
          </button>
          <button type="submit" className={styles.botaoCriar} disabled={isSubmitting}>
            {isSubmitting ? 'Criando…' : 'Criar cobrança'}
          </button>
        </div>
      </form>
    </FinanceiroLayout>
  )
}
