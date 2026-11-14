// Pedido de correção de dados do aluno (IU15). Espelha o SolicitacaoCorrecaoResponse do backend.
export type SolicitacaoCorrecao = {
  id: string
  alunoId: string
  /** Só na lista da secretaria. */
  alunoNome: string | null
  alunoMatricula: string | null
  mensagem: string
  status: 'ABERTA' | 'ATENDIDA'
  /** ISO com hora. */
  criadoEm: string
  atendidaEm: string | null
}
