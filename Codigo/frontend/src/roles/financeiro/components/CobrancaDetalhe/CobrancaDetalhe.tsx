import { Button } from '../../../../components/Button/Button'
import { Icon } from '../../../../components/Icon/Icon'
import type { Cobranca } from '../../../../types/cobranca'
import { formatarMoeda } from '../../../../utils/formatters'
import styles from './CobrancaDetalhe.module.css'

/** Resultado do atalho de Sandbox, mostrado abaixo do botão. */
export type MensagemSandbox = { texto: string; erro: boolean }

type CobrancaDetalheProps = {
  cobranca: Cobranca
  onVerBoleto: (cobranca: Cobranca) => void
  /** Atalho de teste do Sandbox; sem ele o botão não aparece. */
  onSimularPagamento?: (cobranca: Cobranca) => void
  simulandoPagamento?: boolean
  mensagemSandbox?: MensagemSandbox | null
}

const DATA_DA_ESCOLA = new Intl.DateTimeFormat('pt-BR', { timeZone: 'America/Sao_Paulo' })

/** "2026-10-10" -> "10/10/2026"; data e hora ISO viram a data no fuso da escola. */
function formatarDataBr(iso: string): string {
  if (/^\d{4}-\d{2}-\d{2}$/.test(iso)) {
    const [ano, mes, dia] = iso.split('-')
    return `${dia}/${mes}/${ano}`
  }
  const data = new Date(iso)
  return Number.isNaN(data.getTime()) ? iso : DATA_DA_ESCOLA.format(data)
}

const ROTULOS_FORMA: Record<string, string> = {
  PIX: 'PIX',
  BOLETO: 'Boleto',
  CARTAO: 'Cartão',
  DINHEIRO: 'Dinheiro',
  TRANSFERENCIA: 'Transferência',
}

/** Detalhes da cobrança aberta na lista: dados do aluno, datas, pagamento e ações. */
export function CobrancaDetalhe({
  cobranca,
  onVerBoleto,
  onSimularPagamento,
  simulandoPagamento = false,
  mensagemSandbox = null,
}: CobrancaDetalheProps) {
  const pagamento = cobranca.status === 'PAGA' ? cobranca.pagamento : null
  const emAberto = cobranca.status === 'PENDENTE' || cobranca.status === 'EM_ATRASO'

  const campos: { rotulo: string; valor: string }[] = [
    { rotulo: 'Matrícula', valor: cobranca.alunoNumeroMatricula ?? '—' },
    { rotulo: 'Turma', valor: cobranca.turmaNome ?? '—' },
    { rotulo: 'Responsável financeiro', valor: cobranca.responsavelNome ?? 'O próprio aluno' },
    { rotulo: 'Vencimento', valor: formatarDataBr(cobranca.dataVencimento) },
    { rotulo: 'Lançada em', valor: formatarDataBr(cobranca.criadoEm) },
  ]
  if ((cobranca.status === 'CANCELADA' || cobranca.pagaAposCancelamento) && cobranca.canceladaEm) {
    campos.push({ rotulo: 'Cancelada em', valor: formatarDataBr(cobranca.canceladaEm) })
  }

  return (
    <div className={styles.detalhe}>
      <dl className={styles.campos}>
        {campos.map((campo) => (
          <div key={campo.rotulo} className={styles.campo}>
            <dt>{campo.rotulo}</dt>
            <dd>{campo.valor}</dd>
          </div>
        ))}
      </dl>

      {cobranca.pagaAposCancelamento && (
        <div className={styles.avisoAposCancelamento} role="status">
          <Icon name="alert" size={16} />
          <span>
            O pagamento chegou depois do cancelamento e a cobrança foi marcada como paga. Combine com o
            pagador o que fazer com o valor.
          </span>
        </div>
      )}

      {pagamento && (
        <div className={styles.infoPagamento}>
          <Icon name="check" size={16} />
          <span>
            Pago em {formatarDataBr(pagamento.dataPagamento)} · {formatarMoeda(pagamento.valorPago)}
            {pagamento.forma && ` · ${ROTULOS_FORMA[pagamento.forma] ?? pagamento.forma}`}
            {pagamento.origem === 'MANUAL' ? ' · baixa manual da escola' : ' · online'}
            {pagamento.valorEstornado > 0 && ` · ${formatarMoeda(pagamento.valorEstornado)} estornados`}
          </span>
        </div>
      )}

      <div className={styles.acoes}>
        <Button variant="outline" icon="fileText" onClick={() => onVerBoleto(cobranca)}>
          Ver boleto anexado
        </Button>
        {onSimularPagamento && emAberto && (
          <Button
            variant="accent"
            icon="check"
            onClick={() => onSimularPagamento(cobranca)}
            disabled={simulandoPagamento}
          >
            {simulandoPagamento ? 'Simulando…' : 'Simular pagamento no Sandbox'}
          </Button>
        )}
      </div>

      {mensagemSandbox && (
        <p
          className={mensagemSandbox.erro ? styles.erroSandbox : styles.avisoSandbox}
          role={mensagemSandbox.erro ? 'alert' : 'status'}
        >
          {mensagemSandbox.texto}
        </p>
      )}
    </div>
  )
}
