import type { PaginaComResumo } from '../../hooks/usePaginacao'
import type { Aluno, AlunoDaLista, AlunoRequest, ResumoAlunos, SituacaoNaEscola } from '../../types/pessoas/aluno'
import type { HistoricoFinanceiro } from '../../types/financeiro/pagamentoAluno'
import type { RiscoAtraso } from '../../types/financeiro/riscoAtraso'
import { apiBaixarArquivo, apiRequest } from '../api'

// O backend já limita os alunos à escola de quem está logado. A listagem
// inteira (para escolher alunos) fica em matriculaService.listarAlunos.

export type ConsultaAlunos = {
  pagina: number
  tamanho: number
  /** Parte do nome ou do número de matrícula. */
  busca?: string
  situacao?: SituacaoNaEscola
}

/** Uma página dos alunos, em ordem de nome, com o resumo da escola inteira. */
export function listarPaginaDeAlunos(
  consulta: ConsultaAlunos,
  signal?: AbortSignal,
): Promise<PaginaComResumo<AlunoDaLista, ResumoAlunos>> {
  const params = new URLSearchParams({ pagina: String(consulta.pagina), tamanho: String(consulta.tamanho) })
  if (consulta.busca) params.set('busca', consulta.busca)
  if (consulta.situacao) params.set('situacao', consulta.situacao)
  return apiRequest(`/alunos/pagina?${params}`, { signal })
}

export function buscarAluno(id: string, signal?: AbortSignal): Promise<Aluno> {
  return apiRequest<Aluno>(`/alunos/${id}`, { signal })
}

/** Cria também o login do aluno. 409 se a matrícula ou o e-mail já estão em uso. */
export function cadastrarAluno(dados: AlunoRequest): Promise<Aluno> {
  return apiRequest<Aluno>('/alunos', { method: 'POST', body: JSON.stringify(dados) })
}

/** Substitui os dados do aluno; os responsáveis mudam em responsavelService. 409 como no cadastro. */
export function atualizarAluno(id: string, dados: AlunoRequest): Promise<Aluno> {
  return apiRequest<Aluno>(`/alunos/${id}`, { method: 'PUT', body: JSON.stringify(dados) })
}

/** Nova senha provisória por e-mail. 409 se o aluno já definiu a senha. */
export function reenviarAcessoAluno(id: string): Promise<void> {
  return apiRequest<void>(`/alunos/${id}/primeiro-acesso`, { method: 'POST' })
}

/** Resumo, cobranças e pagamentos de um aluno. Só MASTER e FINANCEIRO; os demais recebem 403. */
export function buscarHistoricoFinanceiroDoAluno(id: string, signal?: AbortSignal): Promise<HistoricoFinanceiro> {
  return apiRequest<HistoricoFinanceiro>(`/alunos/${id}/financeiro`, { signal })
}

/** Risco de atraso do aluno (baixo, médio ou alto) e o motivo. Só MASTER e FINANCEIRO. */
export function buscarRiscoAtrasoDoAluno(id: string, signal?: AbortSignal): Promise<RiscoAtraso> {
  return apiRequest<RiscoAtraso>(`/alunos/${id}/financeiro/risco`, { signal })
}

/**
 * PDF com todos os dados pessoais do aluno, para entregar a quem pediu (LGPD).
 * MASTER e SECRETARIA. 409 se o aluno já foi anonimizado.
 */
export function exportarDadosPessoais(id: string): Promise<Blob> {
  return apiBaixarArquivo(`/alunos/${id}/dados-pessoais`, 'application/pdf')
}

/**
 * Apaga os dados pessoais do aluno que saiu; o histórico financeiro fica. Só
 * MASTER. 409 se ele ainda tem matrícula ativa ou trancada, ou cobrança em aberto.
 */
export function anonimizarAluno(id: string): Promise<void> {
  return apiRequest<void>(`/alunos/${id}/anonimizar`, { method: 'POST' })
}

/** Portal do aluno: os dados do próprio aluno logado. */
export function buscarMeusDados(signal?: AbortSignal): Promise<Aluno> {
  return apiRequest<Aluno>('/portal/aluno/me', { signal })
}
