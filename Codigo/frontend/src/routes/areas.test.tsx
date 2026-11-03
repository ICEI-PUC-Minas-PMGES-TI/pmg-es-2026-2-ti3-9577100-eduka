import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router'
import { describe, expect, it } from 'vitest'
import { FinanceiroLayout } from '../roles/financeiro/layouts/FinanceiroLayout'
import { SecretariaLayout } from '../roles/secretaria/layouts/SecretariaLayout'
import { ehAreaMaster, useRotasAcademicas, useRotasFinanceiras } from './areas'

function Rotas() {
  const financeiras = useRotasFinanceiras()
  const academicas = useRotasAcademicas()
  return (
    <p>
      {financeiras.novaCobranca} {academicas.matriculas}
    </p>
  )
}

describe('áreas', () => {
  it('reconhece a área do MASTER pela URL', () => {
    expect(ehAreaMaster('/master')).toBe(true)
    expect(ehAreaMaster('/master/cobrancas')).toBe(true)
    expect(ehAreaMaster('/financeiro/cobrancas')).toBe(false)
    expect(ehAreaMaster('/mastery')).toBe(false)
  })

  it('na gestão, os links das telas compartilhadas ficam em /master', () => {
    render(
      <MemoryRouter initialEntries={['/master/cobrancas']}>
        <Rotas />
      </MemoryRouter>,
    )
    expect(screen.getByText('/master/cobrancas/nova /master/matriculas')).toBeInTheDocument()
  })

  it('fora da gestão, os links são os do próprio perfil', () => {
    render(
      <MemoryRouter initialEntries={['/financeiro/cobrancas']}>
        <Rotas />
      </MemoryRouter>,
    )
    expect(screen.getByText('/financeiro/cobrancas/nova /secretaria/matriculas')).toBeInTheDocument()
  })

  it('as telas do financeiro e da secretaria usam o menu da gestão dentro de /master', () => {
    render(
      <MemoryRouter initialEntries={['/master/despesas']}>
        <FinanceiroLayout titulo="Despesas">conteúdo</FinanceiroLayout>
        <SecretariaLayout titulo="Alunos">conteúdo</SecretariaLayout>
      </MemoryRouter>,
    )
    const menus = screen.getAllByRole('navigation', { name: /navegação da gestão/i })
    expect(menus).toHaveLength(2)
    expect(screen.getAllByRole('link', { name: /recebimentos/i })[0]).toHaveAttribute('href', '/master/recebimentos')
  })
})
