import { useState } from 'react'
import { useNavigate } from 'react-router'
import { Badge } from '../../../../../components/ui/Badge/Badge'
import { Button } from '../../../../../components/ui/Button/Button'
import { ModalAlerta, ModalAviso } from '../../../../../components/modal/Modal/Modal'
import { Pilulas } from '../../../../../components/financeiro/PainelFinanceiro/PainelFinanceiro'
import { Paginacao } from '../../../../../components/ui/Paginacao/Paginacao'
import { RiscoDoAluno } from '../../../../../components/financeiro/RiscoAtraso/RiscoAtraso'
import { StatCard, StatCards } from '../../../../../components/ui/StatCard/StatCard'
import { useDetalhe } from '../../../../../hooks/useDetalhe'
import { usePaginacao } from '../../../../../hooks/usePaginacao'
import { useRotasFinanceiras } from '../../../../../routes/areas'
import { buscarHistoricoFinanceiroDoAluno, buscarRiscoAtrasoDoAluno } from '../../../../../services/pessoas/alunoService'
import {
  ROTULOS_FORMA_PAGAMENTO,
  rotuloBaixaManual,
  type CobrancaDoHistorico,
  type HistoricoFinanceiro,
  type StatusCobrancaAluno,
} from '../../../../../types/financeiro/pagamentoAluno'
import { formatarMoeda, plural } from '../../../../../utils/formatters'
import { formatarData } from '../../../../../utils/formatacao'
import styles from './FinanceiroAluno.module.css'

/** Situação de cada lançamento, com os nomes da tela de Cobranças (IU20). */
const SITUACAO: Record<StatusCobrancaAluno, { rotulo: string; tom: 'success' | 'danger' | 'neutral' | 'warning' }> = {
  PENDENTE: { rotulo: 'A pagar', tom: 'warning' },
  EM_ATRASO: { rotulo: 'Em atraso', tom: 'danger' },
  PAGA: { rotulo: 'Paga', tom: 'success' },
  CANCELADA: { rotulo: 'Cancelada', tom: 'neutral' },
}

type Filtro = 'TODAS' | 'A_PAGAR' | 'PAGAS' | 'CANCELADAS'

const FILTROS: { valor: Filtro; rotulo: string }[] = [
  { valor: 'TODAS', rotulo: 'Todas' },
  { valor: 'A_PAGAR', rotulo: 'A pagar' },
  { valor: 'PAGAS', rotulo: 'Pagas' },
  { valor: 'CANCELADAS', rotulo: 'Canceladas' },
]

/** "A pagar" junta a que ainda vai vencer e a vencida: as duas esperam pagamento. */
function noFiltro(cobranca: CobrancaDoHistorico, filtro: Filtro): boolean {
  switch (filtro) {
    case 'TODAS':
      return true
    case 'A_PAGAR':
      return cobranca.status === 'PENDENTE' || cobranca.status === 'EM_ATRASO'
    case 'PAGAS':
      return cobranca.status === 'PAGA'
    case 'CANCELADAS':
      return cobranca.status === 'CANCELADA'
  }
}

/** Quando e como foi paga: pagamento online (com a forma) ou baixa manual. */
function descreverPagamento(c: CobrancaDoHistorico): string | null {
  if (c.pagamento) {
    const como = c.pagamento.forma
      ? ROTULOS_FORMA_PAGAMENTO[c.pagamento.forma]
      : rotuloBaixaManual('Baixa manual', c.pagamento.formaManual)
    const estornado =
      c.pagamento.valorEstornado > 0 ? ` · ${formatarMoeda(c.pagamento.valorEstornado)} estornados` : ''
    return `${formatarData(c.pagamento.dataPagamento)} · ${como}${estornado}`
  }
  if (c.pagaEm) {
    return `${new Date(c.pagaEm).toLocaleDateString('pt-BR')} · Baixa manual`
  }
  return null
}

/**
 * Números dos cards. "Total cobrado" deixa as canceladas de fora: elas não
 * são devidas, e assim o total fecha com o já pago mais o em aberto (sem
 * contar estornos). O resto vem pronto do resumo do backend.
 */
function indicadores({ resumo, cobrancas }: HistoricoFinanceiro) {
  const cobradas = cobrancas.filter((c) => c.status !== 'CANCELADA')
  // ISO (yyyy-MM-dd) ordena como texto.
  const proximoVencimento = cobrancas
    .filter((c) => c.status === 'PENDENTE')
    .map((c) => c.dataVencimento)
    .sort()[0]
  return {
    totalCobrado: cobradas.reduce((soma, c) => soma + c.valor, 0),
    quantidadeCobrada: cobradas.length,
    proximoVencimento: proximoVencimento ?? null,
    emAtraso: resumo.quantidadeEmAtraso,
  }
}

function descricaoEmAberto(valorEmAberto: number, proximoVencimento: string | null): string {
  if (valorEmAberto === 0) return 'Nada em aberto'
  if (proximoVencimento) return `Vence em ${formatarData(proximoVencimento)}`
  return 'Tudo já venceu'
}

type FinanceiroAlunoProps = {
  alunoId: string
  /**
   * Nome do responsável financeiro; null quando o aluno não tem (as cobranças
   * saem em nome dele); undefined enquanto o cadastro carrega.
   */
  responsavelFinanceiro?: string | null
  /** Aluno anonimizado: só o histórico, sem cobrança nova. */
  anonimizado?: boolean
}

/**
 * Aba "Financeiro" do cadastro do aluno (IU18, RF17.3): risco de atraso com o
 * motivo (GET /alunos/{id}/financeiro/risco), cards de total cobrado, já pago,
 * em aberto e em atraso, e as cobranças com o pagamento de cada uma
 * (GET /alunos/{id}/financeiro), as mesmas que o aluno vê em Pagamentos (IU17).
 * Só leitura: lançar, editar e dar baixa ficam em Cobranças, aberta já
 * filtrada pelo aluno, e em Nova cobrança, com o aluno escolhido.
 */
export function FinanceiroAluno({ alunoId, responsavelFinanceiro, anonimizado = false }: FinanceiroAlunoProps) {
  const navigate = useNavigate()
  const rotas = useRotasFinanceiras()
  const { dados, carregando, erro } = useDetalhe(buscarHistoricoFinanceiroDoAluno, alunoId)
  // O risco é um extra: se falhar, o histórico aparece sem ele.
  const { dados: risco } = useDetalhe(buscarRiscoAtrasoDoAluno, alunoId)
  const [filtro, setFiltro] = useState<Filtro>('TODAS')
  const visiveis = dados ? dados.cobrancas.filter((c) => noFiltro(c, filtro)) : []
  const { paginacao, topo } = usePaginacao<CobrancaDoHistorico, HTMLElement>(visiveis, { reiniciarCom: filtro })

  if (carregando) return <ModalAviso>Carregando o histórico financeiro…</ModalAviso>
  if (erro) return <ModalAlerta>{erro}</ModalAlerta>
  if (!dados) return null

  const { resumo, cobrancas } = dados
  const { totalCobrado, quantidadeCobrada, proximoVencimento, emAtraso } = indicadores(dados)
  const doAluno = new URLSearchParams({ aluno: alunoId }).toString()

  return (
    <div className={styles.conteudo}>
      {risco && <RiscoDoAluno risco={risco} />}

      <StatCards rotulo="Resumo financeiro do aluno">
        <StatCard
          label="Total cobrado"
          value={formatarMoeda(totalCobrado)}
          descricao={`${quantidadeCobrada} ${plural(quantidadeCobrada, 'cobrança gerada', 'cobranças geradas')}`}
        />
        <StatCard
          label="Já pago"
          value={formatarMoeda(resumo.valorPago)}
          descricao={`${resumo.quantidadePaga} ${plural(resumo.quantidadePaga, 'pagamento confirmado', 'pagamentos confirmados')}`}
          positivo={resumo.valorPago > 0}
        />
        <StatCard
          label="Em aberto"
          value={formatarMoeda(resumo.valorEmAberto)}
          descricao={descricaoEmAberto(resumo.valorEmAberto, proximoVencimento)}
          destaque
        />
        <StatCard
          label="Em atraso"
          value={formatarMoeda(resumo.valorEmAtraso)}
          descricao={emAtraso === 0 ? 'Aluno em dia' : `${emAtraso} ${plural(emAtraso, 'cobrança vencida', 'cobranças vencidas')}`}
          alerta={resumo.valorEmAtraso > 0}
          positivo={resumo.valorEmAtraso === 0}
        />
      </StatCards>

      <section ref={topo} className={styles.lancamentos} aria-labelledby="titulo-lancamentos-aluno">
        <div className={styles.cabecalho}>
          <h3 id="titulo-lancamentos-aluno" className={styles.titulo}>
            Cobranças e pagamentos
          </h3>
          {cobrancas.length > 0 && (
            <Pilulas<Filtro> rotulo="Filtrar por situação" opcoes={FILTROS} valor={filtro} onChange={setFiltro} />
          )}
        </div>

        {cobrancas.length === 0 ? (
          <ModalAviso>Nenhuma cobrança lançada para este aluno.</ModalAviso>
        ) : visiveis.length === 0 ? (
          <ModalAviso>Nenhuma cobrança nesta situação.</ModalAviso>
        ) : (
          <div className={styles.rolagem}>
            <table className={styles.tabela}>
              <caption className={styles.somenteLeitor}>
                Cobranças do aluno, da mais recente para a mais antiga pelo vencimento
              </caption>
              <thead>
                <tr>
                  <th scope="col">Referência</th>
                  <th scope="col">Vencimento</th>
                  <th scope="col">Pagamento</th>
                  <th scope="col" className={styles.numero}>
                    Valor
                  </th>
                  <th scope="col">Situação</th>
                </tr>
              </thead>
              <tbody>
                {paginacao.itensDaPagina.map((c) => (
                  <tr key={c.id}>
                    <td className={styles.colPrincipal}>
                      <span className={styles.descricao}>{c.descricao}</span>
                      {c.turmaNome && <span className={styles.secundario}>{c.turmaNome}</span>}
                    </td>
                    <td data-rotulo="Vencimento" className={styles.data}>
                      {formatarData(c.dataVencimento)}
                    </td>
                    <td data-rotulo="Pagamento" className={styles.secundario}>
                      {descreverPagamento(c) ?? '—'}
                    </td>
                    <td data-rotulo="Valor" className={styles.numero}>
                      {formatarMoeda(c.valor)}
                    </td>
                    <td data-rotulo="Situação">
                      <Badge tone={SITUACAO[c.status].tom}>{SITUACAO[c.status].rotulo}</Badge>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
        <Paginacao paginacao={paginacao} rotulo="Páginas das cobranças do aluno" />
      </section>

      <div className={styles.rodape}>
        <p className={styles.total}>
          {visiveis.length} de {cobrancas.length} {plural(cobrancas.length, 'lançamento', 'lançamentos')}
          {responsavelFinanceiro !== undefined &&
            (responsavelFinanceiro
              ? ` · responsável financeiro: ${responsavelFinanceiro}`
              : ' · sem responsável financeiro: as cobranças saem em nome do aluno')}
        </p>
        <div className={styles.comandos}>
          <Button icon="card" onClick={() => navigate(`${rotas.cobrancas}?${doAluno}`)}>
            Ver na tela de cobranças
          </Button>
          {!anonimizado && (
            <Button variant="primary" icon="plus" onClick={() => navigate(`${rotas.novaCobranca}?${doAluno}`)}>
              Nova cobrança
            </Button>
          )}
        </div>
      </div>
    </div>
  )
}
