import type { SolicitacaoCorrecao } from '../../types/pessoas/solicitacaoCorrecao'
import { apiRequest } from '../api'

// Pedido de correção de dados (IU15): o aluno pede pelo portal; a secretaria
// (ou o MASTER) vê os abertos e marca como atendidos.

/** 409 SOLICITACAO_ABERTA se o aluno já tem um pedido aguardando a secretaria. */
export function pedirCorrecao(mensagem: string): Promise<SolicitacaoCorrecao> {
  return apiRequest<SolicitacaoCorrecao>('/portal/aluno/correcoes', {
    method: 'POST',
    body: JSON.stringify({ mensagem }),
  })
}

/** O pedido aberto do aluno logado, ou null (o backend responde 204). */
export async function buscarMinhaCorrecaoAberta(signal?: AbortSignal): Promise<SolicitacaoCorrecao | null> {
  return (await apiRequest<SolicitacaoCorrecao | undefined>('/portal/aluno/correcoes/aberta', { signal })) ?? null
}

export function listarCorrecoesAbertas(signal?: AbortSignal): Promise<SolicitacaoCorrecao[]> {
  return apiRequest<SolicitacaoCorrecao[]>('/alunos/correcoes', { signal })
}

export function atenderCorrecao(id: string): Promise<SolicitacaoCorrecao> {
  return apiRequest<SolicitacaoCorrecao>(`/alunos/correcoes/${id}/atender`, { method: 'PATCH' })
}
