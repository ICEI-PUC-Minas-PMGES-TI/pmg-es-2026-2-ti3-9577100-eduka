import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import type { ContaRecebimento, StatusContaRecebimento } from '../../../../types/contaRecebimento'
import { PainelContaRecebimento } from './PainelContaRecebimento'

function conta(status: StatusContaRecebimento): ContaRecebimento {
  return {
    status,
    recebimentosHabilitados: status === 'APROVADA',
    motivoNaoHabilitado: null,
    razaoSocial: 'Colégio Ipê Amarelo',
    emailFinanceiro: null,
    telefoneCelular: null,
    tipoEmpresa: null,
    faturamentoMensal: null,
    logradouro: null,
    numero: null,
    complemento: null,
    bairro: null,
    cep: null,
    cnpj: '34567891000184',
    bancoCodigo: null,
    contaFinal: null,
    situacao: null,
    ultimoErro: null,
    solicitadaEm: null,
    aprovadaEm: null,
    sincronizadaEm: null,
    podeAtivar: false,
    podeSincronizar: true,
  }
}

const APROVAR = /aprovar no sandbox/i

function renderizar(status: StatusContaRecebimento, onAprovarNoSandbox?: () => void) {
  return render(
    <PainelContaRecebimento
      conta={conta(status)}
      sincronizando={false}
      erroSincronizar={null}
      onAtivar={vi.fn()}
      onSincronizar={vi.fn()}
      onAprovarNoSandbox={onAprovarNoSandbox}
    />,
  )
}

describe('PainelContaRecebimento', () => {
  it('conta aguardando aprovação mostra o atalho de Sandbox', async () => {
    const onAprovar = vi.fn()
    renderizar('AGUARDANDO_APROVACAO', onAprovar)

    await userEvent.click(screen.getByRole('button', { name: APROVAR }))

    expect(onAprovar).toHaveBeenCalledOnce()
  })

  it('sem o atalho ou com a conta já aprovada, o botão não aparece', () => {
    renderizar('AGUARDANDO_APROVACAO')
    expect(screen.queryByRole('button', { name: APROVAR })).not.toBeInTheDocument()
  })

  it('conta aprovada não oferece aprovar de novo', () => {
    renderizar('APROVADA', vi.fn())
    expect(screen.queryByRole('button', { name: APROVAR })).not.toBeInTheDocument()
  })
})
