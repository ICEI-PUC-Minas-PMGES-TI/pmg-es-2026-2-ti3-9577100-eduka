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

/**
 * Onde o aluno está hoje: com matrícula ativa, só com trancada, ou sem nenhuma
 * das duas. INATIVO é quem a escola inativou: os dados ficam, mas ele não
 * entra no portal nem é matriculado até ser reativado. ANONIMIZADO é quem saiu
 * e teve os dados pessoais apagados (LGPD).
 */
export type SituacaoNaEscola = 'MATRICULADO' | 'TRANCADO' | 'SEM_TURMA' | 'INATIVO' | 'ANONIMIZADO'

/**
 * Como o aluno está no dinheiro, pelas cobranças não canceladas. Vale a
 * primeira que se aplica: quem tem cobrança em atraso está EM_ATRASO mesmo
 * tendo outra a vencer. Espelha o FinanceiroDoAlunoResponse.Situacao do backend.
 */
export type SituacaoFinanceira = 'EM_ATRASO' | 'A_VENCER' | 'EM_DIA' | 'SEM_COBRANCA'

export const ROTULOS_SITUACAO_FINANCEIRA: Record<SituacaoFinanceira, string> = {
  EM_ATRASO: 'Em atraso',
  A_VENCER: 'A vencer',
  EM_DIA: 'Em dia',
  SEM_COBRANCA: 'Sem cobrança',
}

/** Há quanto tempo venceu a cobrança em atraso mais antiga do aluno. */
export type FaixaAtraso = 'ATE_30_DIAS' | 'DE_31_A_60_DIAS' | 'MAIS_DE_60_DIAS'

export const ROTULOS_FAIXA_ATRASO: Record<FaixaAtraso, string> = {
  ATE_30_DIAS: 'Até 30 dias',
  DE_31_A_60_DIAS: '31 a 60 dias',
  MAIS_DE_60_DIAS: 'Mais de 60 dias',
}

// Espelha o FinanceiroDoAlunoResponse do backend. Só vem para o MASTER e o
// FINANCEIRO, com a gestão financeira contratada.
export type FinanceiroDoAluno = {
  situacao: SituacaoFinanceira
  /** Pendentes e em atraso. */
  valorEmAberto: number
  valorEmAtraso: number
  /** Desde o vencimento da cobrança em atraso mais antiga; nulo sem atraso. */
  diasDeAtraso: number | null
  faixaAtraso: FaixaAtraso | null
  /** ISO (yyyy-MM-dd): a pendente que vence primeiro, de hoje em diante. */
  proximoVencimento: string | null
  /** Se a próxima pendente vence de hoje até daqui a 7 dias. */
  venceEm7Dias: boolean
  valorVenceEm7Dias: number
  /** Se há mensalidade com vencimento no mês atual. */
  mensalidadeNoMes: boolean
}

// A listagem (GET /alunos) traz, além do resumo, a situação e a turma da
// matrícula ativa (ou da trancada): as telas não precisam ler as matrículas.
export type AlunoDaLista = AlunoResumo & {
  situacao: SituacaoNaEscola
  turmaId: number | null
  turmaNome: string | null
  /** Só em GET /alunos/pagina, para quem vê o financeiro; nulo (ou ausente) para os demais. */
  financeiro?: FinanceiroDoAluno | null
}

/** A situação financeira da escola inteira, para os cards e as pílulas financeiras. */
export type ResumoFinanceiroAlunos = {
  porSituacao: Record<SituacaoFinanceira, number>
  porFaixaAtraso: Record<FaixaAtraso, number>
  valorEmAtraso: number
  venceEm7Dias: number
  valorVenceEm7Dias: number
  semMensalidadeNoMes: number
}

/** Resumo da escola inteira na página de GET /alunos/pagina, sem a busca nem os filtros. */
export type ResumoAlunos = {
  total: number
  porSituacao: Record<SituacaoNaEscola, number>
  /** Só para quem vê o financeiro (MASTER e FINANCEIRO); nulo (ou ausente) para os demais. */
  financeiro?: ResumoFinanceiroAlunos | null
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
  /** No detalhe, só vem preenchida para o aluno inativo ou anonimizado. */
  situacao?: SituacaoNaEscola | null
  /**
   * ISO (yyyy-MM-dd). Só para o aluno inativo: o dia em que a política de
   * retenção (LGPD) anonimiza o aluno, se até lá ele não for reativado.
   */
  anonimizacaoPrevistaEm?: string | null
}

export type ResponsavelRequest = {
  nome: string
  cpf: string | null
  email: string | null
  telefone: string | null
  parentesco: string | null
  financeiro?: boolean | null
}

// Espelha o VinculoResponsavelRequest do backend.
export type VinculoResponsavelRequest = {
  parentesco: string | null
  financeiro: boolean
}

// Espelha o CadastroResponsavelResponse do backend (GET /responsaveis/{id}).
export type CadastroResponsavel = {
  id: string
  pessoaId: string
  nome: string
  cpf: string | null
  email: string | null
  telefone: string | null
  endereco: Endereco | null
}

// Espelha o CadastroResponsavelRequest do backend. O vínculo com o aluno vai à parte.
export type CadastroResponsavelRequest = {
  nome: string
  /** Só os 11 dígitos. */
  cpf: string
  email: string | null
  telefone: string | null
  endereco: Endereco | null
}

// Espelha o AlunoRequest do backend. A escola vem do token, não do corpo.
// Na edição (PUT) o registro é substituído inteiro e `responsaveis` é ignorado:
// os responsáveis mudam em /alunos/{id}/responsaveis.
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
