import { render, screen, within } from '@testing-library/react'
import { MemoryRouter } from 'react-router'
import { describe, expect, it } from 'vitest'
import { problema, simularApi, type RespostaApi } from '../../../../test/api'
import type { Escola, UsoEscola } from '../../../../types/escola'
import { PainelEscolas } from './PainelEscolas'

function escola(id: string, nome: string): Escola {
  return {
    id,
    nome,
    cnpjCpf: null,
    endereco: null,
    email: null,
    telefone: null,
    ativo: true,
    criadoEm: '2026-01-01',
    plano: null,
    inicioVigencia: null,
    fimVigencia: null,
    limiteUsuarios: null,
    modulos: [],
  }
}

const ESCOLAS = [escola('e1', 'Colégio Bandeirantes'), escola('e2', 'Escola Anglo')]

const USO: UsoEscola[] = [
  {
    escolaId: 'e1',
    alunos: 120,
    usuariosAtivos: 8,
    mes: '2026-11',
    cobrancasNoMes: 95,
    ultimaAtividade: new Date().toISOString(),
  },
  { escolaId: 'e2', alunos: 0, usuariosAtivos: 1, mes: '2026-11', cobrancasNoMes: 0, ultimaAtividade: null },
]

function abrir(uso: RespostaApi) {
  simularApi(({ caminho }) => {
    if (caminho === '/escolas') return { corpo: ESCOLAS }
    if (caminho === '/escolas/uso') return uso
    return undefined
  })
  render(
    <MemoryRouter>
      <PainelEscolas />
    </MemoryRouter>,
  )
}

function linhaDa(nome: string): HTMLElement {
  const linha = screen.getByText(nome).closest('li')
  if (!linha) throw new Error(`Linha de ${nome} não encontrada`)
  return linha
}

describe('Painel de Escolas', () => {
  it('mostra alunos, usuários ativos, cobranças do mês e última atividade de cada escola', async () => {
    abrir({ corpo: USO })

    const bandeirantes = within(await waitUso('Colégio Bandeirantes'))
    expect(bandeirantes.getByText('Alunos').nextSibling).toHaveTextContent('120')
    expect(bandeirantes.getByText('Usuários ativos').nextSibling).toHaveTextContent('8')
    expect(bandeirantes.getByText('Cobranças em nov').nextSibling).toHaveTextContent('95')
    expect(bandeirantes.getByText('Última atividade').nextSibling).toHaveTextContent(/^Hoje às \d{2}:\d{2}$/)

    const anglo = within(await waitUso('Escola Anglo'))
    expect(anglo.getByText('Última atividade').nextSibling).toHaveTextContent('Nenhuma ainda')
  })

  it('sem o uso, as escolas aparecem do mesmo jeito e o painel avisa', async () => {
    abrir(problema(500, 'ERRO_INTERNO'))

    expect(await screen.findByText('Colégio Bandeirantes')).toBeInTheDocument()
    expect(await screen.findByRole('alert')).toHaveTextContent('Não foi possível carregar o uso das escolas.')
    expect(screen.queryByLabelText('Uso da escola')).not.toBeInTheDocument()
  })
})

/** A lista de uso da escola, quando ela chega. */
async function waitUso(nome: string): Promise<HTMLElement> {
  await screen.findAllByLabelText('Uso da escola')
  const uso = linhaDa(nome).querySelector<HTMLElement>('[aria-label="Uso da escola"]')
  if (!uso) throw new Error(`Uso de ${nome} não encontrado`)
  return uso
}
