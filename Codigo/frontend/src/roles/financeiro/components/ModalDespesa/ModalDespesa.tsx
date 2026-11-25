import { useState } from 'react'
import { Campo } from '../../../../components/formulario/Campo/Campo'
import { CampoData } from '../../../../components/formulario/CampoData/CampoData'
import { Select } from '../../../../components/formulario/Select/Select'
import { ModalCadastro } from '../../../../components/modal/ModalCadastro/ModalCadastro'
import { ApiError } from '../../../../services/api'
import { atualizarDespesa, criarDespesa } from '../../../../services/financeiro/despesaService'
import type { CategoriaDespesa, Despesa, SituacaoDespesa } from '../../../../types/financeiro/despesa'
import { hojeIso } from '../../../../utils/formatters'
import { textoParaValor, valorParaTexto } from '../../valorEmReais'

// Limites das colunas DESCRICAO (V31) e RESPONSAVEL (V56).
const MAX_DESCRICAO = 255
const MAX_RESPONSAVEL = 120

const SITUACOES_DO_LANCAMENTO: { valor: SituacaoDespesa; rotulo: string }[] = [
  { valor: 'A_PAGAR', rotulo: 'A pagar' },
  { valor: 'PAGA', rotulo: 'Paga' },
]

type Campos = {
  descricao: string
  responsavel: string
  categoriaId: string
  valor: string
  dataVencimento: string
  /** Só no lançamento: na edição a situação muda pelos botões da lista. */
  situacao: SituacaoDespesa
}
type Erros = Partial<Record<keyof Campos, string>>

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

type ModalDespesaProps = {
  /** Despesa a editar; sem ela, o modal lança uma nova. */
  despesa?: Despesa
  categorias: CategoriaDespesa[]
  /** Responsável que abre preenchido no lançamento (o último usado na tela). */
  responsavelSugerido?: string
  onFechar: () => void
  onSalva: (despesa: Despesa) => void
}

/**
 * Lançar ou editar uma despesa (IU21): descrição, responsável, categoria,
 * valor e data. No lançamento escolhe também se já está paga; na edição a
 * situação muda pelos botões da lista, não aqui.
 */
export function ModalDespesa({ despesa, categorias, responsavelSugerido = '', onFechar, onSalva }: ModalDespesaProps) {
  const editando = despesa !== undefined
  const [campos, setCampos] = useState<Campos>(() =>
    despesa
      ? {
          descricao: despesa.descricao,
          // Despesa antiga não tem responsável: o campo abre vazio e precisa ser preenchido.
          responsavel: despesa.responsavel ?? '',
          categoriaId: despesa.categoriaId,
          valor: valorParaTexto(despesa.valor),
          dataVencimento: despesa.dataVencimento,
          situacao: despesa.situacao,
        }
      : {
          descricao: '',
          responsavel: responsavelSugerido,
          // Vazia até alguém escolher: vale a primeira categoria (ver categoriaId abaixo).
          categoriaId: '',
          valor: '',
          dataVencimento: hojeIso(),
          situacao: 'A_PAGAR',
        },
  )
  const [erros, setErros] = useState<Erros>({})

  // No lançamento, sem escolha, vale a primeira categoria; assim o modal aberto
  // antes de as categorias chegarem também fica com uma escolhida.
  const categoriaId = campos.categoriaId || (editando ? '' : (categorias[0]?.id ?? ''))

  // Na edição, a categoria atual entra na lista mesmo que a escola não a tenha mais carregada.
  const opcoesCategoria =
    !despesa || categorias.some((c) => c.id === despesa.categoriaId)
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
    if (!categoriaId) novos.categoriaId = 'Escolha a categoria.'
    if (!(textoParaValor(campos.valor.trim()) > 0)) novos.valor = 'Informe um valor maior que zero (ex.: 150,00).'
    if (!campos.dataVencimento) novos.dataVencimento = 'Informe a data da despesa.'
    setErros(novos)
    return Object.keys(novos).length === 0
  }

  async function enviar() {
    const dados = {
      descricao: campos.descricao.trim(),
      responsavel: campos.responsavel.trim(),
      categoriaId,
      valor: textoParaValor(campos.valor.trim()),
      dataVencimento: campos.dataVencimento,
    }
    onSalva(
      despesa
        ? await atualizarDespesa(despesa.id, dados)
        : await criarDespesa({ ...dados, paga: campos.situacao === 'PAGA' }),
    )
  }

  return (
    <ModalCadastro
      titulo={editando ? 'Editar despesa' : 'Nova despesa'}
      rotuloSalvar={editando ? 'Salvar alterações' : 'Salvar despesa'}
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
            placeholder="Ex.: conta de água"
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
            placeholder="Nenhuma categoria cadastrada"
            opcoes={opcoesCategoria.map((c) => ({ valor: c.id, rotulo: c.nome }))}
            valor={categoriaId}
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
            placeholder="0,00"
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
      {!editando && (
        <Campo label="Situação" largo>
          {(props) => (
            <Select
              {...props}
              name="situacao"
              opcoes={SITUACOES_DO_LANCAMENTO}
              valor={campos.situacao}
              onChange={(situacao) => alterar('situacao', situacao)}
            />
          )}
        </Campo>
      )}
    </ModalCadastro>
  )
}
