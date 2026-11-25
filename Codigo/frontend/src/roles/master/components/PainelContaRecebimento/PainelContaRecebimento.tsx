import { Badge } from '../../../../components/ui/Badge/Badge'
import { Button } from '../../../../components/ui/Button/Button'
import { Linhas, Painel } from '../../../../components/layout/Dashboard/Dashboard'
import {
  ROTULOS_SITUACAO,
  ROTULOS_STATUS_CONTA,
  ROTULOS_TIPO_EMPRESA,
  type ContaRecebimento,
  type SituacaoCadastral,
  type StatusContaRecebimento,
} from '../../../../types/financeiro/contaRecebimento'
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
  REJEITADA: 'O provedor rejeitou o cadastro da conta. Fale com o suporte do Eduka.',
  CREDENCIAL_INDISPONIVEL: 'A conta existe no provedor, mas o Eduka não tem acesso a ela. Fale com o suporte.',
}

function situacao(valor: SituacaoCadastral | null): string {
  return valor ? ROTULOS_SITUACAO[valor] : '—'
}

/** Rótulo do botão que abre o cadastro: a primeira vez, ou de novo depois de uma recusa ou sem resposta. */
function rotuloEnvio(status: StatusContaRecebimento): string {
  if (status === 'NAO_INICIADA') return 'Ativar recebimentos'
  if (status === 'ERRO_CRIACAO') return 'Corrigir e reenviar'
  return 'Enviar de novo'
}

type Etapa = { titulo: string; feita: boolean }

/** Etapas da ativação (IU23): dados enviados, em análise no Asaas e aprovada, com as datas. */
function etapasDaAtivacao(conta: ContaRecebimento): Etapa[] {
  const enviada = conta.solicitadaEm !== null
  const emAnalise = conta.status === 'AGUARDANDO_APROVACAO' || conta.status === 'APROVADA'
  const aprovada = conta.status === 'APROVADA'
  return [
    { titulo: enviada ? `Dados enviados · ${dataHora(conta.solicitadaEm)}` : 'Dados enviados', feita: enviada },
    { titulo: 'Em análise no Asaas', feita: emAnalise },
    {
      titulo: aprovada ? `Aprovada · ${dataHora(conta.aprovadaEm)}` : 'Aprovada: a escola pode receber',
      feita: aprovada,
    },
  ]
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
  /** Atalho de teste do Sandbox; sem ele o botão não aparece. */
  onAprovarNoSandbox?: () => void
}

/** Situação da conta de recebimento da escola, com Ativar e Sincronizar conforme o backend libera. */
export function PainelContaRecebimento({
  conta,
  sincronizando,
  erroSincronizar,
  onAtivar,
  onSincronizar,
  onAprovarNoSandbox,
}: PainelContaRecebimentoProps) {
  const acao = (
    <div className={styles.acoes}>
      {onAprovarNoSandbox && conta.status === 'AGUARDANDO_APROVACAO' && (
        <Button size="sm" variant="accent" icon="check" onClick={onAprovarNoSandbox} disabled={sincronizando}>
          Aprovar no Sandbox
        </Button>
      )}
      {conta.podeSincronizar && (
        <Button size="sm" variant="outline" icon="clock" onClick={onSincronizar} disabled={sincronizando}>
          {sincronizando ? 'Sincronizando…' : 'Sincronizar agora'}
        </Button>
      )}
      {conta.podeAtivar && (
        <Button size="sm" variant="primary" icon="card" onClick={onAtivar}>
          {rotuloEnvio(conta.status)}
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
      {conta.ultimoErro && conta.status === 'ERRO_CRIACAO' ? (
        <div className={styles.recusa} role="alert">
          <strong>O Asaas recusou o cadastro</strong>
          <span>{conta.ultimoErro}</span>
          <span>Use Corrigir e reenviar para ajustar os dados e enviar de novo.</span>
        </div>
      ) : (
        conta.ultimoErro &&
        conta.status !== 'APROVADA' && (
          <p className={styles.alerta} role="alert">
            {conta.ultimoErro}
          </p>
        )
      )}
      {erroSincronizar && (
        <p className={styles.alerta} role="alert">
          {erroSincronizar}
        </p>
      )}
      {criada && (
        <ol className={styles.etapas} aria-label="Etapas da ativação">
          {etapasDaAtivacao(conta).map((etapa, indice) => (
            <li key={etapa.titulo} className={etapa.feita ? styles.feita : undefined}>
              <span className={styles.marcador} aria-hidden="true">
                {etapa.feita ? '✓' : indice + 1}
              </span>
              {etapa.titulo}
            </li>
          ))}
        </ol>
      )}
      {criada && (
        <Linhas
          itens={[
            { rotulo: 'Razão social', valor: conta.razaoSocial ?? '—' },
            { rotulo: 'CNPJ', valor: conta.cnpj ? formatarCnpjCpf(conta.cnpj) : '—' },
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
