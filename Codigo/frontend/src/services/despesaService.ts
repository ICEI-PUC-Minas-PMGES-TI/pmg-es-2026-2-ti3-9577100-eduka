import { DESPESAS_MOCK_INICIAIS, type Despesa, type NovaDespesaRequest } from '../types/despesa'
import { apiRequest } from './api'

// Memória local para persistência de sessão e feedback instantâneo
let despesasMemoria: Despesa[] = [...DESPESAS_MOCK_INICIAIS]

export async function listarDespesas(signal?: AbortSignal): Promise<Despesa[]> {
  try {
    const dados = await apiRequest<Despesa[]>('/despesas', { signal })
    despesasMemoria = dados
    return ordenarPorData(dados)
  } catch {
    return ordenarPorData([...despesasMemoria])
  }
}

export async function criarDespesa(dados: NovaDespesaRequest): Promise<Despesa> {
  try {
    const nova = await apiRequest<Despesa>('/despesas', {
      method: 'POST',
      body: JSON.stringify(dados),
    })
    despesasMemoria.push(nova)
    return nova
  } catch {
    const nova: Despesa = {
      id: `desp-${Date.now()}`,
      descricao: dados.descricao,
      categoria: dados.categoria,
      data: dados.data,
      valor: dados.valor,
      situacao: dados.situacao,
    }
    despesasMemoria.push(nova)
    return nova
  }
}

export async function alternarSituacaoDespesa(id: string): Promise<Despesa> {
  const index = despesasMemoria.findIndex((d) => d.id === id)
  if (index === -1) throw new Error('Despesa não encontrada')

  const atual = despesasMemoria[index]
  const novaSituacao = atual.situacao === 'A_PAGAR' ? 'PAGA' : 'A_PAGAR'

  try {
    const atualizada = await apiRequest<Despesa>(`/despesas/${id}`, {
      method: 'PATCH',
      body: JSON.stringify({ situacao: novaSituacao }),
    })
    despesasMemoria[index] = atualizada
    return atualizada
  } catch {
    const atualizada: Despesa = {
      ...atual,
      situacao: novaSituacao,
    }
    despesasMemoria[index] = atualizada
    return atualizada
  }
}

function ordenarPorData(lista: Despesa[]): Despesa[] {
  return [...lista].sort((a, b) => new Date(a.data).getTime() - new Date(b.data).getTime())
}

