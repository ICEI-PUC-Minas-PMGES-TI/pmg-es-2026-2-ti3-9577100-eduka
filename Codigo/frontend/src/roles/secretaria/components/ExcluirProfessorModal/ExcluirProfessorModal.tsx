import { ConfirmarExclusao } from '../../../../components/ConfirmarExclusao/ConfirmarExclusao'
import { ApiError } from '../../../../services/api'
import type { Professor } from '../../../../types/professor'

type ExcluirProfessorModalProps = {
  professor: Professor
  onFechar: () => void
  /** Exclui de vez; deve lançar ApiError em caso de falha. */
  onExcluir: (professor: Professor) => Promise<void>
  /** Inativa no lugar de excluir; deve lançar ApiError em caso de falha. */
  onInativar: (professor: Professor) => Promise<void>
  onConcluido: (mensagem: string) => void
}

function mensagemErro(erro: unknown): string {
  if (erro instanceof ApiError && erro.status === 409) {
    return 'Este professor já tem histórico na escola e não pode ser excluído. Inative-o.'
  }
  return erro instanceof ApiError ? erro.message : 'Erro inesperado. Tente de novo.'
}

/**
 * Confirmação da exclusão. Excluir é para quem saiu da escola e não tem
 * volta; para afastamento (licença) o caminho é inativar, oferecido aqui
 * mesmo para quem abriu o diálogo pelo motivo errado.
 */
export function ExcluirProfessorModal({
  professor,
  onFechar,
  onExcluir,
  onInativar,
  onConcluido,
}: ExcluirProfessorModalProps) {
  return (
    <ConfirmarExclusao
      titulo="Excluir professor?"
      nome={professor.nome}
      rotuloConfirmar="Excluir professor"
      traduzirErro={mensagemErro}
      onFechar={onFechar}
      alternativa={
        professor.ativo
          ? {
              rotulo: 'Inativar em vez disso',
              icon: 'pause',
              onClick: async () => {
                await onInativar(professor)
                onConcluido(`"${professor.nome}" foi inativado.`)
              },
            }
          : undefined
      }
      onConfirmar={async () => {
        await onExcluir(professor)
        onConcluido(`"${professor.nome}" foi excluído.`)
      }}
    >
      <p>
        <strong>{professor.nome}</strong> e o acesso dele ao Eduka serão apagados. Não dá para desfazer.
      </p>
      <p>Use a exclusão quando o professor saiu da escola. Para licença ou outro afastamento, inative.</p>
    </ConfirmarExclusao>
  )
}
