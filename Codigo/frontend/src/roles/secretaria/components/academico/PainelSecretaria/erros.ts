import { ApiError } from '../../../../../services/api'

/**
 * Mensagem para a secretaria a partir do erro de edição ou exclusão no
 * painel acadêmico. O backend manda o motivo no `detail` (sem acento); aqui
 * vira texto de tela.
 */
export function mensagemErroAcademico(erro: unknown): string {
  if (!(erro instanceof ApiError)) return 'Erro inesperado. Tente de novo.'
  const detalhe = erro.detalhe ?? ''
  const minusculo = detalhe.toLowerCase()

  const saindoDaGrade = /A disciplina (.+) tem avaliacoes nesta turma/.exec(detalhe)
  if (saindoDaGrade) {
    return `A disciplina ${saindoDaGrade[1]} já tem avaliações nesta turma e não pode sair da grade.`
  }
  const vagas = /tem (\d+) matriculas ativas/.exec(detalhe)
  if (vagas) return `A turma tem ${vagas[1]} matrículas ativas; as vagas não podem ficar abaixo disso.`

  if (minusculo.includes('periodo letivo tem turmas')) {
    return 'Existem turmas neste período letivo. Exclua as turmas ou mude o período delas antes.'
  }
  if (minusculo.includes('curso tem turmas')) {
    return 'Existem turmas deste curso. Exclua as turmas ou mude o curso delas antes.'
  }
  if (minusculo.includes('disciplinas vinculadas')) {
    return 'Existem disciplinas vinculadas a este curso. Exclua-as ou mude o curso delas antes.'
  }
  if (minusculo.includes('grade de alguma turma')) {
    return 'A disciplina está na grade de alguma turma. Tire-a da grade das turmas antes.'
  }
  if (minusculo.includes('alunos matriculados')) {
    return 'A turma tem alunos matriculados. Cancele ou transfira as matrículas antes.'
  }
  if (minusculo.includes('avaliacoes cadastradas')) return 'A turma já tem avaliações cadastradas pelos professores.'
  if (minusculo.includes('professores alocados')) {
    return 'A turma tem professores alocados. Desaloque-os na tela de alocação antes.'
  }
  if (minusculo.includes('fim deve ser igual ou posterior')) return 'O término não pode ser antes do início.'

  if (erro.status === 404) return 'Este cadastro não existe mais. Recarregue a página.'
  if (erro.status === 400) return 'Os dados foram recusados pelo servidor. Revise os campos e tente novamente.'
  return erro.message
}
