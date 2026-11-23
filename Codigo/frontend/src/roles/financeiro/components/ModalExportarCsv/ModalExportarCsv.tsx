import { useState, type FormEvent } from 'react'
import { Button } from '../../../../components/ui/Button/Button'
import { rotuloDoPeriodo } from '../../../../components/formulario/CampoData/datas'
import type { Periodo } from '../../../../components/formulario/CampoPeriodo/CampoPeriodo'
import { Modal, ModalAlerta } from '../../../../components/modal/Modal/Modal'
import { ApiError } from '../../../../services/api'
import { exportarRelatorioFinanceiro, nomeDoCsv } from '../../../../services/financeiro/relatorioFinanceiroService'
import type { ConjuntoCsv, SeparadorCsv } from '../../../../types/financeiro/relatorioFinanceiro'
import { salvarArquivo } from '../../../../utils/arquivos'
import styles from './ModalExportarCsv.module.css'

const CONJUNTOS: { valor: ConjuntoCsv; rotulo: string; descricao: string }[] = [
  { valor: 'COBRANCAS', rotulo: 'Cobranças', descricao: 'Uma linha por cobrança que vence no período' },
  { valor: 'PAGAMENTOS', rotulo: 'Pagamentos', descricao: 'Uma linha por pagamento ou estorno, com a forma' },
  { valor: 'DESPESAS', rotulo: 'Despesas', descricao: 'Uma linha por despesa que vence no período' },
]

const SEPARADORES: { valor: SeparadorCsv; rotulo: string }[] = [
  { valor: 'PONTO_E_VIRGULA', rotulo: 'Ponto e vírgula (;)' },
  { valor: 'VIRGULA', rotulo: 'Vírgula (,)' },
]

type ModalExportarCsvProps = {
  /** O período do relatório na tela: o arquivo sai com ele. */
  periodo: Periodo
  onFechar: () => void
}

/** Exportar em CSV (IU19-A). Fica aberta depois de baixar, com o nome do arquivo gerado. */
export function ModalExportarCsv({ periodo, onFechar }: ModalExportarCsvProps) {
  const [conjuntos, setConjuntos] = useState<ConjuntoCsv[]>(['COBRANCAS', 'PAGAMENTOS'])
  const [separador, setSeparador] = useState<SeparadorCsv>('PONTO_E_VIRGULA')
  const [baixando, setBaixando] = useState(false)
  const [erro, setErro] = useState<string | null>(null)
  const [arquivoGerado, setArquivoGerado] = useState<string | null>(null)

  function alternarConjunto(conjunto: ConjuntoCsv) {
    setArquivoGerado(null)
    setErro(null)
    // Pela lista completa, para o pedido sair sempre na mesma ordem.
    const marcados = conjuntos.includes(conjunto) ? conjuntos.filter((c) => c !== conjunto) : [...conjuntos, conjunto]
    setConjuntos(CONJUNTOS.map((c) => c.valor).filter((c) => marcados.includes(c)))
  }

  function escolherSeparador(escolhido: SeparadorCsv) {
    setArquivoGerado(null)
    setErro(null)
    setSeparador(escolhido)
  }

  async function baixar(evento: FormEvent<HTMLFormElement>) {
    evento.preventDefault()
    if (conjuntos.length === 0 || baixando) return
    setBaixando(true)
    setErro(null)
    setArquivoGerado(null)
    try {
      const nome = nomeDoCsv(periodo)
      salvarArquivo(await exportarRelatorioFinanceiro(periodo, conjuntos, separador), nome)
      setArquivoGerado(nome)
    } catch (e) {
      setErro(e instanceof ApiError ? (e.detalhe ?? e.message) : 'Não foi possível gerar o arquivo.')
    } finally {
      setBaixando(false)
    }
  }

  return (
    <Modal
      titulo="Exportar em CSV"
      subtitulo={`O arquivo sai com o período selecionado: ${rotuloDoPeriodo(periodo)}.`}
      ocupado={baixando}
      onFechar={onFechar}
      onEnviar={baixar}
      rodape={
        <>
          <Button disabled={baixando} onClick={onFechar}>
            Cancelar
          </Button>
          <Button type="submit" variant="primary" icon="download" disabled={baixando || conjuntos.length === 0}>
            {baixando ? 'Gerando…' : 'Baixar arquivo'}
          </Button>
        </>
      }
    >
      <div className={styles.corpo}>
        <fieldset className={styles.grupo}>
          <legend className={styles.legenda}>O que entra no arquivo</legend>
          <div className={styles.conjuntos}>
            {CONJUNTOS.map((conjunto) => {
              const marcado = conjuntos.includes(conjunto.valor)
              return (
                <label key={conjunto.valor} className={`${styles.conjunto} ${marcado ? styles.conjuntoMarcado : ''}`}>
                  <input type="checkbox" checked={marcado} onChange={() => alternarConjunto(conjunto.valor)} />
                  <span className={styles.textoConjunto}>
                    <strong>{conjunto.rotulo}</strong>
                    <span>{conjunto.descricao}</span>
                  </span>
                </label>
              )
            })}
          </div>
          {conjuntos.length === 0 && <p className={styles.aviso}>Escolha ao menos um conjunto de dados.</p>}
        </fieldset>

        <div className={styles.grupo} role="group" aria-labelledby="separador-csv">
          <span id="separador-csv" className={styles.legenda}>
            Separador das colunas
          </span>
          <div className={styles.separadores}>
            {SEPARADORES.map((opcao) => (
              <button
                key={opcao.valor}
                type="button"
                aria-pressed={separador === opcao.valor}
                className={`${styles.separador} ${separador === opcao.valor ? styles.separadorAtivo : ''}`}
                onClick={() => escolherSeparador(opcao.valor)}
              >
                {opcao.rotulo}
              </button>
            ))}
          </div>
        </div>

        {arquivoGerado && (
          <p className={styles.gerado} role="status">
            Arquivo gerado: {arquivoGerado}
          </p>
        )}
        {erro && <ModalAlerta>{erro}</ModalAlerta>}
      </div>
    </Modal>
  )
}
