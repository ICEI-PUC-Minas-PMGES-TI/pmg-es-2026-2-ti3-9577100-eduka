import { useMemo } from 'react'
import {
  buscarComprovanteDaMinhaFatura,
  buscarMinhaFatura,
  pagarFatura,
} from '../../../../services/escola/faturaPlataformaService'
import { pagarFaturaNoSandbox } from '../../../../services/financeiro/sandboxService'
import type { FaturaPlataforma } from '../../../../types/escola/faturaPlataforma'
import { ModalPagar, type Pagavel } from '../../../aluno/components/ModalPagamento/ModalPagamento'

type ModalPagamentoFaturaProps = {
  fatura: FaturaPlataforma
  /** Pagamento confirmado: a lista recarrega. */
  onPago: () => void
  onFechar: () => void
}

/**
 * Pagar a fatura do Eduka (IU24-A, comando 1): as mesmas modais do aluno
 * (IU17-A a IU17-D), com a fatura no lugar da mensalidade. O dinheiro vai para
 * a conta do Eduka; o boleto da fatura não tem PDF, só a linha digitável.
 */
export function ModalPagamentoFatura({ fatura, onPago, onFechar }: ModalPagamentoFaturaProps) {
  const pagavel = useMemo<Pagavel>(
    () => ({
      descricao: fatura.descricao,
      valor: fatura.valor,
      dataVencimento: fatura.dataVencimento,
      nome: 'fatura',
      canceladaPor: 'pelo Eduka',
      naoPagavel: 'Esta fatura não está mais aberta para pagamento.',
      ondeAparece: 'em Faturas anteriores',
      avisoPago: 'O comprovante fica em Faturas anteriores.',
      pagar: (forma) => pagarFatura(fatura.id, forma),
      consultar: () => buscarMinhaFatura(fatura.id),
      buscarComprovante: () => buscarComprovanteDaMinhaFatura(fatura.id),
      pagarNoSandbox: () => pagarFaturaNoSandbox(fatura.id),
    }),
    [fatura],
  )
  return <ModalPagar pagavel={pagavel} onPago={onPago} onFechar={onFechar} />
}
