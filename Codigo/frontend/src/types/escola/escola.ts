// Valores do enum PlanoEscola do backend.
export const PLANOS_ESCOLA = ['BASICO', 'AVANCADO', 'ENTERPRISE'] as const

export type PlanoEscola = (typeof PLANOS_ESCOLA)[number]

export const ROTULOS_PLANO: Record<PlanoEscola, string> = {
  BASICO: 'Básico',
  AVANCADO: 'Avançado',
  ENTERPRISE: 'Enterprise',
}

// Opções prontas para o componente Select.
export const OPCOES_PLANO = PLANOS_ESCOLA.map((plano) => ({ valor: plano, rotulo: ROTULOS_PLANO[plano] }))

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
  // Opcionais no banco: escolas antigas ou do mock podem vir sem eles.
  cnpjCpf: string | null
  // Nulo quando nenhum campo do endereço foi preenchido.
  endereco: Endereco | null
  email: string | null
  telefone: string | null
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

// Espelha o MasterInicialRequest do backend: o gestor da escola, que recebe o
// primeiro login MASTER. O e-mail vira o login e a senha provisória é o CPF.
export type MasterInicialRequest = {
  nome: string
  /** Só dígitos. */
  cpf: string
  email: string
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
  /** Cria o login MASTER e envia o e-mail de primeiro acesso. */
  master: MasterInicialRequest
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

/** Uso de uma escola no Painel de Escolas (IU02). Espelha o UsoEscolaResponse do backend (GET /escolas/uso). */
export type UsoEscola = {
  escolaId: string
  alunos: number
  /** Logins ativos da escola, de todos os perfis. */
  usuariosAtivos: number
  /** Mês de `cobrancasNoMes` (yyyy-MM), no fuso da escola. */
  mes: string
  /** Cobranças lançadas no mês, canceladas ou não. */
  cobrancasNoMes: number
  /** Instante (ISO) do último login ou da última ação de um usuário da escola; nulo se nunca houve. */
  ultimaAtividade: string | null
}
