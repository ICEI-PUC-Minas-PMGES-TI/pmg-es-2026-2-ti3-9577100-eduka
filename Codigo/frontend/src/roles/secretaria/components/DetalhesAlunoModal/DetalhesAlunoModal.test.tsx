import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { ACCESS_TOKEN_KEY } from '../../../../services/api'
import { problema, simularApi, type ChamadaApi, type RespostaApi } from '../../../../test/api'
import type { Aluno } from '../../../../types/aluno'
import { nomeDoPdfDeDadosPessoais } from '../../../../utils/arquivos'
import { DetalhesAlunoModal } from './DetalhesAlunoModal'

const ANA: Aluno = {
  id: 'ana',
  escolaId: 'escola-1',
  nome: 'Ana Souza',
  matricula: '2026001',
  genero: 'Feminino',
  cpf: '52998224725',
  email: 'ana@escola.com',
  dataNascimento: '2010-03-14',
  endereco: null,
  responsaveis: [],
  situacao: null,
}

/** JWT só com o payload que o front lê (sub e perfil); a assinatura não é conferida no cliente. */
function logarComo(perfil: string) {
  const payload = btoa(JSON.stringify({ sub: 'usuario-1', perfil })).replace(/=+$/, '')
  localStorage.setItem(ACCESS_TOKEN_KEY, `cabecalho.${payload}.assinatura`)
}

function abrir(responder: (chamada: ChamadaApi) => RespostaApi | undefined, anonimizado = false) {
  const chamadas = simularApi((chamada) => {
    if (chamada.caminho === '/alunos/ana') return { corpo: anonimizado ? { ...ANA, situacao: 'ANONIMIZADO' } : ANA }
    if (chamada.caminho.startsWith('/alunos/ana/financeiro')) return { corpo: null }
    return responder(chamada)
  })
  const onAnonimizado = vi.fn()
  render(
    <DetalhesAlunoModal
      aluno={{ id: 'ana', nome: 'Ana Souza', matricula: '2026001', genero: null }}
      anonimizado={anonimizado}
      onFechar={() => undefined}
      onAnonimizado={onAnonimizado}
    />,
  )
  return { chamadas, onAnonimizado }
}

beforeEach(() => {
  // O jsdom não baixa arquivo nem cria URL de Blob.
  URL.createObjectURL = vi.fn(() => 'blob:dados')
  URL.revokeObjectURL = vi.fn()
})

afterEach(() => {
  localStorage.clear()
  vi.restoreAllMocks()
})

describe('Detalhes do aluno: dados pessoais (LGPD)', () => {
  it('a secretaria exporta o PDF, mas não anonimiza', async () => {
    logarComo('SECRETARIA')
    const { chamadas } = abrir(({ caminho }) =>
      caminho === '/alunos/ana/dados-pessoais' ? { corpo: '%PDF-1.4' } : undefined,
    )
    await screen.findByText('529.982.247-25')

    expect(screen.queryByRole('button', { name: 'Anonimizar' })).not.toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Exportar dados (PDF)' }))

    expect(chamadas.map((c) => `${c.metodo} ${c.caminho}`)).toContain('GET /alunos/ana/dados-pessoais')
    expect(URL.createObjectURL).toHaveBeenCalled()
  })

  it('o master anonimiza depois de confirmar', async () => {
    logarComo('MASTER')
    const { chamadas, onAnonimizado } = abrir(({ metodo, caminho }) =>
      metodo === 'POST' && caminho === '/alunos/ana/anonimizar' ? { status: 204 } : undefined,
    )
    await screen.findByText('529.982.247-25')

    await userEvent.click(screen.getByRole('button', { name: 'Anonimizar' }))
    const confirmar = await screen.findByRole('dialog', { name: 'Anonimizar aluno' })
    expect(within(confirmar).getByText(/Esta ação não pode ser desfeita/)).toBeInTheDocument()
    await userEvent.click(within(confirmar).getByRole('button', { name: 'Anonimizar' }))

    expect(chamadas.map((c) => `${c.metodo} ${c.caminho}`)).toContain('POST /alunos/ana/anonimizar')
    expect(onAnonimizado).toHaveBeenCalled()
  })

  it('explica por que não anonimiza quem ainda tem matrícula', async () => {
    logarComo('MASTER')
    const { onAnonimizado } = abrir(({ metodo }) =>
      metodo === 'POST'
        ? problema(409, 'CONFLITO', 'Aluno com matricula ativa ou trancada; cancele ou conclua a matricula antes de anonimizar')
        : undefined,
    )
    await screen.findByText('529.982.247-25')

    await userEvent.click(screen.getByRole('button', { name: 'Anonimizar' }))
    const confirmar = await screen.findByRole('dialog', { name: 'Anonimizar aluno' })
    await userEvent.click(within(confirmar).getByRole('button', { name: 'Anonimizar' }))

    expect(
      await within(confirmar).findByText(/ainda tem matrícula ativa ou trancada/),
    ).toBeInTheDocument()
    expect(onAnonimizado).not.toHaveBeenCalled()
  })

  it('aluno anonimizado não tem o que exportar nem anonimizar', async () => {
    logarComo('MASTER')
    abrir(() => undefined, true)

    expect(await screen.findByText(/foram apagados a pedido/)).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Exportar dados (PDF)' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Anonimizar' })).not.toBeInTheDocument()
  })

  it('nomeia o PDF sem acento nem espaço', () => {
    expect(nomeDoPdfDeDadosPessoais('Ana Souza Lima Ñúñez', new Date(2026, 10, 8))).toBe(
      'dados-do-aluno-ana-souza-lima-nunez-2026-11-08.pdf',
    )
  })
})
