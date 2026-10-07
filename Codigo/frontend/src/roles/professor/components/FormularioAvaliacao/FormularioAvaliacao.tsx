import { useState } from 'react'
import { Campo } from '../../../../components/Campo/Campo'
import { CampoData } from '../../../../components/CampoData/CampoData'
import { Select } from '../../../../components/Select/Select'
import type { AvaliacaoDoProfessor, DadosAvaliacao } from '../../../../types/avaliacaoProfessor'
import { ROTULOS_TIPO_AVALIACAO, type TipoAvaliacao } from '../../../../types/portalAluno'
import { opcional } from '../../../secretaria/components/PainelSecretaria/formatacao'
import { ModalCadastro } from '../../../secretaria/components/PainelSecretaria/ModalCadastro'
import { lerPontos, mascararPontos, mensagemErroAvaliacao, pontosParaCampo } from '../../notas'

// Limites das colunas de AVALIACAO (V19).
const MAX_TITULO = 150
const MAX_DESCRICAO = 500

const OPCOES_TIPO = (Object.keys(ROTULOS_TIPO_AVALIACAO) as TipoAvaliacao[]).map((tipo) => ({
  valor: tipo,
  rotulo: ROTULOS_TIPO_AVALIACAO[tipo],
}))

type Campos = { tipo: TipoAvaliacao | ''; titulo: string; data: string; valor: string; descricao: string }
type Erros = Partial<Record<'tipo' | 'titulo' | 'data' | 'valor', string>>

type FormularioAvaliacaoProps = {
  /** "Turma · Disciplina", para o professor saber onde está cadastrando. */
  contexto: string
  /** Sem avaliação é cadastro; com avaliação, edição com os campos preenchidos. */
  avaliacao?: AvaliacaoDoProfessor
  onFechar: () => void
  onSalvar: (dados: DadosAvaliacao) => Promise<void>
}

export function FormularioAvaliacao({ contexto, avaliacao, onFechar, onSalvar }: FormularioAvaliacaoProps) {
  const [campos, setCampos] = useState<Campos>({
    tipo: avaliacao?.tipo ?? '',
    titulo: avaliacao?.titulo ?? '',
    data: avaliacao?.data ?? '',
    valor: avaliacao ? pontosParaCampo(avaliacao.valor) : '',
    descricao: avaliacao?.descricao ?? '',
  })
  const [erros, setErros] = useState<Erros>({})

  function alterar<K extends keyof Campos>(campo: K, valor: Campos[K]) {
    setCampos((atuais) => ({ ...atuais, [campo]: valor }))
    if (campo in erros) setErros((atuais) => ({ ...atuais, [campo]: undefined }))
  }

  function validar(): boolean {
    const novos: Erros = {}
    if (!campos.tipo) novos.tipo = 'Escolha o tipo.'
    if (!campos.titulo.trim()) novos.titulo = 'Informe o título.'
    if (!campos.data) novos.data = 'Informe a data.'
    if (!((lerPontos(campos.valor) ?? 0) > 0)) novos.valor = 'Informe quantos pontos a avaliação vale.'
    setErros(novos)
    return Object.keys(novos).length === 0
  }

  return (
    <ModalCadastro
      titulo={avaliacao ? 'Editar avaliação' : 'Nova avaliação'}
      rotuloSalvar={avaliacao ? 'Salvar alterações' : 'Salvar avaliação'}
      onFechar={onFechar}
      validar={validar}
      traduzirErro={mensagemErroAvaliacao}
      onEnviar={() =>
        onSalvar({
          tipo: campos.tipo as TipoAvaliacao,
          titulo: campos.titulo.trim(),
          descricao: opcional(campos.descricao),
          data: campos.data,
          valor: lerPontos(campos.valor) as number,
        })
      }
    >
      <Campo label="Turma e disciplina" largo>
        {(props) => <input {...props} readOnly tabIndex={-1} value={contexto} />}
      </Campo>
      <Campo label="Tipo" erro={erros.tipo} obrigatorio>
        {(props) => (
          <Select<TipoAvaliacao | ''>
            {...props}
            name="tipo"
            opcoes={OPCOES_TIPO}
            valor={campos.tipo}
            onChange={(tipo) => alterar('tipo', tipo)}
          />
        )}
      </Campo>
      <Campo label="Data" erro={erros.data} obrigatorio>
        {(props) => <CampoData {...props} name="data" valor={campos.data} onChange={(data) => alterar('data', data)} />}
      </Campo>
      <Campo label="Título" erro={erros.titulo} obrigatorio>
        {(props) => (
          <input
            {...props}
            name="titulo"
            maxLength={MAX_TITULO}
            placeholder="Ex.: Prova 1"
            value={campos.titulo}
            onChange={(e) => alterar('titulo', e.target.value)}
          />
        )}
      </Campo>
      <Campo
        label="Valor (pontos)"
        erro={erros.valor}
        dica={avaliacao && avaliacao.notasLancadas > 0 ? 'Não pode ficar abaixo das notas já lançadas.' : undefined}
        obrigatorio
      >
        {(props) => (
          <input
            {...props}
            name="valor"
            inputMode="decimal"
            placeholder="Ex.: 10"
            value={campos.valor}
            onChange={(e) => alterar('valor', mascararPontos(e.target.value))}
          />
        )}
      </Campo>
      <Campo label="Descrição" largo>
        {(props) => (
          <textarea
            {...props}
            name="descricao"
            rows={3}
            maxLength={MAX_DESCRICAO}
            placeholder="Conteúdo, instruções ou o que o aluno precisa levar"
            value={campos.descricao}
            onChange={(e) => alterar('descricao', e.target.value)}
          />
        )}
      </Campo>
    </ModalCadastro>
  )
}
