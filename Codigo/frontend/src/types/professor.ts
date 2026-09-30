// Espelha o ProfessorResponse do backend. Na listagem o CPF vem null: ele só
// aparece no detalhe de um professor específico (GET /professores/{id}).
export type Professor = {
  id: string
  escolaId: string
  nome: string
  cpf: string | null
  email: string | null
  telefone: string | null
  formacao: string | null
  /** ISO (yyyy-MM-dd). */
  dataAdmissao: string | null
  ativo: boolean
}
