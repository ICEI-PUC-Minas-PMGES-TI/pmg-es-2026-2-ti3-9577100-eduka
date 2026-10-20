import { useMemo, useRef, useState, type DragEvent, type FormEvent } from 'react'
import { Link, useNavigate } from 'react-router'
import { Icon } from '../../../../components/Icon/Icon'
import { paths } from '../../../../routes/paths'
import { criarCobranca } from '../../../../services/cobrancaService'
import { FinanceiroLayout } from '../../layouts/FinanceiroLayout'
import styles from './NovaCobranca.module.css'

type TipoCobranca = 'Mensalidade' | 'Matrícula' | 'Material' | 'Outro'

type AlunoPreset = {
  id: string
  nome: string
  turmaCurso: string
  responsavel: string
}

const ALUNOS_EXEMPLO: AlunoPreset[] = [
  {
    id: '1',
    nome: 'Lucas Andrade',
    turmaCurso: '2º ano A · Ensino Médio',
    responsavel: 'Cláudia Andrade',
  },
  {
    id: '2',
    nome: 'Helena Castro',
    turmaCurso: '2º ano B · Ensino Médio',
    responsavel: 'Eduardo Castro',
  },
  {
    id: '3',
    nome: 'Caio Ferreira',
    turmaCurso: 'Inglês B1 · Curso livre',
    responsavel: 'Marcos Ferreira',
  },
]

const TIPOS_COBRANCA: TipoCobranca[] = ['Mensalidade', 'Matrícula', 'Material', 'Outro']

export function NovaCobranca() {
  const navigate = useNavigate()
  const fileInputRef = useRef<HTMLInputElement>(null)

  const [buscaAluno, setBuscaAluno] = useState('')
  const [alunoSelecionadoId, setAlunoSelecionadoId] = useState('1')
  const [tipoCobranca, setTipoCobranca] = useState<TipoCobranca>('Mensalidade')
  const [valor, setValor] = useState('890,00')
  const [dataVencimento, setDataVencimento] = useState('2026-10-10')
  const [descricao, setDescricao] = useState('Mensalidade de outubro de 2026')
  const [arquivoBoleto, setArquivoBoleto] = useState<File | null>(null)
  const [arrastandoArquivo, setArrastandoArquivo] = useState(false)
  const [isSubmitting, setIsSubmitting] = useState(false)
  const [erro, setErro] = useState<string | null>(null)

  // Filtra ou inclui aluno personalizado caso a busca não coincida
  const alunosFiltrados = useMemo(() => {
    const termo = buscaAluno.trim().toLowerCase()
    if (!termo) return ALUNOS_EXEMPLO

    const filtrados = ALUNOS_EXEMPLO.filter(
      (a) =>
        a.nome.toLowerCase().includes(termo) ||
        a.turmaCurso.toLowerCase().includes(termo),
    )

    if (filtrados.length === 0) {
      return [
        {
          id: 'custom',
          nome: buscaAluno.trim(),
          turmaCurso: 'Aluno selecionado',
          responsavel: 'Responsável legal',
        },
      ]
    }

    return filtrados
  }, [buscaAluno])

  const alunoSelecionado = useMemo(() => {
    return (
      alunosFiltrados.find((a) => a.id === alunoSelecionadoId) ??
      ALUNOS_EXEMPLO.find((a) => a.id === alunoSelecionadoId) ??
      alunosFiltrados[0]
    )
  }, [alunosFiltrados, alunoSelecionadoId])

  function handleSelecionarTipo(tipo: TipoCobranca) {
    setTipoCobranca(tipo)
    // Atualiza automaticamente a descrição sugerida caso o usuário não tenha personalizado
    if (tipo === 'Mensalidade') {
      setDescricao('Mensalidade de outubro de 2026')
      setValor('890,00')
    } else if (tipo === 'Matrícula') {
      setDescricao('Taxa de matrícula 2026')
      setValor('350,00')
    } else if (tipo === 'Material') {
      setDescricao('Material didático 2026')
      setValor('450,00')
    } else {
      setDescricao('Outras taxas e serviços escolares')
    }
  }

  function handleFileChange(e: React.ChangeEvent<HTMLInputElement>) {
    const file = e.target.files?.[0]
    if (file) {
      setArquivoBoleto(file)
    }
  }

  function handleDragOver(e: DragEvent<HTMLDivElement>) {
    e.preventDefault()
    setArrastandoArquivo(true)
  }

  function handleDragLeave(e: DragEvent<HTMLDivElement>) {
    e.preventDefault()
    setArrastandoArquivo(false)
  }

  function handleDrop(e: DragEvent<HTMLDivElement>) {
    e.preventDefault()
    setArrastandoArquivo(false)
    const file = e.dataTransfer.files?.[0]
    if (file && (file.type === 'application/pdf' || file.name.endsWith('.pdf'))) {
      setArquivoBoleto(file)
    }
  }

  async function handleSubmit(e: FormEvent) {
    e.preventDefault()
    setErro(null)

    if (!alunoSelecionado) {
      setErro('Por favor, selecione um aluno.')
      return
    }

    const valorLimpo = valor.replace(/\./g, '').replace(',', '.')
    const valorNumerico = parseFloat(valorLimpo)

    if (isNaN(valorNumerico) || valorNumerico <= 0) {
      setErro('Informe um valor monetário válido.')
      return
    }

    if (!dataVencimento) {
      setErro('Informe a data de vencimento.')
      return
    }

    if (!descricao.trim()) {
      setErro('Informe a descrição da cobrança.')
      return
    }

    setIsSubmitting(true)
    try {
      await criarCobranca({
        alunoNome: alunoSelecionado.nome,
        responsavelNome: alunoSelecionado.responsavel,
        valor: valorNumerico,
        dataVencimento,
        descricao: descricao.trim(),
      })

      navigate(paths.financeiro.cobrancas)
    } catch {
      setErro('Ocorreu um erro ao gerar a cobrança. Tente novamente.')
    } finally {
      setIsSubmitting(false)
    }
  }

  return (
    <FinanceiroLayout
      titulo="Nova cobrança"
      subtitulo="Preencha os dados abaixo para gerar a cobrança do aluno."
      acao={
        <Link to={paths.financeiro.cobrancas} className={styles.botaoVoltar}>
          <span aria-hidden="true">←</span>
          Voltar
        </Link>
      }
    >
      <form onSubmit={handleSubmit} className={styles.container}>
        {/* Seção 1: Aluno */}
        <section className={styles.secao}>
          <h2 className={styles.secaoTitulo}>Aluno</h2>
          <div className={styles.buscaWrapper}>
            <Icon name="search" size={18} className={styles.buscaIcone} />
            <input
              type="text"
              className={styles.buscaInput}
              placeholder="Procurar pelo nome ou pela matrícula"
              value={buscaAluno}
              onChange={(e) => setBuscaAluno(e.target.value)}
            />
          </div>

          <div className={styles.alunosGrid}>
            {alunosFiltrados.map((aluno) => {
              const ativo = alunoSelecionado?.id === aluno.id
              return (
                <button
                  key={aluno.id}
                  type="button"
                  className={`${styles.alunoCard} ${ativo ? styles.alunoCardAtivo : ''}`}
                  onClick={() => setAlunoSelecionadoId(aluno.id)}
                >
                  <span className={styles.alunoNome}>{aluno.nome}</span>
                  <span className={styles.alunoDetalhe}>{aluno.turmaCurso}</span>
                </button>
              )
            })}
          </div>
        </section>

        {/* Seção 2: Tipo de cobrança */}
        <section className={styles.secao}>
          <h2 className={styles.secaoTitulo}>Tipo de cobrança</h2>
          <div className={styles.tiposGrid}>
            {TIPOS_COBRANCA.map((tipo) => {
              const ativo = tipoCobranca === tipo
              return (
                <button
                  key={tipo}
                  type="button"
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
            <label htmlFor="valorInput" className={styles.secaoTitulo}>
              Valor
            </label>
            <div className={styles.inputMoedaWrapper}>
              <span className={styles.cifrao}>R$</span>
              <input
                id="valorInput"
                type="text"
                className={styles.inputMoeda}
                value={valor}
                onChange={(e) => setValor(e.target.value.replace(/^R\$\s*/, ''))}
                placeholder="0,00"
                required
              />
            </div>
          </section>

          <section className={styles.secao}>
            <label htmlFor="vencimentoInput" className={styles.secaoTitulo}>
              Vencimento
            </label>
            <input
              id="vencimentoInput"
              type="date"
              className={styles.inputPadrao}
              value={dataVencimento}
              onChange={(e) => setDataVencimento(e.target.value)}
              required
            />
          </section>
        </div>

        {/* Seção 4: Descrição */}
        <section className={styles.secao}>
          <label htmlFor="descricaoInput" className={styles.secaoTitulo}>
            Descrição
          </label>
          <input
            id="descricaoInput"
            type="text"
            className={styles.inputPadrao}
            value={descricao}
            onChange={(e) => setDescricao(e.target.value)}
            placeholder="Ex: Mensalidade de outubro de 2026"
            required
          />
        </section>

        {/* Seção 5: Boleto (pode anexar depois) */}
        <section className={styles.secao}>
          <span className={styles.secaoTitulo}>Boleto (pode anexar depois)</span>
          <input
            ref={fileInputRef}
            type="file"
            accept="application/pdf"
            style={{ display: 'none' }}
            onChange={handleFileChange}
          />
          <div
            className={`${styles.dropzone} ${arrastandoArquivo ? styles.dropzoneArrastando : ''}`}
            onClick={() => fileInputRef.current?.click()}
            onDragOver={handleDragOver}
            onDragLeave={handleDragLeave}
            onDrop={handleDrop}
            role="button"
            tabIndex={0}
            onKeyDown={(e) => {
              if (e.key === 'Enter' || e.key === ' ') {
                fileInputRef.current?.click()
              }
            }}
          >
            {arquivoBoleto ? (
              <div className={styles.dropzoneArquivo}>
                <Icon name="fileText" size={20} />
                <span className={styles.nomeArquivo}>{arquivoBoleto.name}</span>
                <button
                  type="button"
                  className={styles.botaoRemoverArquivo}
                  title="Remover anexo"
                  onClick={(e) => {
                    e.stopPropagation()
                    setArquivoBoleto(null)
                    if (fileInputRef.current) fileInputRef.current.value = ''
                  }}
                >
                  ×
                </button>
              </div>
            ) : (
              <div className={styles.dropzoneConteudo}>
                <Icon name="download" size={20} />
                <span>Escolher o arquivo do boleto (PDF)</span>
              </div>
            )}
          </div>
          <p className={styles.helperBoleto}>
            O aluno baixa esse arquivo na tela Pagamentos.
          </p>
        </section>

        {erro && <p className={styles.mensagemErro}>{erro}</p>}

        {/* Rodapé: Ações */}
        <div className={styles.acoesFormulario}>
          <button
            type="button"
            className={styles.botaoCancelar}
            onClick={() => navigate(paths.financeiro.cobrancas)}
            disabled={isSubmitting}
          >
            Cancelar
          </button>
          <button
            type="submit"
            className={styles.botaoCriar}
            disabled={isSubmitting}
          >
            {isSubmitting ? 'Criando…' : 'Criar cobrança'}
          </button>
        </div>
      </form>
    </FinanceiroLayout>
  )
}

