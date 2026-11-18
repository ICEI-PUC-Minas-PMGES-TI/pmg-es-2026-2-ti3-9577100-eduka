import { Button } from '../../../../components/ui/Button/Button'
import { Icon } from '../../../../components/ui/Icon/Icon'
import type { Cobranca } from '../../../../types/financeiro/cobranca'
import { ROTULOS_FORMA_PAGAMENTO_MANUAL } from '../../../../types/financeiro/pagamentoAluno'
import { formatarMoeda } from '../../../../utils/formatters'
import styles from './CobrancaDetalhe.module.css'

/** Resultado do atalho de Sandbox, mostrado abaixo do botão. */
export type MensagemSandbox = { texto: string; erro: boolean }

type CobrancaDetalheProps = {
  cobranca: Cobranca
  onEditar: (cobranca: Cobranca) => void
  onCancelar: (cobranca: Cobranca) => void
  /** Baixa manual (UC12): pagamento recebido fora do Eduka. */
  onRegistrarPagamento: (cobranca: Cobranca) => void
  onVerComprovante: (cobranca: Cobranca) => void
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

type Etapa = { titulo: string; situacao: 'feita' | 'atual' | 'futura' }

/**
 * Andamento do pagamento (IU20): cobrança criada, aluno clicou em Pagar
 * (forma e data) e confirmação do banco. Cancelada, a última etapa é o
 * cancelamento.
 */
function etapasDoPagamento(cobranca: Cobranca): Etapa[] {
  const clicou = cobranca.pagamentoIniciadoEm
  const paga = cobranca.status === 'PAGA'
  const cancelada = cobranca.status === 'CANCELADA'
  const etapas: Etapa[] = [{ titulo: `Cobrança criada · ${formatarDataBr(cobranca.criadoEm)}`, situacao: 'feita' }]

  if (clicou) {
    const forma = cobranca.formaPagamento ? `${ROTULOS_FORMA[cobranca.formaPagamento]} · ` : ''
    etapas.push({ titulo: `Aluno clicou em Pagar · ${forma}${formatarDataBr(clicou)}`, situacao: 'feita' })
  } else if (!paga) {
    etapas.push({ titulo: 'Aguardando pagamento', situacao: cancelada ? 'futura' : 'atual' })
  }

  if (paga) {
    const em = cobranca.pagamento?.dataPagamento ?? cobranca.pagaEm
    etapas.push({ titulo: `Pagamento confirmado${em ? ` · ${formatarDataBr(em)}` : ''}`, situacao: 'feita' })
  } else if (cancelada) {
    const em = cobranca.canceladaEm ? ` · ${formatarDataBr(cobranca.canceladaEm)}` : ''
    etapas.push({ titulo: `Cobrança cancelada${em}`, situacao: 'feita' })
  } else {
    etapas.push({ titulo: 'Aguardando a confirmação do banco', situacao: clicou ? 'atual' : 'futura' })
  }
  return etapas
}

/** Detalhes da cobrança aberta na lista: dados do aluno, andamento do pagamento e ações. */
export function CobrancaDetalhe({
  cobranca,
  onEditar,
  onCancelar,
  onRegistrarPagamento,
  onVerComprovante,
  onSimularPagamento,
  simulandoPagamento = false,
  mensagemSandbox = null,
}: CobrancaDetalheProps) {
  const pagamento = cobranca.status === 'PAGA' ? cobranca.pagamento : null
  const emAberto = cobranca.status === 'PENDENTE' || cobranca.status === 'EM_ATRASO'
  const temComprovante = pagamento?.origem === 'ONLINE' && pagamento.comprovanteUrl !== null

  const campos: { rotulo: string; valor: string }[] = [
    { rotulo: 'Matrícula', valor: cobranca.alunoNumeroMatricula ?? '—' },
    { rotulo: 'Turma', valor: cobranca.turmaNome ?? '—' },
    {
      rotulo: 'Quem paga',
      valor: cobranca.responsavelNome ? `${cobranca.responsavelNome} (responsável)` : 'O próprio aluno',
    },
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

      <section aria-label="Andamento do pagamento">
        <h3 className={styles.tituloAndamento}>Andamento do pagamento</h3>
        <ol className={styles.andamento}>
          {etapasDoPagamento(cobranca).map((etapa, indice) => (
            <li key={etapa.titulo} className={styles[etapa.situacao]}>
              <span className={styles.marcador} aria-hidden="true">
                {etapa.situacao === 'feita' ? <Icon name="check" size={12} /> : indice + 1}
              </span>
              <span>{etapa.titulo}</span>
            </li>
          ))}
        </ol>
      </section>

      {emAberto && !cobranca.editavel && (
        <div className={styles.avisoBloqueio} role="note">
          <Icon name="lock" size={16} />
          <span>Edição bloqueada: o aluno já iniciou o pagamento. Ainda dá para cancelar enquanto não for paga.</span>
        </div>
      )}

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
            {pagamento.formaManual && ` · ${ROTULOS_FORMA_PAGAMENTO_MANUAL[pagamento.formaManual]}`}
            {pagamento.origem === 'MANUAL' ? ' · baixa manual da escola' : ' · online'}
            {pagamento.valorEstornado > 0 && ` · ${formatarMoeda(pagamento.valorEstornado)} estornados`}
          </span>
        </div>
      )}

      <div className={styles.acoes}>
        {emAberto && (
          <>
            <Button variant="accent" icon="dollarSign" onClick={() => onRegistrarPagamento(cobranca)}>
              Registrar pagamento
            </Button>
            <Button variant="outline" icon="edit" onClick={() => onEditar(cobranca)} disabled={!cobranca.editavel}>
              Editar
            </Button>
            <Button variant="dangerOutline" icon="trash" onClick={() => onCancelar(cobranca)}>
              Cancelar cobrança
            </Button>
          </>
        )}
        {temComprovante && (
          <Button variant="outline" icon="eye" onClick={() => onVerComprovante(cobranca)}>
            Ver comprovante
          </Button>
        )}
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
