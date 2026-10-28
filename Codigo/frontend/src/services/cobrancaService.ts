import {
  COBRANCAS_MOCK_INICIAIS,
  type Cobranca,
  type DadosRegistroPagamento,
  type NovaCobrancaRequest,
} from '../types/cobranca'
import { apiRequest } from './api'

// Armazenamento em memória local para testes imediatos e reatividade
let cobrancasMemoria: Cobranca[] = [...COBRANCAS_MOCK_INICIAIS]

export async function listarCobrancas(signal?: AbortSignal): Promise<Cobranca[]> {
  try {
    const dados = await apiRequest<Cobranca[]>('/cobrancas', { signal })
    cobrancasMemoria = dados
    return dados
  } catch {
    return [...cobrancasMemoria]
  }
}

export async function registrarPagamento(
  id: string,
  dados?: DadosRegistroPagamento,
): Promise<Cobranca> {
  try {
    const atualizada = await apiRequest<Cobranca>(`/cobrancas/${id}/pagar`, {
      method: 'PATCH',
      body: dados ? JSON.stringify(dados) : undefined,
    })
    cobrancasMemoria = cobrancasMemoria.map((c) => (c.id === id ? atualizada : c))
    return atualizada
  } catch {
    const index = cobrancasMemoria.findIndex((c) => c.id === id)
    if (index === -1) throw new Error('Cobrança não encontrada')

    const atualizada: Cobranca = {
      ...cobrancasMemoria[index],
      status: 'PAGA',
      dataPagamento: dados?.dataPagamento || new Date().toISOString().split('T')[0],
    }
    cobrancasMemoria[index] = atualizada
    return atualizada
  }
}

export async function criarCobranca(dados: NovaCobrancaRequest): Promise<Cobranca> {
  try {
    const nova = await apiRequest<Cobranca>('/cobrancas', {
      method: 'POST',
      body: JSON.stringify(dados),
    })
    cobrancasMemoria.unshift(nova)
    return nova
  } catch {
    const nova: Cobranca = {
      id: `cob-${Date.now()}`,
      alunoNome: dados.alunoNome,
      descricao: `${dados.descricao} · vence ${formatarDataBr(dados.dataVencimento)}`,
      dataVencimento: dados.dataVencimento,
      valor: dados.valor,
      status: 'A_PAGAR',
      responsavelNome: dados.responsavelNome,
      linhaDigitavel: '34191.79001 01043.510047 91020.150008 8 10250000000000',
      codigoBarras: '34198102500000000001790001043510049102015000',
    }
    cobrancasMemoria.unshift(nova)
    return nova
  }
}

function formatarDataBr(isoDate: string): string {
  if (!isoDate) return ''
  const partes = isoDate.split('-')
  if (partes.length === 3) return `${partes[2]}/${partes[1]}/${partes[0]}`
  return isoDate
}
