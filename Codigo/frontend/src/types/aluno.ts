// Espelha o AlunoResponse do backend na listagem (GET /alunos), que só traz o
// necessário para achar e identificar o aluno: os dados pessoais completos
// ficam no detalhe (GET /alunos/{id}).
export type AlunoResumo = {
  id: string
  nome: string
  /** Número de matrícula do aluno na escola. */
  matricula: string
  genero: string | null
}
