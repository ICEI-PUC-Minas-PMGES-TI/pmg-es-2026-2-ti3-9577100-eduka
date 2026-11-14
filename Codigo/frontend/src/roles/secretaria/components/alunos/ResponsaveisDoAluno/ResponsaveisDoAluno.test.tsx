import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { problema, simularApi, type ChamadaApi, type RespostaApi } from '../../../../../test/api'
import type { Aluno, Responsavel } from '../../../../../types/pessoas/aluno'
import { AlunoModal } from '../AlunoModal/AlunoModal'
import { ResponsaveisDoAluno } from './ResponsaveisDoAluno'

const CPF = '52998224725'
const CPF_MASCARADO = '529.982.247-25'

const MARTA: Responsavel = {
  pessoaId: 'p-marta',
  responsavelId: 'r-marta',
  nome: 'Marta',
  email: 'marta@exemplo.com',
  telefone: null,
  parentesco: 'Mãe',
  financeiro: true,
}

const JOAO: Responsavel = { ...MARTA, pessoaId: 'p-joao', responsavelId: 'r-joao', nome: 'João', parentesco: 'Pai', financeiro: false }

const LISTA = '/alunos/ana/responsaveis'

function backend(lista: Responsavel[], extra: (chamada: ChamadaApi) => RespostaApi | undefined = () => undefined) {
  return simularApi((chamada) => {
    const resposta = extra(chamada)
    if (resposta) return resposta
    if (chamada.metodo === 'GET' && chamada.caminho === LISTA) return { corpo: lista }
    return undefined
  })
}

async function preencherNovo(cpf?: string) {
  await userEvent.click(await screen.findByRole('button', { name: 'Adicionar responsável' }))
  await userEvent.type(screen.getByRole('textbox', { name: /^Nome/ }), 'Lucia')
  if (cpf) await userEvent.type(screen.getByRole('textbox', { name: /^CPF/ }), cpf)
}

describe('ResponsaveisDoAluno', () => {
  it('mostra quem é o financeiro', async () => {
    backend([MARTA, JOAO])
    render(<ResponsaveisDoAluno alunoId="ana" />)

    const marta = (await screen.findByText('Marta')).closest('li') as HTMLElement
    expect(within(marta).getByText('Responsável financeiro')).toBeInTheDocument()
    const joao = screen.getByText('João').closest('li') as HTMLElement
    expect(within(joao).queryByText('Responsável financeiro')).not.toBeInTheDocument()
    expect(screen.queryByText(/saem em nome do aluno/)).not.toBeInTheDocument()
  })

  it('avisa que, sem financeiro, as cobranças saem em nome do aluno', async () => {
    backend([JOAO])
    render(<ResponsaveisDoAluno alunoId="ana" />)

    expect(await screen.findByText(/as cobranças saem em nome do aluno/)).toBeInTheDocument()
  })

  it('não inclui financeiro sem CPF', async () => {
    const chamadas = backend([])
    render(<ResponsaveisDoAluno alunoId="ana" />)

    await preencherNovo()
    await userEvent.click(screen.getByRole('switch'))
    await userEvent.click(screen.getByRole('button', { name: 'Incluir responsável' }))

    expect(await screen.findByText(/quem paga as cobranças precisa ter CPF/)).toBeInTheDocument()
    expect(chamadas.some((c) => c.metodo === 'POST')).toBe(false)
  })

  it('inclui o financeiro de uma vez e recarrega a lista', async () => {
    const lista: Responsavel[] = []
    const chamadas = backend(lista, ({ metodo }) => {
      if (metodo !== 'POST') return undefined
      lista.push({ ...MARTA, nome: 'Lucia' })
      return { status: 201, corpo: lista[0] }
    })
    render(<ResponsaveisDoAluno alunoId="ana" />)

    await preencherNovo(CPF)
    await userEvent.click(screen.getByRole('switch'))
    await userEvent.click(screen.getByRole('button', { name: 'Incluir responsável' }))

    const item = (await screen.findByText('Lucia')).closest('li') as HTMLElement
    expect(within(item).getByText('Responsável financeiro')).toBeInTheDocument()
    expect(chamadas.find((c) => c.metodo === 'POST')).toMatchObject({
      caminho: LISTA,
      corpo: { nome: 'Lucia', cpf: CPF, financeiro: true },
    })
  })

  it('Enter nos campos salva o responsável, não o formulário do aluno', async () => {
    const aoEnviar = vi.fn((e: React.FormEvent) => e.preventDefault())
    const chamadas = backend([], ({ metodo }) => (metodo === 'POST' ? { status: 201, corpo: JOAO } : undefined))
    render(
      <form onSubmit={aoEnviar}>
        <ResponsaveisDoAluno alunoId="ana" />
      </form>,
    )

    await preencherNovo()
    await userEvent.keyboard('{Enter}')

    expect(chamadas.some((c) => c.metodo === 'POST')).toBe(true)
    expect(aoEnviar).not.toHaveBeenCalled()
  })

  it('edita mantendo o endereço e atualiza o vínculo', async () => {
    const endereco = { logradouro: 'Rua A', numero: '1', bairro: null, cidade: null, estado: null, cep: null }
    const chamadas = backend([JOAO], ({ metodo, caminho }) => {
      if (metodo === 'GET' && caminho === '/responsaveis/r-joao') {
        return { corpo: { id: 'r-joao', pessoaId: 'p-joao', nome: 'João', cpf: CPF, email: null, telefone: null, endereco } }
      }
      if (metodo === 'PUT') return { corpo: JOAO }
      return undefined
    })
    render(<ResponsaveisDoAluno alunoId="ana" />)

    await userEvent.click(await screen.findByRole('button', { name: 'Editar João' }))
    expect(await screen.findByDisplayValue(CPF_MASCARADO)).toBeInTheDocument()
    await userEvent.click(screen.getByRole('switch'))
    await userEvent.click(screen.getByRole('button', { name: 'Salvar responsável' }))

    await screen.findByRole('button', { name: 'Adicionar responsável' })
    const puts = chamadas.filter((c) => c.metodo === 'PUT')
    expect(puts[0]).toMatchObject({ caminho: '/responsaveis/r-joao', corpo: { cpf: CPF, endereco } })
    expect(puts[1]).toMatchObject({
      caminho: `${LISTA}/r-joao`,
      corpo: { parentesco: 'Pai', financeiro: true },
    })
  })

  it('desvincula só depois de confirmar e explica a recusa por cobrança em aberto', async () => {
    const chamadas = backend([MARTA], ({ metodo }) =>
      metodo === 'DELETE' ? problema(409, 'ERRO', 'Responsavel tem cobrancas') : undefined,
    )
    render(<ResponsaveisDoAluno alunoId="ana" />)

    await userEvent.click(await screen.findByRole('button', { name: 'Desvincular Marta' }))
    expect(screen.getByRole('alert')).toHaveTextContent('próximas cobranças saem em nome do aluno')
    expect(chamadas.some((c) => c.metodo === 'DELETE')).toBe(false)

    await userEvent.click(screen.getByRole('button', { name: 'Confirmar' }))

    expect(await screen.findByText(/tem cobranças em aberto deste aluno/)).toBeInTheDocument()
    expect(chamadas.find((c) => c.metodo === 'DELETE')?.caminho).toBe(`${LISTA}/r-marta`)
  })
})

describe('AlunoModal', () => {
  const salvo: Aluno = {
    id: 'ana',
    nome: 'Ana',
    matricula: '2026001',
    escolaId: 'e',
    cpf: '11144477735',
    email: 'ana@escola.com',
    dataNascimento: null,
    endereco: null,
    responsaveis: [],
  } as unknown as Aluno

  it('no cadastro, envia o financeiro e só um fica marcado', async () => {
    const onSalvar = vi.fn(async () => salvo)
    render(<AlunoModal onFechar={vi.fn()} onSalvar={onSalvar} onSalvo={vi.fn()} />)

    await userEvent.type(screen.getByRole('textbox', { name: /^Nome completo/ }), 'Ana')
    await userEvent.type(screen.getByRole('textbox', { name: /^Matrícula/ }), '2026001')
    await userEvent.type(screen.getByRole('textbox', { name: /^CPF/ }), '11144477735')
    await userEvent.type(screen.getByRole('textbox', { name: /^E-mail/ }), 'ana@escola.com')

    await userEvent.click(screen.getByRole('button', { name: 'Adicionar responsável' }))
    await userEvent.click(screen.getByRole('button', { name: 'Adicionar responsável' }))
    const nomes = screen.getAllByRole('textbox', { name: 'Nome' })
    await userEvent.type(nomes[0], 'Marta')
    await userEvent.type(nomes[1], 'João')

    const chaves = screen.getAllByRole('switch')
    await userEvent.click(chaves[0])
    await userEvent.click(chaves[1])
    expect(chaves[0]).not.toBeChecked()
    expect(chaves[1]).toBeChecked()

    // O financeiro (João) está sem CPF: não envia.
    await userEvent.click(screen.getByRole('button', { name: 'Salvar aluno' }))
    expect(await screen.findByText(/quem paga as cobranças precisa ter CPF/)).toBeInTheDocument()
    expect(onSalvar).not.toHaveBeenCalled()

    const cpfDoJoao = document.querySelector('[name="responsavel-1-cpf"]') as HTMLInputElement
    await userEvent.type(cpfDoJoao, CPF)
    await userEvent.click(screen.getByRole('button', { name: 'Salvar aluno' }))

    expect(onSalvar).toHaveBeenCalledWith(
      expect.objectContaining({
        responsaveis: [
          expect.objectContaining({ nome: 'Marta', financeiro: false }),
          expect.objectContaining({ nome: 'João', cpf: CPF, financeiro: true }),
        ],
      }),
    )
    // Digita o formulário inteiro: passa do limite padrão com a suíte rodando em paralelo.
  }, 30000)

  it('na edição, os responsáveis podem ser alterados', async () => {
    backend([MARTA], ({ caminho }) => (caminho === '/alunos/ana' ? { corpo: salvo } : undefined))
    render(<AlunoModal alunoId="ana" onFechar={vi.fn()} onSalvar={vi.fn()} onSalvo={vi.fn()} />)

    expect(await screen.findByRole('button', { name: 'Editar Marta' })).toBeInTheDocument()
    expect(screen.queryByText(/ainda não podem ser alterados/)).not.toBeInTheDocument()
  })
})
