import { useState } from 'react'
import { Campo } from '../../../../components/Campo/Campo'
import { CampoData } from '../../../../components/CampoData/CampoData'
import { Select } from '../../../../components/Select/Select'
import type { AvaliacaoDoProfessor, DadosAvaliacao } from '../../../../types/avaliacaoProfessor'
import { ROTULOS_TIPO_AVALIACAO, type TipoAvaliacao } from '../../../../types/portalAluno'
import { opcional } from '../../../secretaria/components/PainelSecretaria/formatacao'
import { ModalCadastro } from '../../../../components/ModalCadastro/ModalCadastro'
import type { TurmaDisciplina } from '../../hooks/useTurmaDisciplina'
import { lerPontos, mascararPontos, mensagemErroAvaliacao, pontosParaCampo } from '../../notas'

// Limites das colunas de AVALIACAO (V19).
const MAX_TITULO = 150
const MAX_DESCRICAO = 500

const OPCOES_TIPO = (Object.keys(ROTULOS_TIPO_AVALIACAO) as TipoAvaliacao[]).map((tipo) => ({
  valor: tipo,
  rotulo: ROTULOS_TIPO_AVALIACAO[tipo],
}))

type Campos = {
  turma: string
  disciplina: string
  tipo: TipoAvaliacao | ''
  titulo: string
  data: string
  valor: string
  descricao: string
}
type Erros = Partial<Record<'turma' | 'disciplina' | 'tipo' | 'titulo' | 'data' | 'valor', string>>

/** Turma e disciplina que já chegam escolhidas no cadastro. */
export type EscolhaInicial = { turmaId?: number; disciplinaId?: number }

/** Onde a avaliação é cadastrada. */
export type DestinoAvaliacao = { turmaId: number; disciplinaId: number }

type FormularioAvaliacaoProps = {
  /** Cada "turma + disciplina" que o professor leciona: o que dá para escolher no cadastro. */
  opcoes: TurmaDisciplina[]
  inicial?: EscolhaInicial
  /** Sem avaliação é cadastro; com avaliação, edição com os campos preenchidos. */
  avaliacao?: AvaliacaoDoProfessor
  onFechar: () => void
  onSalvar: (dados: DadosAvaliacao, destino: DestinoAvaliacao) => Promise<void>
}

/** A disciplina do professor na turma, quando é uma só e não há o que escolher. */
function disciplinaUnica(opcoes: TurmaDisciplina[], turma: string): string {
  const daTurma = opcoes.filter((o) => String(o.turma.id) === turma)
  return daTurma.length === 1 ? String(daTurma[0].disciplina.id) : ''
}

/** Só aceita o que o professor leciona; com uma turma só, ela já vem escolhida. */
function escolhaDeInicio(opcoes: TurmaDisciplina[], inicial?: EscolhaInicial) {
  const turmas = new Set(opcoes.map((o) => String(o.turma.id)))
  const pedida = String(inicial?.turmaId)
  const turma = turmas.has(pedida) ? pedida : turmas.size === 1 ? [...turmas][0] : ''
  const pedidaExiste = opcoes.some(
    (o) => String(o.turma.id) === turma && o.disciplina.id === inicial?.disciplinaId,
  )
  return { turma, disciplina: pedidaExiste ? String(inicial?.disciplinaId) : disciplinaUnica(opcoes, turma) }
}

export function FormularioAvaliacao({ opcoes, inicial, avaliacao, onFechar, onSalvar }: FormularioAvaliacaoProps) {
  const [campos, setCampos] = useState<Campos>({
    ...escolhaDeInicio(opcoes, inicial),
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

  // A disciplina depende da turma: trocar a turma refaz a escolha dela.
  function escolherTurma(turma: string) {
    setCampos((atuais) => ({ ...atuais, turma, disciplina: disciplinaUnica(opcoes, turma) }))
    setErros((atuais) => ({ ...atuais, turma: undefined, disciplina: undefined }))
  }

  function validar(): boolean {
    const novos: Erros = {}
    if (!avaliacao) {
      if (!campos.turma) novos.turma = 'Escolha a turma.'
      else if (!campos.disciplina) novos.disciplina = 'Escolha a disciplina.'
    }
    if (!campos.tipo) novos.tipo = 'Escolha o tipo.'
    if (!campos.titulo.trim()) novos.titulo = 'Informe o título.'
    if (!campos.data) novos.data = 'Informe a data.'
    if (!((lerPontos(campos.valor) ?? 0) > 0)) novos.valor = 'Informe quantos pontos a avaliação vale.'
    setErros(novos)
    return Object.keys(novos).length === 0
  }

  const opcoesTurma = [...new Map(opcoes.map((o) => [o.turma.id, o.turma])).values()].map((turma) => ({
    valor: String(turma.id),
    rotulo: `${turma.nome}${turma.ativa ? '' : ' (encerrada)'}`,
  }))
  const opcoesDisciplina = opcoes
    .filter((o) => String(o.turma.id) === campos.turma)
    .map((o) => ({ valor: String(o.disciplina.id), rotulo: o.disciplina.nome }))

  return (
    <ModalCadastro
      titulo={avaliacao ? 'Editar avaliação' : 'Nova avaliação'}
      rotuloSalvar={avaliacao ? 'Salvar alterações' : 'Salvar avaliação'}
      onFechar={onFechar}
      validar={validar}
      traduzirErro={mensagemErroAvaliacao}
      onEnviar={() =>
        onSalvar(
          {
            tipo: campos.tipo as TipoAvaliacao,
            titulo: campos.titulo.trim(),
            descricao: opcional(campos.descricao),
            data: campos.data,
            valor: lerPontos(campos.valor) as number,
          },
          avaliacao ?? { turmaId: Number(campos.turma), disciplinaId: Number(campos.disciplina) },
        )
      }
    >
      {avaliacao ? (
        // Turma e disciplina não mudam na edição.
        <Campo label="Turma e disciplina" largo>
          {(props) => (
            <input {...props} readOnly tabIndex={-1} value={`${avaliacao.turmaNome} · ${avaliacao.disciplinaNome}`} />
          )}
        </Campo>
      ) : (
        <>
          <Campo label="Turma" erro={erros.turma} obrigatorio>
            {(props) => (
              <Select {...props} name="turma" opcoes={opcoesTurma} valor={campos.turma} onChange={escolherTurma} />
            )}
          </Campo>
          <Campo label="Disciplina" erro={erros.disciplina} obrigatorio>
            {(props) =>
              campos.turma ? (
                <Select
                  {...props}
                  name="disciplina"
                  opcoes={opcoesDisciplina}
                  valor={campos.disciplina}
                  onChange={(disciplina) => alterar('disciplina', disciplina)}
                />
              ) : (
                <input {...props} readOnly tabIndex={-1} placeholder="Escolha a turma primeiro" value="" />
              )
            }
          </Campo>
        </>
      )}
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
