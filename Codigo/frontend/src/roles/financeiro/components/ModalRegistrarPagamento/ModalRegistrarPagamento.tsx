import { useState } from 'react'
import { Campo } from '../../../../components/formulario/Campo/Campo'
import { CampoData } from '../../../../components/formulario/CampoData/CampoData'
import { Select } from '../../../../components/formulario/Select/Select'
import { ModalCadastro } from '../../../../components/modal/ModalCadastro/ModalCadastro'
import { ApiError } from '../../../../services/api'
import { registrarPagamentoManual } from '../../../../services/financeiro/cobrancaService'
import type { Cobranca } from '../../../../types/financeiro/cobranca'
import {
  ROTULOS_FORMA_PAGAMENTO_MANUAL,
  type FormaPagamentoManual,
} from '../../../../types/financeiro/pagamentoAluno'
import { formatarMoeda, hojeIso } from '../../../../utils/formatters'
import { textoParaValor, valorParaTexto } from '../../valorEmReais'
import styles from './ModalRegistrarPagamento.module.css'

type Campos = { valorPago: string; dataPagamento: string; forma: FormaPagamentoManual | '' }
type Erros = Partial<Record<keyof Campos, string>>

const OPCOES_FORMA = (Object.keys(ROTULOS_FORMA_PAGAMENTO_MANUAL) as FormaPagamentoManual[]).map((forma) => ({
  valor: forma,
  rotulo: ROTULOS_FORMA_PAGAMENTO_MANUAL[forma],
}))

/** Recusas da baixa, pelo `codigo` do backend. */
function mensagemErro(erro: unknown): string {
  if (erro instanceof ApiError) {
    if (erro.codigo === 'COBRANCA_JA_PAGA') {
      return 'O Asaas já recebeu o pagamento online desta cobrança: ela fica paga sem a baixa manual.'
    }
    if (erro.codigo === 'PAGAMENTO_EM_PROCESSAMENTO') {
      return 'O aluno está gerando o PIX ou o boleto agora. Tente de novo em instantes.'
    }
    if (erro.codigo === 'PROVEDOR_INDISPONIVEL' || erro.codigo === 'PROVEDOR_NAO_CONFIGURADO') {
      return 'O Asaas não respondeu e a cobrança continua em aberto. Tente de novo em instantes.'
    }
    if (erro.status === 400) {
      return 'A cobrança não está mais em aberto, ou os dados foram recusados. Feche e confira a cobrança.'
    }
    return erro.detalhe ?? erro.message
  }
  return 'Erro inesperado ao registrar o pagamento.'
}

/** O backend mudou a cobrança (paga no Asaas ou já encerrada): a lista precisa ser lida de novo. */
function mudouNoServidor(erro: unknown): boolean {
  return erro instanceof ApiError && (erro.codigo === 'COBRANCA_JA_PAGA' || erro.status === 400)
}

type ModalRegistrarPagamentoProps = {
  cobranca: Cobranca
  onFechar: () => void
  onRegistrado: (cobranca: Cobranca) => void
  /** A recusa mostra que a cobrança mudou no servidor; o modal continua aberto com o erro. */
  onDesatualizada: () => void
}

/**
 * Baixa manual (UC12): o financeiro registra o pagamento que a escola recebeu
 * fora do Eduka (dinheiro, transferência etc.), com o valor que entrou, o dia
 * e a forma.
 */
export function ModalRegistrarPagamento({
  cobranca,
  onFechar,
  onRegistrado,
  onDesatualizada,
}: ModalRegistrarPagamentoProps) {
  const hoje = hojeIso()
  const [campos, setCampos] = useState<Campos>({
    valorPago: valorParaTexto(cobranca.valor),
    dataPagamento: hoje,
    forma: '',
  })
  const [erros, setErros] = useState<Erros>({})

  function alterar<K extends keyof Campos>(campo: K, valor: Campos[K]) {
    setCampos((atuais) => ({ ...atuais, [campo]: valor }))
    if (erros[campo]) setErros((atuais) => ({ ...atuais, [campo]: undefined }))
  }

  function validar(): boolean {
    const novos: Erros = {}
    const valor = textoParaValor(campos.valorPago)
    if (!(valor > 0)) novos.valorPago = 'Informe o valor recebido, maior que zero (ex.: 890,00).'
    if (!campos.dataPagamento) novos.dataPagamento = 'Informe o dia em que a escola recebeu.'
    else if (campos.dataPagamento > hoje) novos.dataPagamento = 'O dia do pagamento não pode ser depois de hoje.'
    if (!campos.forma) novos.forma = 'Escolha como a escola recebeu.'
    setErros(novos)
    return Object.keys(novos).length === 0
  }

  async function enviar() {
    if (!campos.forma) return
    try {
      onRegistrado(
        await registrarPagamentoManual(cobranca.id, {
          valorPago: textoParaValor(campos.valorPago),
          dataPagamento: campos.dataPagamento,
          forma: campos.forma,
        }),
      )
    } catch (e) {
      if (mudouNoServidor(e)) onDesatualizada()
      throw e
    }
  }

  return (
    <ModalCadastro
      titulo="Registrar pagamento"
      rotuloSalvar="Registrar pagamento"
      onFechar={onFechar}
      validar={validar}
      onEnviar={enviar}
      traduzirErro={mensagemErro}
    >
      <p className={styles.resumo}>
        <strong>{cobranca.descricao}</strong> de {cobranca.alunoNome ?? 'aluno sem nome'} ·{' '}
        {formatarMoeda(cobranca.valor)}. Use quando a escola recebeu fora do Eduka. A cobrança fica paga e quem paga
        recebe o aviso por e-mail.
      </p>
      {cobranca.pagamentoIniciadoEm && (
        <p className={styles.aviso} role="note">
          O aluno já gerou o PIX ou o boleto. Ao registrar, a cobrança sai do Asaas para não ser paga duas vezes.
        </p>
      )}
      <Campo
        label="Valor recebido (R$)"
        erro={erros.valorPago}
        dica="Pode ser diferente do valor da cobrança (desconto ou juros)."
        obrigatorio
      >
        {(props) => (
          <input
            {...props}
            name="valorPago"
            inputMode="decimal"
            value={campos.valorPago}
            onChange={(e) => alterar('valorPago', e.target.value)}
          />
        )}
      </Campo>
      <Campo label="Dia do pagamento" erro={erros.dataPagamento} obrigatorio>
        {(props) => (
          <CampoData
            {...props}
            name="dataPagamento"
            valor={campos.dataPagamento}
            max={hoje}
            onChange={(data) => alterar('dataPagamento', data)}
          />
        )}
      </Campo>
      <Campo label="Forma de pagamento" erro={erros.forma} obrigatorio largo>
        {(props) => (
          <Select
            {...props}
            name="forma"
            placeholder="Como a escola recebeu?"
            opcoes={OPCOES_FORMA}
            valor={campos.forma}
            onChange={(forma) => alterar('forma', forma)}
          />
        )}
      </Campo>
    </ModalCadastro>
  )
}
