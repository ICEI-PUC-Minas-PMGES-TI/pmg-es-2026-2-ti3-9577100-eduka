import { useState, type FormEvent, type ReactNode } from 'react'
import { Button } from '../../../../../components/ui/Button/Button'
import { Campo } from '../../../../../components/formulario/Campo/Campo'
import { CampoData } from '../../../../../components/formulario/CampoData/CampoData'
import { Icon } from '../../../../../components/ui/Icon/Icon'
import { Select } from '../../../../../components/formulario/Select/Select'
import { ROTULOS_PLANO, type Escola } from '../../../../../types/escola/escola'
import type { FaturaPlataforma, LancarFaturaRequest } from '../../../../../types/escola/faturaPlataforma'
import { hojeIso } from '../../../../../utils/formatters'
import { competenciaDaReferencia, lerValor, MAX_DESCRICAO, nomeDoMes, traduzirErroLancar } from './regrasFatura'
import styles from './FormLancarFatura.module.css'

type Campos = { escolaId: string; referencia: string; valor: string; dataVencimento: string }
type Erros = Partial<Record<keyof Campos, string>>

const VAZIO: Campos = { escolaId: '', referencia: '', valor: '', dataVencimento: '' }

function sugerirReferencia(escola: Escola | undefined): string {
  const hoje = hojeIso()
  const plano = escola?.plano ? `Plano ${ROTULOS_PLANO[escola.plano]}` : 'Mensalidade Eduka'
  return `${plano} · ${nomeDoMes(hoje)}/${hoje.slice(0, 4)}`
}

/** O valor da fatura mais recente da escola; a lista já vem do vencimento mais recente para o mais antigo. */
function sugerirValor(faturas: FaturaPlataforma[], escolaId: string): string {
  const ultima = faturas.find((f) => f.escolaId === escolaId && f.status !== 'CANCELADA')
  return ultima ? ultima.valor.toFixed(2).replace('.', ',') : ''
}

type FormLancarFaturaProps = {
  escolas: Escola[]
  faturas: FaturaPlataforma[]
  /** Deve lançar ApiError em caso de falha. */
  onLancar: (escolaId: string, dados: LancarFaturaRequest) => Promise<void>
  /** Ações abaixo do botão de lançar. */
  children?: ReactNode
}

/** Formulário "Lançar fatura" da IU24. A competência sai do mês escrito na referência. */
export function FormLancarFatura({ escolas, faturas, onLancar, children }: FormLancarFaturaProps) {
  const [campos, setCampos] = useState<Campos>(VAZIO)
  const [erros, setErros] = useState<Erros>({})
  const [erroEnvio, setErroEnvio] = useState<string | null>(null)
  const [enviando, setEnviando] = useState(false)
  const ativas = escolas.filter((e) => e.ativo)

  function alterar(campo: keyof Campos, valor: string) {
    setCampos((c) => ({ ...c, [campo]: valor }))
    setErros((e) => ({ ...e, [campo]: undefined }))
  }

  function escolherEscola(escolaId: string) {
    setCampos((c) => ({
      ...c,
      escolaId,
      referencia: sugerirReferencia(escolas.find((e) => e.id === escolaId)),
      valor: sugerirValor(faturas, escolaId),
    }))
    setErros((e) => ({ ...e, escolaId: undefined, referencia: undefined, valor: undefined }))
  }

  function validar(): boolean {
    const novos: Erros = {}
    if (!campos.escolaId) novos.escolaId = 'Escolha a escola que vai pagar.'
    if (!campos.referencia.trim()) novos.referencia = 'Informe o plano e o mês.'
    else if (!competenciaDaReferencia(campos.referencia)) {
      novos.referencia = 'Inclua o mês e o ano, como em novembro/2026.'
    }
    if (!(lerValor(campos.valor) > 0)) novos.valor = 'Informe um valor maior que zero.'
    if (!campos.dataVencimento) novos.dataVencimento = 'Informe o vencimento.'
    else if (campos.dataVencimento < hojeIso()) novos.dataVencimento = 'Não pode ser anterior a hoje.'
    setErros(novos)
    return Object.keys(novos).length === 0
  }

  async function enviar(event: FormEvent) {
    event.preventDefault()
    setErroEnvio(null)
    if (!validar()) return
    setEnviando(true)
    try {
      await onLancar(campos.escolaId, {
        competencia: competenciaDaReferencia(campos.referencia) ?? '',
        descricao: campos.referencia.trim(),
        valor: Math.round(lerValor(campos.valor) * 100) / 100,
        dataVencimento: campos.dataVencimento,
      })
      setCampos(VAZIO)
    } catch (erro) {
      setErroEnvio(traduzirErroLancar(erro))
    } finally {
      setEnviando(false)
    }
  }

  return (
    <form className={styles.cartao} onSubmit={enviar} noValidate aria-labelledby="titulo-lancar-fatura">
      <h2 id="titulo-lancar-fatura" className={styles.titulo}>
        Lançar fatura
      </h2>
      <Campo label="Escola" erro={erros.escolaId} obrigatorio>
        {(props) => (
          <Select
            {...props}
            name="escolaId"
            placeholder={ativas.length === 0 ? 'Nenhuma escola ativa' : 'Escolha a escola'}
            opcoes={ativas.map((e) => ({ valor: e.id, rotulo: e.nome }))}
            valor={campos.escolaId}
            onChange={escolherEscola}
          />
        )}
      </Campo>
      <Campo label="Referência" erro={erros.referencia} obrigatorio>
        {(props) => (
          <input
            {...props}
            name="referencia"
            maxLength={MAX_DESCRICAO}
            placeholder="Plano Avançado · novembro/2026"
            value={campos.referencia}
            onChange={(e) => alterar('referencia', e.target.value)}
          />
        )}
      </Campo>
      <div className={styles.dupla}>
        <Campo label="Valor (R$)" erro={erros.valor} obrigatorio>
          {(props) => (
            <input
              {...props}
              name="valor"
              inputMode="decimal"
              placeholder="690,00"
              value={campos.valor}
              onChange={(e) => alterar('valor', e.target.value)}
            />
          )}
        </Campo>
        <Campo label="Vencimento" erro={erros.dataVencimento} obrigatorio>
          {(props) => (
            <CampoData
              {...props}
              name="dataVencimento"
              min={hojeIso()}
              valor={campos.dataVencimento}
              onChange={(data) => alterar('dataVencimento', data)}
            />
          )}
        </Campo>
      </div>
      <p className={styles.aviso}>
        <Icon name="info" size={16} />
        <span>O valor vem da última fatura da escola e pode ser ajustado. O dinheiro cai na conta do Eduka.</span>
      </p>
      {erroEnvio && (
        <p role="alert" className={styles.erro}>
          {erroEnvio}
        </p>
      )}
      <Button type="submit" variant="primary" disabled={enviando} className={styles.enviar}>
        {enviando ? 'Lançando…' : 'Lançar fatura'}
      </Button>
      {children}
    </form>
  )
}
