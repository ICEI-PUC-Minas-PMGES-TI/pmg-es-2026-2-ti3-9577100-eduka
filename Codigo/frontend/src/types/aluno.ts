export type ResponsavelInfo = {
  nome: string
  parentesco: string
  cpf: string
  rg: string
  email: string
  telefone: string
  endereco: string
  cidade: string
}

export type AcessoInfo = {
  login: string
  tipoUsuario: string
  statusConta: string
  ultimoAcesso: string
}

export type AlunoPerfil = {
  id: string
  nome: string
  nomeCurto: string
  iniciais: string
  matricula: string
  matriculaAtiva: boolean
  turma: string
  curso: string
  periodo: string
  turno: string
  escolaNome: string
  escolaEmail: string
  escolaTelefone: string
  // Dados Pessoais
  dataNascimento: string
  cpf: string
  rg: string
  email: string
  telefone: string
  endereco: string
  cidade: string
  // Responsável
  responsavel: ResponsavelInfo
  // Acesso
  acesso: AcessoInfo
}

export const ALUNO_MOCK_PADRAO: AlunoPerfil = {
  id: 'aluno-001',
  nome: 'Lucas Andrade Pereira',
  nomeCurto: 'Lucas Andrade',
  iniciais: 'LA',
  matricula: '2026-0412',
  matriculaAtiva: true,
  turma: '2º ano A',
  curso: 'Ensino Médio',
  periodo: '2026/2',
  turno: 'Manhã',
  escolaNome: 'Colégio Santo Agostinho',
  escolaEmail: 'secretaria@santoagostinho.com.br',
  escolaTelefone: '(31) 3215-6000',
  dataNascimento: '14/03/2009',
  cpf: '000.000.000-00',
  rg: '00.000.000',
  email: 'lucas@email.com',
  telefone: '(31) 90000-0000',
  endereco: 'Rua X, 268 · Centro',
  cidade: 'Belo Horizonte · MG',
  responsavel: {
    nome: 'Mariana Andrade Pereira',
    parentesco: 'Mãe',
    cpf: '000.000.000-00',
    rg: '00.000.000',
    email: 'mariana.andrade@email.com',
    telefone: '(31) 98888-0000',
    endereco: 'Rua X, 268 · Centro',
    cidade: 'Belo Horizonte · MG',
  },
  acesso: {
    login: 'lucas.andrade@aluno.eduka.com.br',
    tipoUsuario: 'Aluno',
    statusConta: 'Ativa',
    ultimoAcesso: 'Hoje às 10:15',
  },
}
