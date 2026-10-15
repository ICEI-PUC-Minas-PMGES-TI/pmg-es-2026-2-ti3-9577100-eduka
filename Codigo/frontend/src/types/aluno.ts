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

export type Endereco = {
  logradouro: string | null
  numero: string | null
  bairro: string | null
  cidade: string | null
  /** UF, duas letras. */
  estado: string | null
  /** Só os 8 dígitos. */
  cep: string | null
}

export type Responsavel = {
  pessoaId: string
  // Id do cadastro em /responsaveis (pessoaId é o que as cobranças usam).
  responsavelId: string | null
  nome: string
  email: string | null
  telefone: string | null
  parentesco: string | null
  // Quem paga as cobranças do aluno; no máximo um por aluno.
  financeiro: boolean
}

// Espelha o AlunoResponse do backend no detalhe (GET /alunos/{id} e
// GET /portal/aluno/me): todos os dados pessoais, já decifrados.
export type Aluno = AlunoResumo & {
  escolaId: string
  cpf: string | null
  email: string | null
  /** ISO (yyyy-MM-dd). */
  dataNascimento: string | null
  /** Nulo quando nenhum campo do endereço foi preenchido. */
  endereco: Endereco | null
  responsaveis: Responsavel[]
}

export type ResponsavelRequest = {
  nome: string
  cpf: string | null
  email: string | null
  telefone: string | null
  parentesco: string | null
  financeiro?: boolean | null
}

// Espelha o AlunoRequest do backend. A escola vem do token, não do corpo.
// Na edição (PUT) o registro é substituído inteiro e `responsaveis` é ignorado.
export type AlunoRequest = {
  nome: string
  matricula: string
  /** Só os 11 dígitos; vira a senha provisória do primeiro acesso. */
  cpf: string
  /** Vira o login do aluno; o acesso inicial é enviado para ele. */
  email: string
  /** ISO (yyyy-MM-dd). */
  dataNascimento: string | null
  genero: string | null
  endereco: Endereco | null
  responsaveis: ResponsavelRequest[]
}

// O backend guarda o gênero como texto livre; a tela oferece estas opções.
export const OPCOES_GENERO = ['Feminino', 'Masculino', 'Outro', 'Prefere não informar'].map((genero) => ({
  valor: genero,
  rotulo: genero,
}))
