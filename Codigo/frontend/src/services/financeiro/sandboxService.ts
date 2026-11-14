import { ApiError, apiRequest } from '../api'

// Atalhos de teste do Sandbox do provedor (SandboxController no backend).
// Existem no perfil dev e no ambiente de apresentação; fora deles o backend
// responde 404. Os botões só aparecem nos builds em que as rotas existem.

/** Dev local ou build de apresentação (VITE_DEMO=true). */
export const atalhosSandboxHabilitados = import.meta.env.DEV || import.meta.env.VITE_DEMO === 'true'

/**
 * MASTER: aprova no Sandbox a conta filha da escola. A situação nova chega
 * pelo aviso do provedor ou pelo Sincronizar. 400 se a conta ainda não foi
 * criada no provedor.
 */
export function aprovarContaNoSandbox(): Promise<void> {
  return apiRequest<void>('/config-pagamento/conta/sandbox/aprovar', { method: 'POST' })
}

/**
 * FINANCEIRO: paga no Sandbox a cobrança já emitida (o aluno gerou PIX ou
 * boleto). Ela vira PAGA quando o aviso do provedor chega. 400 se ainda não
 * foi emitida.
 */
export function simularPagamentoNoSandbox(cobrancaId: string): Promise<void> {
  return apiRequest<void>(`/cobrancas/${cobrancaId}/sandbox/confirmar-pagamento`, { method: 'POST' })
}

/**
 * ALUNO: paga no Sandbox a própria cobrança depois de gerar o PIX ou o
 * boleto, como se tivesse pago no banco. O backend já grava o pagamento, sem
 * esperar o aviso do provedor. 400 se ainda não foi emitida.
 */
export function pagarNoSandboxComoAluno(cobrancaId: string): Promise<void> {
  return apiRequest<void>(`/portal/aluno/cobrancas/${cobrancaId}/sandbox/pagar`, { method: 'POST' })
}

/**
 * MASTER: paga no Sandbox a fatura do Eduka depois de gerar o PIX ou o
 * boleto, como se a escola tivesse pago no banco. O valor cai na conta-pai do
 * Eduka e o backend já grava o pagamento. 400 se ainda não foi emitida.
 */
export function pagarFaturaNoSandbox(faturaId: string): Promise<void> {
  return apiRequest<void>(`/config-pagamento/faturas/${faturaId}/sandbox/pagar`, { method: 'POST' })
}

/** Mensagem dos erros comuns dos atalhos, com o caso específico de cada um em `seInvalido`. */
export function mensagemErroSandbox(e: unknown, seInvalido: string): string {
  if (!(e instanceof ApiError)) return 'Erro inesperado no Sandbox.'
  // O Asaas recusou: o motivo dele diz mais do que a mensagem genérica.
  if (e.codigo === 'PROVEDOR_RECUSOU') return `O Asaas recusou: ${e.detalhe ?? 'motivo não informado'}`
  if (e.status === 400) return seInvalido
  if (e.status === 404) return 'Os atalhos de Sandbox estão desligados neste ambiente.'
  if (e.codigo === 'SANDBOX_INDISPONIVEL') {
    return 'O provedor não está no Sandbox: o atalho só funciona em ambiente de teste.'
  }
  if (e.status === 503) return 'O provedor de pagamento não respondeu. Tente de novo em instantes.'
  return e.detalhe ?? e.message
}
