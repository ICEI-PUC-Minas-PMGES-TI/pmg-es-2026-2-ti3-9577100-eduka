import { useMemo, useState } from 'react'
import { Campo } from '../../../../components/Campo/Campo'
import { Select } from '../../../../components/Select/Select'
import { useCarregamento } from '../../../../hooks/useCarregamento'
import { listarMatriculas, mensagemErroMatricula } from '../../../../services/matriculaService'
import type { TurmaAcademica } from '../../../../types/academico'
import {
  ROTULOS_STATUS_MATRICULA,
  type AtualizarMatriculaRequest,
  type Matricula,
  type StatusMatricula,
} from '../../../../types/matricula'
import { ModalCadastro } from '../../../../components/ModalCadastro/ModalCadastro'
import styles from './AlterarMatriculaModal.module.css'

const MAX_OBSERVACAO = 500

type Acao = 'TRANCAR' | 'REATIVAR' | 'TRANSFERIR' | 'CONCLUIR' | 'CANCELAR' | 'OBSERVACAO'

const ROTULOS_ACAO: Record<Acao, string> = {
  TRANCAR: 'Trancar matrícula',
  REATIVAR: 'Reativar matrícula',
  TRANSFERIR: 'Transferir para outra turma',
  CONCLUIR: 'Concluir (aluno terminou a turma)',
  CANCELAR: 'Cancelar matrícula',
  OBSERVACAO: 'Só editar a observação',
}

// Espelha as transições do StatusMatricula do backend.
const ACOES_POR_STATUS: Record<StatusMatricula, Acao[]> = {
  ATIVA: ['TRANCAR', 'TRANSFERIR', 'CONCLUIR', 'CANCELAR', 'OBSERVACAO'],
  TRANCADA: ['REATIVAR', 'CANCELAR', 'OBSERVACAO'],
  CANCELADA: ['OBSERVACAO'],
  CONCLUIDA: ['OBSERVACAO'],
}

const STATUS_DA_ACAO: Partial<Record<Acao, StatusMatricula>> = {
  TRANCAR: 'TRANCADA',
  REATIVAR: 'ATIVA',
  CONCLUIR: 'CONCLUIDA',
  CANCELAR: 'CANCELADA',
}

// Estável: o useCarregamento pede o valor inicial sempre igual.
const SEM_MATRICULAS: Matricula[] = []

/** Ações finais: depois delas a matrícula não muda mais de situação. */
const ACOES_FINAIS: Acao[] = ['CONCLUIR', 'CANCELAR']

type AlterarMatriculaModalProps = {
  matricula: Matricula
  /** Com as vagas ocupadas de cada uma. */
  turmas: TurmaAcademica[]
  onFechar: () => void
  /** Envia a alteração; deve lançar ApiError em caso de falha. */
  onAtualizar: (id: string, dados: AtualizarMatriculaRequest) => Promise<Matricula>
  onConcluido: (mensagem: string) => void
}

/**
 * Muda a situação, transfere de turma ou edita a observação de uma
 * matrícula. Só oferece as ações que o backend aceita para a situação atual.
 */
export function AlterarMatriculaModal({
  matricula,
  turmas,
  onFechar,
  onAtualizar,
  onConcluido,
}: AlterarMatriculaModalProps) {
  const acoes = ACOES_POR_STATUS[matricula.status]
  const [acao, setAcao] = useState<Acao | ''>(acoes.length === 1 ? acoes[0] : '')
  const [destinoId, setDestinoId] = useState('')
  const [observacao, setObservacao] = useState(matricula.observacao ?? '')
  const [erros, setErros] = useState<{ acao?: string; destino?: string; observacao?: string }>({})

  const nome = matricula.alunoNome ?? 'Aluno'

  // As turmas onde o aluno já tem matrícula só importam para transferir: busca só então.
  const alunoId = matricula.alunoId
  const transferindo = acao === 'TRANSFERIR'
  const buscarDoAluno = useMemo(
    () => (transferindo ? (signal: AbortSignal) => listarMatriculas({ alunoId }, signal) : null),
    [transferindo, alunoId],
  )
  const doAluno = useCarregamento(buscarDoAluno, SEM_MATRICULAS, 'O aluno não existe mais.')
  const turmasDoAluno = new Set(doAluno.dados.map((m) => m.turmaId))

  // Destinos possíveis: turma ativa, com vaga, onde o aluno ainda não tem matrícula.
  const opcoesDestino = doAluno.carregando
    ? []
    : turmas
        .filter((t) => t.ativa && !turmasDoAluno.has(t.id) && t.matriculasAtivas < t.vagas)
        .map((t) => ({
          valor: String(t.id),
          rotulo: `${t.nome} · ${t.periodoLetivoNome} (${t.vagas - t.matriculasAtivas} vagas)`,
        }))
  const dicaDestino = doAluno.carregando
    ? 'Carregando as turmas…'
    : (doAluno.erro ?? (opcoesDestino.length === 0 ? 'Nenhuma turma ativa com vaga disponível.' : undefined))

  function validar(): boolean {
    const novos: typeof erros = {}
    if (!acao) novos.acao = 'Escolha o que fazer.'
    if (acao === 'TRANSFERIR' && !destinoId) novos.destino = 'Escolha a turma de destino.'
    if (acao === 'OBSERVACAO' && observacao.trim() === (matricula.observacao ?? '')) {
      novos.observacao = 'A observação não mudou.'
    }
    setErros(novos)
    return Object.keys(novos).length === 0
  }

  async function enviar() {
    // validar() já garante que uma ação foi escolhida.
    const escolhida = acao as Acao
    const dados: AtualizarMatriculaRequest = {}
    const status = STATUS_DA_ACAO[escolhida]
    if (status) dados.status = status
    if (escolhida === 'TRANSFERIR') dados.turmaId = Number(destinoId)
    // Só manda a observação se mudou; vazia apaga.
    if (observacao.trim() !== (matricula.observacao ?? '')) dados.observacao = observacao.trim()

    const atualizada = await onAtualizar(matricula.id, dados)
    onConcluido(
      escolhida === 'TRANSFERIR'
        ? `${nome} foi transferido(a) para a turma ${atualizada.turmaNome}.`
        : escolhida === 'OBSERVACAO'
          ? `Observação de ${nome} atualizada.`
          : `Matrícula de ${nome} agora está ${ROTULOS_STATUS_MATRICULA[atualizada.status].toLowerCase()}.`,
    )
  }

  return (
    <ModalCadastro
      titulo="Alterar matrícula"
      rotuloSalvar="Salvar alteração"
      onFechar={onFechar}
      validar={validar}
      onEnviar={enviar}
      traduzirErro={mensagemErroMatricula}
    >
      <p className={styles.resumo}>
        <strong>{nome}</strong> · Nº {matricula.alunoNumeroMatricula}
        <br />
        Turma {matricula.turmaNome} · situação atual:{' '}
        <strong>{ROTULOS_STATUS_MATRICULA[matricula.status]}</strong>
      </p>

      <Campo label="O que fazer" erro={erros.acao} obrigatorio largo>
        {(props) => (
          <Select<Acao | ''>
            {...props}
            name="acao"
            opcoes={acoes.map((a) => ({ valor: a, rotulo: ROTULOS_ACAO[a] }))}
            valor={acao}
            onChange={(nova) => {
              setAcao(nova)
              setErros({})
            }}
          />
        )}
      </Campo>

      {acao === 'TRANSFERIR' && (
        <Campo
          label="Turma de destino"
          erro={erros.destino}
          obrigatorio
          largo
          dica={dicaDestino}
        >
          {(props) => (
            <Select<string>
              {...props}
              name="turmaDestino"
              opcoes={opcoesDestino}
              valor={destinoId}
              onChange={(id) => {
                setDestinoId(id)
                setErros((atuais) => ({ ...atuais, destino: undefined }))
              }}
            />
          )}
        </Campo>
      )}

      {acao && ACOES_FINAIS.includes(acao) && (
        <p className={styles.aviso} role="note">
          Essa situação é final: depois de salvar, a matrícula não pode ser reativada e o aluno não pode ser
          matriculado de novo nesta turma. Para um afastamento temporário, use “Trancar”.
        </p>
      )}

      <Campo label="Observação" erro={erros.observacao} largo>
        {(props) => (
          <textarea
            {...props}
            name="observacao"
            rows={2}
            maxLength={MAX_OBSERVACAO}
            placeholder="Ex.: motivo do trancamento"
            value={observacao}
            onChange={(e) => {
              setObservacao(e.target.value)
              setErros((atuais) => ({ ...atuais, observacao: undefined }))
            }}
          />
        )}
      </Campo>
    </ModalCadastro>
  )
}
