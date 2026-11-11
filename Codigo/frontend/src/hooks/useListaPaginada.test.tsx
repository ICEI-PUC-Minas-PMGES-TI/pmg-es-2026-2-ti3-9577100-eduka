import { act, renderHook, waitFor } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { ApiError } from '../services/api'
import { useListaPaginada, type BuscarPagina, type PaginaDoBackend } from './usePaginacao'

type Pagina = PaginaDoBackend<string>

function pagina(conteudo: string[], numero: number, totalElementos: number, tamanho = 10): Pagina {
  return { conteudo, pagina: numero, tamanho, totalElementos, totalPaginas: Math.ceil(totalElementos / tamanho) }
}

/** Busca controlada pelo teste: cada chamada fica pendente até `responder`. */
function buscaManual() {
  const pendentes: { pagina: number; tamanho: number; signal: AbortSignal; resolver: (p: Pagina) => void; rejeitar: (e: unknown) => void }[] = []
  const buscar: BuscarPagina<Pagina> = (numero, tamanho, signal) =>
    new Promise((resolver, rejeitar) => pendentes.push({ pagina: numero, tamanho, signal, resolver, rejeitar }))
  return { buscar, pendentes }
}

describe('useListaPaginada', () => {
  it('mantém a página anterior enquanto a próxima chega, e só então troca', async () => {
    const { buscar, pendentes } = buscaManual()
    const { result } = renderHook(() => useListaPaginada(buscar, 'Falhou.'))
    expect(result.current.carregando).toBe(true)

    await act(async () => pendentes[0].resolver(pagina(['a'], 0, 11)))
    expect(result.current.paginacao?.itensDaPagina).toEqual(['a'])

    act(() => result.current.paginacao?.irPara(2))
    expect(result.current.atualizando).toBe(true)
    expect(result.current.paginacao?.itensDaPagina).toEqual(['a'])
    expect(pendentes.at(-1)?.pagina).toBe(1)

    await act(async () => pendentes.at(-1)?.resolver(pagina(['k'], 1, 11)))
    expect(result.current.atualizando).toBe(false)
    expect(result.current.paginacao).toMatchObject({ itensDaPagina: ['k'], pagina: 2, totalPaginas: 2 })
  })

  it('trocar a busca volta à primeira página e cancela a consulta anterior', async () => {
    const primeira = buscaManual()
    const segunda = buscaManual()
    const { result, rerender } = renderHook(({ buscar }) => useListaPaginada(buscar, 'Falhou.'), {
      initialProps: { buscar: primeira.buscar },
    })
    await act(async () => primeira.pendentes[0].resolver(pagina(['a'], 0, 30)))
    act(() => result.current.paginacao?.irPara(3))
    const atrasada = primeira.pendentes.at(-1)

    rerender({ buscar: segunda.buscar })

    expect(atrasada?.signal.aborted).toBe(true)
    expect(segunda.pendentes[0].pagina).toBe(0)
    await act(async () => segunda.pendentes[0].resolver(pagina(['filtrado'], 0, 1)))
    // A resposta da busca antiga, mesmo chegando depois, não volta para a tela.
    await act(async () => atrasada?.resolver(pagina(['velho'], 2, 30)))
    expect(result.current.paginacao?.itensDaPagina).toEqual(['filtrado'])
  })

  it('se a lista encolheu e a página pedida veio vazia, vai para a última', async () => {
    const { buscar, pendentes } = buscaManual()
    const { result } = renderHook(() => useListaPaginada(buscar, 'Falhou.'))
    await act(async () => pendentes[0].resolver(pagina(['a'], 0, 30)))
    act(() => result.current.paginacao?.irPara(3))

    await act(async () => pendentes.at(-1)?.resolver(pagina([], 2, 12)))

    expect(pendentes.at(-1)?.pagina).toBe(1)
    await act(async () => pendentes.at(-1)?.resolver(pagina(['k', 'l'], 1, 12)))
    expect(result.current.paginacao).toMatchObject({ itensDaPagina: ['k', 'l'], pagina: 2 })
  })

  it('mostra o detalhe do erro do backend e recarrega a mesma página', async () => {
    const { buscar, pendentes } = buscaManual()
    const { result } = renderHook(() => useListaPaginada(buscar, 'Falhou.'))

    await act(async () => pendentes[0].rejeitar(new ApiError(500, 'Erro', 'Banco fora do ar')))
    expect(result.current.erro).toBe('Banco fora do ar')

    act(() => result.current.recarregar())
    expect(result.current.erro).toBeNull()
    await act(async () => pendentes.at(-1)?.resolver(pagina(['a'], 0, 1)))
    await waitFor(() => expect(result.current.paginacao?.itensDaPagina).toEqual(['a']))
    expect(pendentes).toHaveLength(2)
  })

  it('falha fora do backend usa a mensagem da tela', async () => {
    const { buscar, pendentes } = buscaManual()
    const { result } = renderHook(() => useListaPaginada(buscar, 'Não foi possível carregar.'))

    await act(async () => pendentes[0].rejeitar(new TypeError('json inválido')))

    expect(result.current.erro).toBe('Não foi possível carregar.')
  })

  it('sem busca (null) não consulta, não mostra "Carregando" nem a lista de uma busca anterior', async () => {
    const { buscar, pendentes } = buscaManual()
    const { result, rerender } = renderHook(({ ativa }) => useListaPaginada(ativa ? buscar : null, 'Falhou.'), {
      initialProps: { ativa: false },
    })
    expect(pendentes).toHaveLength(0)
    expect(result.current).toMatchObject({ carregando: false, atualizando: false, paginacao: null })

    rerender({ ativa: true })
    await act(async () => pendentes[0].resolver(pagina(['a'], 0, 1)))
    expect(result.current.paginacao?.itensDaPagina).toEqual(['a'])

    rerender({ ativa: false })
    expect(result.current.paginacao).toBeNull()
  })

  it('troca o 404 pelo texto da tela, quando ela passa um', async () => {
    const { buscar, pendentes } = buscaManual()
    const { result } = renderHook(() => useListaPaginada(buscar, 'Falhou.', 'A turma não é mais sua.'))

    await act(async () => pendentes[0].rejeitar(new ApiError(404, 'Recurso não encontrado.', 'Alocacao nao encontrada')))

    expect(result.current.erro).toBe('A turma não é mais sua.')
  })

  it('trocar a quantidade por página mantém na tela o primeiro item que estava nela', async () => {
    const { buscar, pendentes } = buscaManual()
    const { result } = renderHook(() => useListaPaginada(buscar, 'Falhou.'))
    await act(async () => pendentes[0].resolver(pagina(['a'], 0, 60)))
    act(() => result.current.paginacao?.irPara(3))
    await act(async () => pendentes.at(-1)?.resolver(pagina(['u'], 2, 60)))

    // Itens 21 a 30 na página 3 de 10; com 20 por página, o item 21 fica na página 2.
    act(() => result.current.paginacao?.mudarPorPagina?.(20))

    expect(pendentes.at(-1)).toMatchObject({ pagina: 1, tamanho: 20 })
  })
})
