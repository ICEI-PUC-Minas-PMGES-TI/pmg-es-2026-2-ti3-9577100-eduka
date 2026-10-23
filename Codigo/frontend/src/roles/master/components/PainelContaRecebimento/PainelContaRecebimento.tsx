import { Badge } from '../../../../components/Badge/Badge'
import { Button } from '../../../../components/Button/Button'
import { Linhas, Painel } from '../../../../components/Dashboard/Dashboard'
import {
  ROTULOS_SITUACAO,
  ROTULOS_STATUS_CONTA,
  ROTULOS_TIPO_EMPRESA,
  type ContaRecebimento,
  type SituacaoCadastral,
  type StatusContaRecebimento,
} from '../../../../types/contaRecebimento'
import { formatarCnpjCpf } from '../../../../utils/formatters'
import styles from './PainelContaRecebimento.module.css'

type Tom = 'success' | 'danger' | 'neutral' | 'primary' | 'warning'

const TOM_STATUS: Record<StatusContaRecebimento, Tom> = {
  NAO_INICIADA: 'neutral',
  CRIANDO: 'primary',
  VERIFICACAO_PENDENTE: 'warning',
  ERRO_CRIACAO: 'danger',
  AGUARDANDO_APROVACAO: 'warning',
  APROVADA: 'success',
  REJEITADA: 'danger',
  CREDENCIAL_INDISPONIVEL: 'danger',
}

const EXPLICACAO: Record<StatusContaRecebimento, string> = {
  NAO_INICIADA: 'Ative para os alunos pagarem as mensalidades com PIX ou boleto pelo portal.',
  CRIANDO: 'A conta está sendo criada no provedor de pagamento.',
  VERIFICACAO_PENDENTE:
    'O provedor não confirmou a criação da conta. Envie o cadastro de novo: a conta é conferida antes de criar outra.',
  ERRO_CRIACAO: 'O provedor recusou o cadastro. Corrija os dados e envie de novo.',
  AGUARDANDO_APROVACAO:
    'Conta criada. O provedor está analisando o cadastro; a situação é atualizada sozinha, ou use Sincronizar.',
  APROVADA: 'Os alunos já podem pagar online.',
  REJEITADA: 'O provedor rejeitou o cadastro da conta. Fale com o suporte do Edúka.',
  CREDENCIAL_INDISPONIVEL: 'A conta existe no provedor, mas o Edúka não tem acesso a ela. Fale com o suporte.',
}

function situacao(valor: SituacaoCadastral | null): string {
  return valor ? ROTULOS_SITUACAO[valor] : '—'
}

function dataHora(iso: string | null): string {
  return iso ? new Date(iso).toLocaleString('pt-BR', { dateStyle: 'short', timeStyle: 'short' }) : '—'
}

type PainelContaRecebimentoProps = {
  conta: ContaRecebimento
  sincronizando: boolean
  erroSincronizar: string | null
  onAtivar: () => void
  onSincronizar: () => void
}

/** Situação da conta de recebimento da escola, com Ativar e Sincronizar conforme o backend libera. */
export function PainelContaRecebimento({
  conta,
  sincronizando,
  erroSincronizar,
  onAtivar,
  onSincronizar,
}: PainelContaRecebimentoProps) {
  const acao = (
    <div className={styles.acoes}>
      {conta.podeSincronizar && (
        <Button size="sm" variant="outline" icon="clock" onClick={onSincronizar} disabled={sincronizando}>
          {sincronizando ? 'Sincronizando…' : 'Sincronizar'}
        </Button>
      )}
      {conta.podeAtivar && (
        <Button size="sm" variant="primary" icon="card" onClick={onAtivar}>
          {conta.status === 'NAO_INICIADA' ? 'Ativar recebimentos' : 'Enviar de novo'}
        </Button>
      )}
    </div>
  )

  const criada = conta.status !== 'NAO_INICIADA'

  return (
    <Painel titulo="Recebimentos online" descricao={EXPLICACAO[conta.status]} acao={acao}>
      <p>
        <Badge tone={TOM_STATUS[conta.status]}>{ROTULOS_STATUS_CONTA[conta.status]}</Badge>
      </p>
      {conta.ultimoErro && conta.status !== 'APROVADA' && (
        <p className={styles.alerta} role="alert">
          {conta.ultimoErro}
        </p>
      )}
      {erroSincronizar && (
        <p className={styles.alerta} role="alert">
          {erroSincronizar}
        </p>
      )}
      {criada && (
        <Linhas
          itens={[
            { rotulo: 'Razão social', valor: conta.razaoSocial ?? '—' },
            { rotulo: 'CNPJ', valor: formatarCnpjCpf(conta.cnpj) },
            { rotulo: 'Tipo de empresa', valor: conta.tipoEmpresa ? ROTULOS_TIPO_EMPRESA[conta.tipoEmpresa] : '—' },
            {
              rotulo: 'Conta bancária',
              valor: conta.bancoCodigo ? `Banco ${conta.bancoCodigo} · final ${conta.contaFinal}` : '—',
            },
            ...(conta.situacao
              ? [
                  { rotulo: 'Documentação', valor: situacao(conta.situacao.documentacao) },
                  { rotulo: 'Dados comerciais', valor: situacao(conta.situacao.dadosComerciais) },
                  { rotulo: 'Conta bancária (análise)', valor: situacao(conta.situacao.contaBancaria) },
                ]
              : []),
            { rotulo: 'Solicitada em', valor: dataHora(conta.solicitadaEm) },
            { rotulo: 'Aprovada em', valor: dataHora(conta.aprovadaEm) },
            { rotulo: 'Última sincronização', valor: dataHora(conta.sincronizadaEm) },
          ]}
        />
      )}
    </Painel>
  )
}
