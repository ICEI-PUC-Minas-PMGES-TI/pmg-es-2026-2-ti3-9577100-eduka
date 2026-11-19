import type { PerfilUsuario } from '../../services/acesso/authService'

// Registro de atividades (IU28): espelha o LOG_AUDITORIA do backend.

/** Espelha o enum OperacaoAuditoria. */
export type OperacaoAuditoria =
  | 'CRIAR'
  | 'EDITAR'
  | 'CANCELAR'
  | 'PAGAR'
  | 'CONFIRMAR_PAGAMENTO'
  | 'PAGO_APOS_CANCELAR'
  | 'ESTORNAR'
  | 'CONTESTAR'
  | 'ATIVAR'
  | 'ALTERAR_SITUACAO'
  | 'INATIVAR'
  | 'EXCLUIR'
  | 'REENVIAR_ACESSO'
  | 'PUBLICAR_NOTAS'
  | 'SUSPENDER'
  | 'EXPORTAR_DADOS'
  | 'ANONIMIZAR'
  | 'ENCERRAR'
  | 'ABRIR_MATRICULAS'
  | 'VINCULAR'
  | 'DESVINCULAR'
  | 'ATENDER'

/** Espelha o enum EntidadeAuditada. */
export type EntidadeAuditada =
  | 'COBRANCA'
  | 'PAGAMENTO'
  | 'DESPESA'
  | 'CONTA_RECEBIMENTO'
  | 'FATURA_PLATAFORMA'
  | 'USUARIO'
  | 'ALUNO'
  | 'PROFESSOR'
  | 'MATRICULA'
  | 'AVALIACAO'
  | 'NOTA'
  | 'ESCOLA'
  | 'PERIODO_LETIVO'
  | 'CURSO'
  | 'DISCIPLINA'
  | 'TURMA'
  | 'ALOCACAO'
  | 'HORARIO'
  | 'AULA'
  | 'FREQUENCIA'
  | 'RESPONSAVEL'
  | 'CATEGORIA_DESPESA'
  | 'PEDIDO_CORRECAO'

/** Espelha o enum OrigemAuditoria: quem fez, quando não foi uma pessoa da escola. */
export type OrigemAuditoria = 'USUARIO' | 'PROVEDOR' | 'SISTEMA'

/** Uma linha do Registro de atividades. Espelha o AtividadeResponse. */
export type Atividade = {
  id: string
  /** Instante (ISO). */
  dataHora: string
  origem: OrigemAuditoria
  operacao: OperacaoAuditoria
  entidade: EntidadeAuditada
  registroId: string
  /** Nulos quando foi o provedor ou o sistema. */
  usuarioId: string | null
  /** Nulo também se a pessoa não é mais da escola. */
  usuarioNome: string | null
  usuarioPerfil: PerfilUsuario | null
}

/** Quem já fez alguma atividade: as opções do filtro por pessoa. Espelha o AutorAtividadeResponse. */
export type AutorAtividade = {
  usuarioId: string
  /** Nulo se a pessoa não é mais da escola. */
  nome: string | null
  perfil: PerfilUsuario
}

/** Filtros do Registro de atividades; vazio ('') é "todos". */
export type FiltrosAtividades = {
  usuarioId: string
  de: string
  ate: string
  entidade: EntidadeAuditada | ''
  operacao: OperacaoAuditoria | ''
}

export const ROTULOS_PERFIL: Record<PerfilUsuario, string> = {
  SUPER_ADMIN: 'Eduka',
  MASTER: 'Gestão',
  SECRETARIA: 'Secretaria',
  FINANCEIRO: 'Financeiro',
  PROFESSOR: 'Professor',
  ALUNO: 'Aluno',
}

/** Nome da operação no filtro. */
export const ROTULOS_OPERACAO: Record<OperacaoAuditoria, string> = {
  CRIAR: 'Criar',
  EDITAR: 'Editar',
  CANCELAR: 'Cancelar',
  PAGAR: 'Registrar pagamento',
  CONFIRMAR_PAGAMENTO: 'Confirmar pagamento',
  PAGO_APOS_CANCELAR: 'Pago após cancelar',
  ESTORNAR: 'Estornar',
  CONTESTAR: 'Contestar',
  ATIVAR: 'Ativar',
  ALTERAR_SITUACAO: 'Alterar situação',
  INATIVAR: 'Inativar',
  EXCLUIR: 'Excluir',
  REENVIAR_ACESSO: 'Reenviar acesso',
  PUBLICAR_NOTAS: 'Publicar notas',
  SUSPENDER: 'Suspender',
  EXPORTAR_DADOS: 'Exportar dados',
  ANONIMIZAR: 'Anonimizar',
  ENCERRAR: 'Encerrar',
  ABRIR_MATRICULAS: 'Abrir matrículas',
  VINCULAR: 'Vincular',
  DESVINCULAR: 'Desvincular',
  ATENDER: 'Atender',
}

/** Nome da entidade no filtro. */
export const ROTULOS_ENTIDADE: Record<EntidadeAuditada, string> = {
  COBRANCA: 'Cobrança',
  PAGAMENTO: 'Pagamento',
  DESPESA: 'Despesa',
  CONTA_RECEBIMENTO: 'Conta de recebimento',
  FATURA_PLATAFORMA: 'Fatura do Eduka',
  USUARIO: 'Login da equipe',
  ALUNO: 'Aluno',
  PROFESSOR: 'Professor',
  MATRICULA: 'Matrícula',
  AVALIACAO: 'Avaliação',
  NOTA: 'Nota',
  ESCOLA: 'Escola',
  PERIODO_LETIVO: 'Período letivo',
  CURSO: 'Curso',
  DISCIPLINA: 'Disciplina',
  TURMA: 'Turma',
  ALOCACAO: 'Alocação de professor',
  HORARIO: 'Horário da turma',
  AULA: 'Aula',
  FREQUENCIA: 'Chamada de um aluno',
  RESPONSAVEL: 'Responsável',
  CATEGORIA_DESPESA: 'Categoria de despesa',
  PEDIDO_CORRECAO: 'Pedido de correção',
}

const VERBOS: Record<OperacaoAuditoria, string> = {
  CRIAR: 'Criou',
  EDITAR: 'Editou',
  CANCELAR: 'Cancelou',
  PAGAR: 'Registrou o pagamento de',
  CONFIRMAR_PAGAMENTO: 'Confirmou o pagamento de',
  PAGO_APOS_CANCELAR: 'Recebeu o pagamento depois de cancelar',
  ESTORNAR: 'Estornou',
  CONTESTAR: 'Contestou',
  ATIVAR: 'Ativou',
  ALTERAR_SITUACAO: 'Alterou a situação de',
  INATIVAR: 'Inativou',
  EXCLUIR: 'Excluiu',
  REENVIAR_ACESSO: 'Reenviou o acesso de',
  PUBLICAR_NOTAS: 'Publicou as notas de',
  SUSPENDER: 'Suspendeu',
  EXPORTAR_DADOS: 'Exportou os dados de',
  ANONIMIZAR: 'Anonimizou',
  ENCERRAR: 'Encerrou',
  ABRIR_MATRICULAS: 'Abriu as matrículas de',
  VINCULAR: 'Vinculou',
  DESVINCULAR: 'Desvinculou',
  ATENDER: 'Atendeu',
}

const OBJETOS: Record<EntidadeAuditada, string> = {
  COBRANCA: 'uma cobrança',
  PAGAMENTO: 'um pagamento',
  DESPESA: 'uma despesa',
  CONTA_RECEBIMENTO: 'a conta de recebimento',
  FATURA_PLATAFORMA: 'uma fatura do Eduka',
  USUARIO: 'um login da equipe',
  ALUNO: 'um aluno',
  PROFESSOR: 'um professor',
  MATRICULA: 'uma matrícula',
  AVALIACAO: 'uma avaliação',
  NOTA: 'uma nota',
  ESCOLA: 'a escola',
  PERIODO_LETIVO: 'um período letivo',
  CURSO: 'um curso',
  DISCIPLINA: 'uma disciplina',
  TURMA: 'uma turma',
  ALOCACAO: 'a alocação de um professor',
  HORARIO: 'uma aula da grade',
  AULA: 'uma aula',
  FREQUENCIA: 'a chamada de um aluno',
  RESPONSAVEL: 'um responsável',
  CATEGORIA_DESPESA: 'uma categoria de despesa',
  PEDIDO_CORRECAO: 'um pedido de correção',
}

/** "Registrou o pagamento de" + "uma cobrança"; "de a conta" vira "da conta", "de o" vira "do". */
export function descreverAcao(operacao: OperacaoAuditoria, entidade: EntidadeAuditada): string {
  const verbo = VERBOS[operacao]
  const objeto = OBJETOS[entidade]
  const contracao = verbo.endsWith(' de') && /^[ao] /.exec(objeto)
  return contracao ? `${verbo.slice(0, -3)} d${objeto}` : `${verbo} ${objeto}`
}

/** Quem fez: o nome da pessoa, ou o provedor de pagamento, ou o próprio Eduka. */
export function descreverAutor(origem: OrigemAuditoria, nome: string | null): string {
  if (origem === 'PROVEDOR') return 'Asaas'
  if (origem === 'SISTEMA') return 'Eduka (automático)'
  return nome ?? 'Ex-integrante da escola'
}
