import { useState } from 'react'
import { Campo } from '../../../../components/Campo/Campo'
import { CampoData } from '../../../../components/CampoData/CampoData'
import { ModalCadastro } from '../../../../components/ModalCadastro/ModalCadastro'
import { ApiError } from '../../../../services/api'
import { atualizarCobranca } from '../../../../services/cobrancaService'
import type { Cobranca } from '../../../../types/cobranca'

// Limite da coluna DESCRICAO (V20), o mesmo da Nova cobrança.
const MAX_DESCRICAO = 255

type Campos = { descricao: string; valor: string; dataVencimento: string }
type Erros = Partial<Record<keyof Campos, string>>

/** 890.5 -> "890,50", como o financeiro digita. */
function valorParaTexto(valor: number): string {
  return valor.toFixed(2).replace('.', ',')
}

/** "1.890,50" -> 1890.5; NaN se não for número. */
function textoParaValor(texto: string): number {
  return parseFloat(texto.replace(/^R\$\s*/, '').replace(/\./g, '').replace(',', '.'))
}

function mensagemErro(erro: unknown): string {
  if (erro instanceof ApiError) {
    if (erro.codigo === 'COBRANCA_NAO_EDITAVEL') {
      return 'O aluno já clicou em Pagar: a cobrança não pode mais ser editada. Cancele e lance outra.'
    }
    if (erro.codigo === 'ALTERADO_AO_MESMO_TEMPO') {
      return 'A cobrança mudou enquanto você editava. Feche e abra de novo para ver a versão atual.'
    }
    return erro.detalhe ?? erro.message
  }
  return 'Erro inesperado ao salvar a cobrança.'
}

type ModalEditarCobrancaProps = {
  cobranca: Cobranca
  onFechar: () => void
  onSalva: (cobranca: Cobranca) => void
}

/** Editar (IU20): descrição, valor e vencimento, só enquanto o aluno não clicou em Pagar. */
export function ModalEditarCobranca({ cobranca, onFechar, onSalva }: ModalEditarCobrancaProps) {
  const [campos, setCampos] = useState<Campos>({
    descricao: cobranca.descricao,
    valor: valorParaTexto(cobranca.valor),
    dataVencimento: cobranca.dataVencimento,
  })
  const [erros, setErros] = useState<Erros>({})

  function alterar<K extends keyof Campos>(campo: K, valor: Campos[K]) {
    setCampos((atuais) => ({ ...atuais, [campo]: valor }))
    if (erros[campo]) setErros((atuais) => ({ ...atuais, [campo]: undefined }))
  }

  function validar(): boolean {
    const novos: Erros = {}
    if (!campos.descricao.trim()) novos.descricao = 'Informe a descrição que o aluno vê.'
    const valor = textoParaValor(campos.valor)
    if (!(valor > 0)) novos.valor = 'Informe um valor maior que zero (ex.: 890,00).'
    if (!campos.dataVencimento) novos.dataVencimento = 'Informe o vencimento.'
    setErros(novos)
    return Object.keys(novos).length === 0
  }

  async function enviar() {
    onSalva(
      await atualizarCobranca(cobranca.id, {
        // O PUT substitui tudo: matrícula e responsável voltam como estavam.
        matriculaId: cobranca.matriculaId,
        responsavelId: cobranca.responsavelId,
        descricao: campos.descricao.trim(),
        valor: textoParaValor(campos.valor),
        dataVencimento: campos.dataVencimento,
      }),
    )
  }

  return (
    <ModalCadastro
      titulo="Editar cobrança"
      rotuloSalvar="Salvar alterações"
      onFechar={onFechar}
      validar={validar}
      onEnviar={enviar}
      traduzirErro={mensagemErro}
    >
      <Campo label="Descrição" erro={erros.descricao} obrigatorio largo>
        {(props) => (
          <input
            {...props}
            name="descricao"
            maxLength={MAX_DESCRICAO}
            value={campos.descricao}
            onChange={(e) => alterar('descricao', e.target.value)}
          />
        )}
      </Campo>
      <Campo label="Valor (R$)" erro={erros.valor} obrigatorio>
        {(props) => (
          <input
            {...props}
            name="valor"
            inputMode="decimal"
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
            valor={campos.dataVencimento}
            onChange={(data) => alterar('dataVencimento', data)}
          />
        )}
      </Campo>
    </ModalCadastro>
  )
}
