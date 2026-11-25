import type { PerfilUsuario } from '../../../services/acesso/authService'

/** Perfil logado ou `null` nas telas abertas (login, página inicial, 404...). */
export type PerfilDaAjuda = PerfilUsuario | null

export type Pergunta = { pergunta: string; resposta: string }

export const ROTULO_PERFIL: Record<PerfilUsuario, string> = {
  SUPER_ADMIN: 'Super Admin',
  MASTER: 'Gestão da escola',
  SECRETARIA: 'Secretaria',
  FINANCEIRO: 'Financeiro',
  PROFESSOR: 'Professor',
  ALUNO: 'Aluno',
}

/**
 * Quem é atendido pela própria escola. O MASTER é a escola e o SUPER_ADMIN
 * não tem escola: os dois falam direto com o Eduka.
 */
export function procuraAEscola(perfil: PerfilDaAjuda): perfil is 'SECRETARIA' | 'FINANCEIRO' | 'PROFESSOR' | 'ALUNO' {
  return perfil === 'SECRETARIA' || perfil === 'FINANCEIRO' || perfil === 'PROFESSOR' || perfil === 'ALUNO'
}

/** Título do bloco da escola: a equipe administrativa fala com a gestão. */
export const TITULO_ESCOLA: Record<'SECRETARIA' | 'FINANCEIRO' | 'PROFESSOR' | 'ALUNO', string> = {
  ALUNO: 'Fale com a sua escola',
  PROFESSOR: 'Fale com a sua escola',
  SECRETARIA: 'Fale com a gestão da sua escola',
  FINANCEIRO: 'Fale com a gestão da sua escola',
}

/** O que a escola resolve, por perfil. */
export const ESCOLA_RESOLVE: Record<'SECRETARIA' | 'FINANCEIRO' | 'PROFESSOR' | 'ALUNO', string[]> = {
  ALUNO: [
    'Dados cadastrais errados (nome, CPF, endereço ou responsáveis)',
    'Dúvidas sobre notas, faltas e frequência',
    'Mensalidades, descontos, boletos e pagamentos',
    'Senha provisória ou um novo acesso',
  ],
  PROFESSOR: [
    'Turmas e disciplinas que você leciona',
    'Horário das aulas e calendário',
    'Aluno que falta na lista de uma turma',
    'Senha provisória ou um novo acesso',
  ],
  SECRETARIA: [
    'Permissões e acessos da equipe',
    'Regras da escola e módulos contratados',
    'Senha provisória ou um novo acesso',
  ],
  FINANCEIRO: [
    'Valores, descontos e regras de cobrança',
    'Conta de recebimento da escola',
    'Permissões e acessos da equipe',
  ],
}

/** O que o suporte do Eduka resolve, por perfil. */
export function edukaResolve(perfil: PerfilDaAjuda): string[] {
  if (perfil === null) {
    return [
      'Não consigo entrar e a minha escola não resolveu',
      'A tela mostra erro ou não carrega',
      'Quero conhecer o Eduka para a minha escola',
    ]
  }
  if (perfil === 'SUPER_ADMIN') {
    return ['Erros ou instabilidade na plataforma', 'Dúvidas técnicas sobre escolas, usuários e assinaturas']
  }
  const comum = [
    'Tela que não carrega ou mostra erro',
    'Algo que parece funcionar errado no sistema',
    'Problema de acesso que a escola não conseguiu resolver',
  ]
  return perfil === 'MASTER' ? [...comum, 'Plano, faturas do Eduka e módulos contratados'] : comum
}

/** Perguntas frequentes de cada perfil (e das telas abertas, com `null`). */
export function perguntasFrequentes(perfil: PerfilDaAjuda): Pergunta[] {
  switch (perfil) {
    case null:
      return [
        {
          pergunta: 'Esqueci minha senha. E agora?',
          resposta:
            'Na tela de login, use “Esqueceu sua senha?”. Um código chega no seu e-mail para você criar uma senha nova.',
        },
        {
          pergunta: 'É o meu primeiro acesso.',
          resposta:
            'Entre com o login e a senha provisória do e-mail que a escola enviou. Logo em seguida o sistema pede para você criar a sua própria senha.',
        },
        {
          pergunta: 'O código de verificação não chegou.',
          resposta:
            'Confira a caixa de spam e espere alguns minutos. Se o seu e-mail estiver errado no cadastro, só a escola pode corrigir.',
        },
        {
          pergunta: 'Apareceu “acesso temporariamente indisponível”.',
          resposta:
            'O acesso da escola foi suspenso. Quem regulariza é a gestão da escola junto ao Eduka; avise a secretaria.',
        },
      ]
    case 'ALUNO':
      return [
        {
          pergunta: 'Onde vejo minhas notas e faltas?',
          resposta: 'Em Boletim, por disciplina e por bimestre.',
        },
        {
          pergunta: 'Como pago a mensalidade?',
          resposta: 'Em Pagamentos, por PIX ou boleto. O comprovante fica disponível lá mesmo depois do pagamento.',
        },
        {
          pergunta: 'Meus dados estão errados.',
          resposta: 'Em Meus dados, use “Pedir correção de dados”. A secretaria recebe o pedido e faz a correção.',
        },
        {
          pergunta: 'Esqueci a senha.',
          resposta: 'Saia do sistema e, na tela de login, use “Esqueceu sua senha?”.',
        },
      ]
    case 'PROFESSOR':
      return [
        {
          pergunta: 'Como faço a chamada?',
          resposta: 'Em Chamada, use “Fazer chamada” na aula e marque as presenças.',
        },
        {
          pergunta: 'Como lanço notas?',
          resposta: 'Em Avaliações, crie a prova ou a tarefa e lance as notas. Dá para salvar como rascunho antes.',
        },
        {
          pergunta: 'Uma turma minha não aparece.',
          resposta: 'A secretaria precisa alocar você na turma e na disciplina. Peça a ela.',
        },
      ]
    case 'SECRETARIA':
      return [
        {
          pergunta: 'Como cadastro um aluno?',
          resposta:
            'Em Alunos, use “Novo aluno”. O acesso é criado junto: o login é o e-mail do aluno e a senha provisória é o CPF.',
        },
        {
          pergunta: 'Como matriculo um aluno numa turma?',
          resposta: 'Em Matrículas, escolha a turma e use “Matricular”.',
        },
        {
          pergunta: 'Um aluno pediu correção de dados.',
          resposta: 'Os pedidos aparecem no Início. Corrija o cadastro do aluno e atenda o pedido.',
        },
      ]
    case 'FINANCEIRO':
      return [
        {
          pergunta: 'Como lanço uma cobrança?',
          resposta: 'Em Cobranças, use “Nova cobrança” e escolha o aluno.',
        },
        {
          pergunta: 'O aluno pagou fora do sistema.',
          resposta: 'Abra a cobrança em Cobranças e registre a baixa manual com o valor e a data.',
        },
        {
          pergunta: 'Como exporto os números?',
          resposta: 'Em Relatórios, escolha o período e exporte em CSV.',
        },
      ]
    case 'MASTER':
      return [
        {
          pergunta: 'Como coloco alguém na equipe?',
          resposta: 'Em Equipe, use “Adicionar à equipe” para criar o acesso da secretaria ou do financeiro.',
        },
        {
          pergunta: 'Como a escola passa a receber pagamentos?',
          resposta: 'Em Recebimentos, ative a conta de recebimento com os dados bancários da escola.',
        },
        {
          pergunta: 'Quem mudou um cadastro?',
          resposta: 'Em Atividades fica o histórico do que cada pessoa fez.',
        },
        {
          pergunta: 'Como pago a fatura do Eduka?',
          resposta: 'No Início, abra Plano e faturas e pague por PIX ou boleto.',
        },
      ]
    case 'SUPER_ADMIN':
      return [
        {
          pergunta: 'Como cadastro uma escola?',
          resposta: 'Em Escolas, use “Cadastrar nova escola”. O acesso do gestor é criado junto.',
        },
        {
          pergunta: 'Como mudo o limite de usuários ou os módulos?',
          resposta: 'Em Escolas, edite a escola: plano, limite de usuários e módulos ficam lá.',
        },
        {
          pergunta: 'Onde lanço a fatura de uma escola?',
          resposta: 'Em Assinaturas, use “Lançar fatura”.',
        },
      ]
  }
}
