// Valores do enum PlanoEscola do backend.
export const PLANOS_ESCOLA = ['BASICO', 'AVANCADO', 'ENTERPRISE'] as const

export type PlanoEscola = (typeof PLANOS_ESCOLA)[number]

export const ROTULOS_PLANO: Record<PlanoEscola, string> = {
  BASICO: 'Básico',
  AVANCADO: 'Avançado',
  ENTERPRISE: 'Enterprise',
}

// Valores do enum ModuloEscola do backend, na ordem em que aparecem na tela.
export const MODULOS_ESCOLA = [
  'PORTAL_ALUNO',
  'PORTAL_PROFESSOR',
  'GESTAO_FINANCEIRA',
  'SECRETARIA_VIRTUAL',
  'BIBLIOTECA_DIGITAL',
  'CONTROLE_FREQUENCIA',
] as const

export type ModuloEscola = (typeof MODULOS_ESCOLA)[number]

export const ROTULOS_MODULO: Record<ModuloEscola, string> = {
  PORTAL_ALUNO: 'Portal do Aluno',
  PORTAL_PROFESSOR: 'Portal do Professor',
  GESTAO_FINANCEIRA: 'Gestão Financeira',
  SECRETARIA_VIRTUAL: 'Secretaria Virtual',
  BIBLIOTECA_DIGITAL: 'Biblioteca Digital',
  CONTROLE_FREQUENCIA: 'Controle de Frequência',
}

// Espelha o EnderecoDto do backend. Todos os campos são opcionais.
export type Endereco = {
  logradouro: string | null
  numero: string | null
  bairro: string | null
  cidade: string | null
  /** Sigla da UF. */
  estado: string | null
  /** Só dígitos. */
  cep: string | null
}

// Espelha o EscolaResponse do backend.
export type Escola = {
  id: string
  nome: string
  cnpjCpf: string
  // Nulo quando nenhum campo do endereço foi preenchido.
  endereco: Endereco | null
  email: string
  telefone: string
  ativo: boolean
  criadoEm: string
  // Nulo em escolas cadastradas antes do plano existir.
  plano: PlanoEscola | null
  /** Datas no formato ISO (AAAA-MM-DD). */
  inicioVigencia: string | null
  fimVigencia: string | null
  limiteUsuarios: number | null
  modulos: ModuloEscola[]
}

// Espelha o EscolaRequest do backend (POST /escolas).
export type EscolaRequest = {
  nome: string
  /** Só dígitos, como no DER (CHAR(14)). */
  cnpjCpf: string
  endereco?: Partial<Endereco>
  email: string
  telefone?: string
  ativo: boolean
  plano: PlanoEscola
}

// Espelha o EscolaUpdateRequest do backend (PUT /escolas/{id}). Campo omitido
// mantém o valor atual; texto vazio limpa o campo. `endereco` e `modulos`
// substituem os anteriores por inteiro. Não há inicioVigencia: o início é
// fixado no cadastro e não pode ser alterado.
export type EscolaUpdateRequest = {
  nome: string
  cnpjCpf?: string
  endereco?: Partial<Endereco>
  email?: string
  telefone?: string
  plano?: PlanoEscola
  ativo?: boolean
  fimVigencia?: string
  limiteUsuarios?: number
  modulos?: ModuloEscola[]
}
