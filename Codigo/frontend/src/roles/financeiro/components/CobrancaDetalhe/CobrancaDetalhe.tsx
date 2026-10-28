import { Badge } from '../../../../components/Badge/Badge'
import { Button } from '../../../../components/Button/Button'
import { Icon } from '../../../../components/Icon/Icon'
import {
  ROTULOS_STATUS_COBRANCA,
  type Cobranca,
  type StatusCobranca,
} from '../../../../types/cobranca'
import { formatarMoeda } from '../../../../utils/formatters'
import styles from './CobrancaDetalhe.module.css'

type CobrancaDetalheProps = {
  cobranca: Cobranca
  onVerBoleto: (cobranca: Cobranca) => void
}

function obterTomBadge(status: StatusCobranca): 'primary' | 'danger' | 'success' | 'neutral' {
  switch (status) {
    case 'PENDENTE':
      return 'primary'
    case 'EM_ATRASO':
      return 'danger'
    case 'PAGA':
      return 'success'
    case 'CANCELADA':
      return 'neutral'
  }
}

function formatarDataBr(isoDate: string): string {
  if (!isoDate) return ''
  const partes = isoDate.split('-')
  if (partes.length === 3) return `${partes[2]}/${partes[1]}/${partes[0]}`
  return isoDate
}

export function CobrancaDetalhe({ cobranca, onVerBoleto }: CobrancaDetalheProps) {
  const dataPagamento = cobranca.status === 'PAGA' ? cobranca.pagamento?.dataPagamento : null

  return (
    <aside className={styles.painel} aria-label="Detalhes da cobrança selecionada">
      <div className={styles.cabecalhoStatus}>
        <Badge tone={obterTomBadge(cobranca.status)}>
          {ROTULOS_STATUS_COBRANCA[cobranca.status]}
        </Badge>
      </div>

      <div className={styles.valor}>{formatarMoeda(cobranca.valor)}</div>

      <h2 className={styles.alunoNome}>{cobranca.alunoNome ?? 'Aluno sem nome'}</h2>
      <p className={styles.descricao}>
        {cobranca.descricao} · vence {formatarDataBr(cobranca.dataVencimento)}
      </p>
      {cobranca.responsavelNome && (
        <p className={styles.responsavel}>Responsável: {cobranca.responsavelNome}</p>
      )}

      {dataPagamento && (
        <div className={styles.infoPagamento}>
          <Icon name="check" size={16} />
          <span>Pagamento confirmado em {formatarDataBr(dataPagamento)}</span>
        </div>
      )}

      <div className={styles.acoes}>
        <Button
          variant="outline"
          icon="fileText"
          className={styles.botaoBoleto}
          onClick={() => onVerBoleto(cobranca)}
        >
          Ver boleto anexado
        </Button>
      </div>
    </aside>
  )
}
