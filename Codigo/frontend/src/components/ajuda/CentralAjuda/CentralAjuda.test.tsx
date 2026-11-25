import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { PainelLayout } from '../../layout/PainelLayout/PainelLayout'
import { simularApi } from '../../../test/api'
import { IndicadorAjuda } from '../IndicadorAjuda/IndicadorAjuda'
import { CentralAjuda } from './CentralAjuda'

const CONTATO = {
  nome: 'Colégio Ipê Amarelo',
  email: 'secretaria@ipe.com.br',
  telefone: '31933334444',
  cidade: 'Belo Horizonte',
  estado: 'MG',
}

describe('CentralAjuda', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('mostra ao aluno o contato real da escola e o que ela resolve', async () => {
    simularApi(({ caminho }) => (caminho === '/minha-escola/contato' ? { corpo: CONTATO } : undefined))
    render(<CentralAjuda perfil="ALUNO" tela="Boletim" />)

    const escola = screen.getByRole('region', { name: 'Fale com a sua escola' })
    expect(await within(escola).findByText('Colégio Ipê Amarelo')).toBeInTheDocument()
    expect(within(escola).getByText('Belo Horizonte · MG')).toBeInTheDocument()
    expect(within(escola).getByRole('link', { name: /secretaria@ipe\.com\.br/ })).toHaveAttribute(
      'href',
      'mailto:secretaria@ipe.com.br',
    )
    expect(within(escola).getByRole('link', { name: /\(31\) 93333-4444/ })).toHaveAttribute('href', 'tel:+5531933334444')
    expect(within(escola).getByText(/notas, faltas e frequência/i)).toBeInTheDocument()
  })

  it('o e-mail ao suporte já leva o perfil e a tela no assunto', () => {
    simularApi(() => ({ corpo: CONTATO }))
    render(<CentralAjuda perfil="ALUNO" tela="Boletim" />)

    const href = screen.getByRole('link', { name: /escrever para o suporte/i }).getAttribute('href') ?? ''
    expect(href).toMatch(/^mailto:contato@eduka\.com\.br\?/)
    expect(decodeURIComponent(href)).toContain('subject=Ajuda no Eduka · Aluno · Boletim')
  })

  it('escola sem e-mail nem telefone: pede para procurar a secretaria pessoalmente', async () => {
    simularApi(() => ({ corpo: { ...CONTATO, email: null, telefone: null } }))
    render(<CentralAjuda perfil="PROFESSOR" />)

    expect(await screen.findByText(/ainda não cadastrou e-mail nem telefone/i)).toBeInTheDocument()
  })

  it('nas telas abertas não chama a API e orienta a procurar a escola', () => {
    const chamadas = simularApi(() => undefined)
    render(<CentralAjuda perfil={null} tela="Login" />)

    expect(screen.getByRole('region', { name: /é aluno, professor ou funcionário/i })).toBeInTheDocument()
    expect(screen.getByText('Esqueci minha senha. E agora?')).toBeInTheDocument()
    expect(chamadas).toHaveLength(0)
  })

  it('o MASTER é a escola: vai direto ao suporte, que cuida do plano e das faturas', () => {
    const chamadas = simularApi(() => undefined)
    render(<CentralAjuda perfil="MASTER" />)

    expect(screen.queryByRole('region', { name: /fale com a/i })).not.toBeInTheDocument()
    expect(screen.getByText(/plano, faturas do eduka e módulos contratados/i)).toBeInTheDocument()
    expect(chamadas).toHaveLength(0)
  })
})

describe('Ajuda no PainelLayout', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('fica habilitada mesmo sem orientações da área, e o indicador da tela também abre a Central', async () => {
    simularApi(() => ({ corpo: CONTATO }))
    render(
      <MemoryRouter>
        <PainelLayout contexto="Secretaria" titulo="Alunos">
          <IndicadorAjuda>Dúvida? Fale com a escola</IndicadorAjuda>
        </PainelLayout>
      </MemoryRouter>,
    )

    const ajuda = screen.getByRole('button', { name: 'Ajuda' })
    expect(ajuda).toBeEnabled()
    await userEvent.click(screen.getByRole('button', { name: /dúvida\? fale com a escola/i }))
    expect(screen.getByRole('dialog', { name: 'Central de ajuda' })).toBeInTheDocument()
  })
})
