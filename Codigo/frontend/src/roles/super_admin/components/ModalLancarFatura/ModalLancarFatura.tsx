import { useState } from 'react'
import { Campo } from '../../../../components/Campo/Campo'
import { CampoData } from '../../../../components/CampoData/CampoData'
import { ModalCadastro } from '../../../../components/ModalCadastro/ModalCadastro'
import { ApiError } from '../../../../services/api'
import type { LancarFaturaRequest } from '../../../../types/faturaPlataforma'
import { hojeIso } from '../../../../utils/formatters'

const MAX_DESCRICAO = 255

type Campos = { competencia: string; descricao: string; valor: string; dataVencimento: string }
type Erros = Partial<Record<keyof Campos, string>>

/** "1.234,56" ou "1234.56" -> 1234.56; NaN se não for número. */
function lerValor(texto: string): number {
  const limpo = texto.trim().replace(/\s/g, '')
  const normalizado = limpo.includes(',') ? limpo.replace(/\./g, '').replace(',', '.') : limpo
  return normalizado === '' ? Number.NaN : Number(normalizado)
}

function traduzirErro(erro: unknown): string {
  if (erro instanceof ApiError && erro.codigo === 'FATURA_DUPLICADA') {
    return 'Esta escola já tem uma fatura nesta competência. Cancele a anterior para lançar outra.'
  }
  if (erro instanceof ApiError) return erro.detalhe ?? erro.message
  return 'Erro inesperado ao lançar a fatura.'
}

type ModalLancarFaturaProps = {
  escolaNome: string
  onFechar: () => void
  /** Lança; deve lançar ApiError em caso de falha. */
  onLancar: (dados: LancarFaturaRequest) => Promise<void>
}

/** Lançar a fatura do mês de uma escola (R26): competência única, valor > 0 e vencimento a partir de hoje. */
export function ModalLancarFatura({ escolaNome, onFechar, onLancar }: ModalLancarFaturaProps) {
  const [campos, setCampos] = useState<Campos>({ competencia: '', descricao: '', valor: '', dataVencimento: '' })
  const [erros, setErros] = useState<Erros>({})

  function alterar(campo: keyof Campos, valor: string) {
    setCampos((c) => ({ ...c, [campo]: valor }))
    setErros((e) => ({ ...e, [campo]: undefined }))
  }

  function validar(): boolean {
    const novos: Erros = {}
    if (!/^\d{4}-\d{2}$/.test(campos.competencia)) novos.competencia = 'Escolha o mês de referência.'
    if (!campos.descricao.trim()) novos.descricao = 'Informe a descrição.'
    if (!(lerValor(campos.valor) > 0)) novos.valor = 'Informe um valor maior que zero.'
    if (!campos.dataVencimento) novos.dataVencimento = 'Informe o vencimento.'
    else if (campos.dataVencimento < hojeIso()) novos.dataVencimento = 'O vencimento não pode ser anterior a hoje.'
    setErros(novos)
    return Object.keys(novos).length === 0
  }

  return (
    <ModalCadastro
      titulo={`Lançar fatura · ${escolaNome}`}
      rotuloSalvar="Lançar fatura"
      onFechar={onFechar}
      validar={validar}
      traduzirErro={traduzirErro}
      onEnviar={() =>
        onLancar({
          competencia: campos.competencia,
          descricao: campos.descricao.trim(),
          valor: Math.round(lerValor(campos.valor) * 100) / 100,
          dataVencimento: campos.dataVencimento,
        })
      }
    >
      <Campo label="Competência" erro={erros.competencia} obrigatorio>
        {(props) => (
          <input
            {...props}
            type="month"
            name="competencia"
            value={campos.competencia}
            onChange={(e) => alterar('competencia', e.target.value)}
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
      <Campo label="Descrição" erro={erros.descricao} obrigatorio largo>
        {(props) => (
          <input
            {...props}
            name="descricao"
            maxLength={MAX_DESCRICAO}
            placeholder="Ex.: Mensalidade Edúka de novembro"
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
            placeholder="Ex.: 299,90"
            value={campos.valor}
            onChange={(e) => alterar('valor', e.target.value)}
          />
        )}
      </Campo>
    </ModalCadastro>
  )
}
