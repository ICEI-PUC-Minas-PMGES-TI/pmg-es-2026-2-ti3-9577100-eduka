import { useState } from 'react'
import { Campo } from '../../../../../components/formulario/Campo/Campo'
import { CampoData } from '../../../../../components/formulario/CampoData/CampoData'
import { Select } from '../../../../../components/formulario/Select/Select'
import { ModalCadastro } from '../../../../../components/modal/ModalCadastro/ModalCadastro'
import type { LancarFaturaRequest } from '../../../../../types/escola/faturaPlataforma'
import { hojeIso } from '../../../../../utils/formatters'
import { lerValor, MAX_DESCRICAO, traduzirErroLancar } from '../FormLancarFatura/regrasFatura'

/** Meses oferecidos na competência, antes e depois do atual. */
const MESES_EM_VOLTA = 12

/**
 * Opções da competência ("Novembro de 2026" -> "2026-11"), do mês atual
 * para trás e para a frente, na ordem do calendário. No lugar do
 * <input type="month">, cujo seletor é desenhado pelo navegador.
 */
function mesesEmVolta(mesAtual: string): { valor: string; rotulo: string }[] {
  const [ano, mes] = mesAtual.split('-').map(Number)
  return Array.from({ length: MESES_EM_VOLTA * 2 + 1 }, (_, i) => {
    const data = new Date(ano, mes - 1 - MESES_EM_VOLTA + i, 1)
    const rotulo = data.toLocaleDateString('pt-BR', { month: 'long', year: 'numeric' })
    return {
      valor: `${data.getFullYear()}-${String(data.getMonth() + 1).padStart(2, '0')}`,
      rotulo: rotulo.charAt(0).toUpperCase() + rotulo.slice(1),
    }
  })
}

type Campos = { competencia: string; descricao: string; valor: string; dataVencimento: string }
type Erros = Partial<Record<keyof Campos, string>>

type ModalLancarFaturaProps = {
  escolaNome: string
  onFechar: () => void
  /** Lança; deve lançar ApiError em caso de falha. */
  onLancar: (dados: LancarFaturaRequest) => Promise<void>
}

/** Lançar a fatura do mês de uma escola (R26): competência única, valor > 0 e vencimento a partir de hoje. */
export function ModalLancarFatura({ escolaNome, onFechar, onLancar }: ModalLancarFaturaProps) {
  const [campos, setCampos] = useState<Campos>({
    competencia: '',
    descricao: '',
    valor: '',
    dataVencimento: '',
  })
  const [erros, setErros] = useState<Erros>({})
  const [opcoesCompetencia] = useState(() => mesesEmVolta(hojeIso().slice(0, 7)))

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
      titulo={escolaNome ? `Lançar fatura · ${escolaNome}` : 'Lançar fatura'}
      rotuloSalvar="Lançar fatura"
      onFechar={onFechar}
      validar={validar}
      traduzirErro={traduzirErroLancar}
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
          <Select
            {...props}
            name="competencia"
            placeholder="Escolha o mês"
            opcoes={opcoesCompetencia}
            sugestao={hojeIso().slice(0, 7)}
            valor={campos.competencia}
            onChange={(competencia) => alterar('competencia', competencia)}
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
