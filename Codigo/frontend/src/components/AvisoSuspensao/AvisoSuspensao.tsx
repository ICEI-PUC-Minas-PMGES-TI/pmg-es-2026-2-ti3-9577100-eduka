import { useCallback } from 'react'
import { Link } from 'react-router'
import { useCarregamento } from '../../hooks/useCarregamento'
import { paths } from '../../routes/paths'
import { buscarSuspensaoPrevista } from '../../services/faturaPlataformaService'
import type { SuspensaoPrevista } from '../../types/faturaPlataforma'
import { formatarMoeda } from '../../utils/formatters'
import { Icon } from '../Icon/Icon'
import styles from './AvisoSuspensao.module.css'

/** "2026-11-16" -> "16/11/2026", sem passar por Date (sem risco de fuso). */
function dataBr(iso: string): string {
  const [ano, mes, dia] = iso.split('-')
  return `${dia}/${mes}/${ano}`
}

function quando(dias: number, data: string): string {
  if (dias <= 0) return `hoje (${data})`
  if (dias === 1) return `amanhã (${data})`
  return `em ${dias} dias (${data})`
}

/**
 * Faixa no topo de todas as telas do MASTER quando uma fatura do Eduka passou
 * da tolerância: conta os dias até a suspensão e leva para o pagamento. Some
 * sozinha quando a fatura é paga ou cancelada. Erro ao buscar não aparece:
 * o aviso também vai por e-mail.
 */
export function AvisoSuspensao() {
  const buscar = useCallback((signal: AbortSignal) => buscarSuspensaoPrevista(signal), [])
  const { dados } = useCarregamento<SuspensaoPrevista | null>(buscar, null, '')

  if (!dados) return null
  const data = dataBr(dados.suspensaoEm)

  return (
    <div className={styles.aviso} role="alert">
      <span className={styles.icone} aria-hidden="true">
        <Icon name="alert" size={20} />
      </span>
      <div className={styles.texto}>
        <strong>O acesso da escola será suspenso {quando(dados.diasRestantes, data)}.</strong>
        <span>
          A fatura do Eduka "{dados.faturaDescricao}" de {formatarMoeda(dados.faturaValor)}, vencida em{' '}
          {dataBr(dados.faturaVencimento)}, está em aberto. Sem o pagamento, ninguém da escola consegue entrar a
          partir desse dia.
        </span>
      </div>
      <Link to={paths.master.planoEFaturas} className={styles.acao}>
        Pagar a fatura
      </Link>
    </div>
  )
}
