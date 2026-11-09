import { vi } from 'vitest'
import type { PaginaDoBackend } from '../hooks/usePaginacao'

/** Parâmetros da consulta de um caminho, ex.: { pagina: '0', tamanho: '10' }. */
export function parametros(caminho: string): Record<string, string> {
  return Object.fromEntries(new URL(caminho, 'http://teste').searchParams)
}

/** Faz o papel de um GET .../pagina: corta `itens` (já filtrados) na página e no tamanho pedidos. */
export function cortarPagina<T>(itens: T[], caminho: string): PaginaDoBackend<T> {
  const { pagina: textoPagina, tamanho: textoTamanho } = parametros(caminho)
  const pagina = Number(textoPagina)
  const tamanho = Number(textoTamanho)
  return {
    conteudo: itens.slice(pagina * tamanho, (pagina + 1) * tamanho),
    pagina,
    tamanho,
    totalElementos: itens.length,
    totalPaginas: Math.ceil(itens.length / tamanho),
  }
}

/** O que o backend responde: o corpo vira JSON; status padrão 200. */
export type RespostaApi = { status?: number; corpo?: unknown }

export type ChamadaApi = { metodo: string; caminho: string; corpo: unknown }

/** ProblemDetail de erro como o ApiExceptionHandler devolve, com o `codigo` que as telas leem. */
export function problema(status: number, codigo: string, detail?: string): RespostaApi {
  return { status, corpo: { status, detail, codigo } }
}

/**
 * Troca o fetch por um backend de mentira. Os testes passam pelo apiRequest de
 * verdade (headers, ProblemDetail, ApiError) e só a rede é simulada.
 * `responder` recebe cada chamada (caminho sem a origem) e devolve a resposta;
 * devolver undefined é erro do teste, para rota esquecida não passar calada.
 *
 * @returns as chamadas feitas, na ordem
 */
export function simularApi(responder: (chamada: ChamadaApi) => RespostaApi | undefined): ChamadaApi[] {
  const chamadas: ChamadaApi[] = []
  vi.stubGlobal(
    'fetch',
    vi.fn(async (url: string, init: RequestInit = {}) => {
      const chamada: ChamadaApi = {
        metodo: init.method ?? 'GET',
        caminho: url.replace(/^https?:\/\/[^/]+/, '').replace(/^\/api(?=\/)/, ''),
        corpo: typeof init.body === 'string' ? JSON.parse(init.body) : undefined,
      }
      chamadas.push(chamada)
      const resposta = responder(chamada)
      if (!resposta) throw new Error(`Rota sem resposta no teste: ${chamada.metodo} ${chamada.caminho}`)
      const status = resposta.status ?? 200
      return new Response(status === 204 ? null : JSON.stringify(resposta.corpo ?? null), {
        status,
        headers: { 'Content-Type': status >= 400 ? 'application/problem+json' : 'application/json' },
      })
    }),
  )
  return chamadas
}
