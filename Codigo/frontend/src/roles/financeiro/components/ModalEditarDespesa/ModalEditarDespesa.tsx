import { useState } from 'react'
import { Campo } from '../../../../components/formulario/Campo/Campo'
import { CampoData } from '../../../../components/formulario/CampoData/CampoData'
import { ModalCadastro } from '../../../../components/modal/ModalCadastro/ModalCadastro'
import { Select } from '../../../../components/formulario/Select/Select'
import { ApiError } from '../../../../services/api'
import { atualizarDespesa } from '../../../../services/financeiro/despesaService'
import type { CategoriaDespesa, Despesa } from '../../../../types/financeiro/despesa'

// Limites das colunas DESCRICAO (V31) e RESPONSAVEL (V56).
const MAX_DESCRICAO = 255
const MAX_RESPONSAVEL = 120

type Campos = { descricao: string; responsavel: string; categoriaId: string; valor: string; dataVencimento: string }
type Erros = Partial<Record<keyof Campos, string>>

/** 890.5 -> "890,50", como o financeiro digita. */
function valorParaTexto(valor: number): string {
  return valor.toFixed(2).replace('.', ',')
}

/** "R$ 1.890,50" -> 1890.5; NaN se não for número. */
function textoParaValor(texto: string): number {
  return parseFloat(texto.replace(/^R\$\s*/i, '').replace(/\./g, '').replace(',', '.').trim())
}

function mensagemErro(erro: unknown): string {
  if (erro instanceof ApiError) {
    if (erro.status === 400 && /CANCELADA/.test(erro.detalhe ?? '')) {
      return 'A despesa foi cancelada e não pode mais ser editada.'
    }
    if (erro.status === 404) return 'A despesa ou a categoria não existe mais. Recarregue a página e tente de novo.'
    return erro.detalhe ?? erro.message
  }
  return 'Erro inesperado ao salvar a despesa.'
}

type ModalEditarDespesaProps = {
  despesa: Despesa
  categorias: CategoriaDespesa[]
  onFechar: () => void
  onSalva: (despesa: Despesa) => void
}

/**
 * Editar uma despesa (IU21): descrição, responsável, categoria, valor e
 * vencimento. A situação muda pelos botões da lista, não aqui.
 */
export function ModalEditarDespesa({ despesa, categorias, onFechar, onSalva }: ModalEditarDespesaProps) {
  const [campos, setCampos] = useState<Campos>({
    descricao: despesa.descricao,
    // Despesa antiga não tem responsável: o campo abre vazio e precisa ser preenchido.
    responsavel: despesa.responsavel ?? '',
    categoriaId: despesa.categoriaId,
    valor: valorParaTexto(despesa.valor),
    dataVencimento: despesa.dataVencimento,
  })
  const [erros, setErros] = useState<Erros>({})

  // A categoria atual entra na lista mesmo que a escola não a tenha mais carregada.
  const opcoesCategoria = categorias.some((c) => c.id === despesa.categoriaId)
    ? categorias
    : [{ id: despesa.categoriaId, nome: despesa.categoriaNome }, ...categorias]

  function alterar<K extends keyof Campos>(campo: K, valor: Campos[K]) {
    setCampos((atuais) => ({ ...atuais, [campo]: valor }))
    if (erros[campo]) setErros((atuais) => ({ ...atuais, [campo]: undefined }))
  }

  function validar(): boolean {
    const novos: Erros = {}
    if (!campos.descricao.trim()) novos.descricao = 'Informe a descrição do que foi pago.'
    if (!campos.responsavel.trim()) novos.responsavel = 'Informe o responsável pela despesa.'
    if (!campos.categoriaId) novos.categoriaId = 'Escolha a categoria.'
    if (!(textoParaValor(campos.valor) > 0)) novos.valor = 'Informe um valor maior que zero (ex.: 150,00).'
    if (!campos.dataVencimento) novos.dataVencimento = 'Informe a data da despesa.'
    setErros(novos)
    return Object.keys(novos).length === 0
  }

  async function enviar() {
    onSalva(
      await atualizarDespesa(despesa.id, {
        descricao: campos.descricao.trim(),
        responsavel: campos.responsavel.trim(),
        categoriaId: campos.categoriaId,
        valor: textoParaValor(campos.valor),
        dataVencimento: campos.dataVencimento,
      }),
    )
  }

  return (
    <ModalCadastro
      titulo="Editar despesa"
      rotuloSalvar="Salvar alterações"
      onFechar={onFechar}
      validar={validar}
      onEnviar={enviar}
      traduzirErro={mensagemErro}
    >
      <Campo label="O que foi pago" erro={erros.descricao} obrigatorio largo>
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
      <Campo label="Responsável" erro={erros.responsavel} obrigatorio largo>
        {(props) => (
          <input
            {...props}
            name="responsavel"
            maxLength={MAX_RESPONSAVEL}
            placeholder="Ex.: Administração"
            value={campos.responsavel}
            onChange={(e) => alterar('responsavel', e.target.value)}
          />
        )}
      </Campo>
      <Campo label="Categoria" erro={erros.categoriaId} obrigatorio largo>
        {(props) => (
          <Select
            {...props}
            name="categoriaId"
            opcoes={opcoesCategoria.map((c) => ({ valor: c.id, rotulo: c.nome }))}
            valor={campos.categoriaId}
            onChange={(categoriaId) => alterar('categoriaId', categoriaId)}
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
      <Campo label="Data" erro={erros.dataVencimento} obrigatorio>
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
