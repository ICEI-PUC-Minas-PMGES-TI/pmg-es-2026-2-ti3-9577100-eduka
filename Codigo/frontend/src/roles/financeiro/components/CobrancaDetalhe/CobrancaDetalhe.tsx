import { useState } from 'react'
import { Badge } from '../../../../components/Badge/Badge'
import { Button } from '../../../../components/Button/Button'
import { Icon } from '../../../../components/Icon/Icon'
import {
  ROTULOS_STATUS_COBRANCA,
  type Cobranca,
  type DadosRegistroPagamento,
  type StatusCobranca,
} from '../../../../types/cobranca'
import { formatarMoeda } from '../../../../utils/formatters'
import { ModalRegistrarPagamento } from '../ModalRegistrarPagamento/ModalRegistrarPagamento'
import styles from './CobrancaDetalhe.module.css'

type CobrancaDetalheProps = {
  cobranca: Cobranca
  onVerBoleto: (cobranca: Cobranca) => void
  onRegistrarPagamento: (cobrancaId: string, dados?: DadosRegistroPagamento) => Promise<void>
}

function obterTomBadge(status: StatusCobranca): 'primary' | 'danger' | 'success' | 'neutral' {
  switch (status) {
    case 'A_PAGAR':
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

export function CobrancaDetalhe({
  cobranca,
  onVerBoleto,
  onRegistrarPagamento,
}: CobrancaDetalheProps) {
  const [salvando, setSalvando] = useState(false)
  const [mensagemSucesso, setMensagemSucesso] = useState(false)
  const [modalRegistrarAberto, setModalRegistrarAberto] = useState(false)

  async function handleConfirmarRegistro(dados: DadosRegistroPagamento) {
    setSalvando(true)
    try {
      await onRegistrarPagamento(cobranca.id, dados)
      setModalRegistrarAberto(false)
      setMensagemSucesso(true)
      setTimeout(() => setMensagemSucesso(false), 4000)
    } finally {
      setSalvando(false)
    }
  }

  const jaPaga = cobranca.status === 'PAGA'

  return (
    <>
      <aside className={styles.painel} aria-label="Detalhes da cobrança selecionada">
        <div className={styles.cabecalhoStatus}>
          <Badge tone={obterTomBadge(cobranca.status)}>
            {ROTULOS_STATUS_COBRANCA[cobranca.status]}
          </Badge>
        </div>

        <div className={styles.valor}>{formatarMoeda(cobranca.valor)}</div>

        <h2 className={styles.alunoNome}>{cobranca.alunoNome}</h2>
        <p className={styles.descricao}>{cobranca.descricao}</p>
        <p className={styles.responsavel}>Responsável: {cobranca.responsavelNome}</p>

        {jaPaga && cobranca.dataPagamento && (
          <div className={styles.infoPagamento}>
            <Icon name="check" size={16} />
            <span>Pagamento confirmado em {formatarDataBr(cobranca.dataPagamento)}</span>
          </div>
        )}

        {mensagemSucesso && (
          <div className={styles.sucessoRegistro}>
            <Icon name="check" size={16} />
            <span>Pagamento registrado com sucesso!</span>
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

          <Button
            variant="primary"
            className={styles.botaoRegistrar}
            disabled={jaPaga || cobranca.status === 'CANCELADA' || salvando}
            onClick={() => setModalRegistrarAberto(true)}
          >
            {salvando
              ? 'Registrando…'
              : jaPaga
                ? 'Pagamento já registrado'
                : 'Registrar pagamento'}
          </Button>
        </div>
      </aside>

      {modalRegistrarAberto && (
        <ModalRegistrarPagamento
          cobranca={cobranca}
          onFechar={() => setModalRegistrarAberto(false)}
          onConfirmar={handleConfirmarRegistro}
        />
      )}
    </>
  )
}
