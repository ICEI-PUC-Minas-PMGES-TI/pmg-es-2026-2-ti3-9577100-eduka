import { useCallback, useState } from 'react'
import { Badge } from '../../../../components/Badge/Badge'
import { Button } from '../../../../components/Button/Button'
import { CampoPeriodo, type Periodo } from '../../../../components/CampoPeriodo/CampoPeriodo'
import { ModalComprovante } from '../../../../components/Comprovante/ModalComprovante'
import { Paginacao } from '../../../../components/Paginacao/Paginacao'
import { StatCard, StatCards } from '../../../../components/StatCard/StatCard'
import { usePaginacao } from '../../../../hooks/usePaginacao'
import { buscarComprovante, buscarHistoricoFinanceiro } from '../../../../services/pagamentoAlunoService'
import {
  podePagar,
  ROTULOS_FORMA_PAGAMENTO,
  ROTULOS_STATUS_COBRANCA_ALUNO,
  type CobrancaDoHistorico,
  type HistoricoFinanceiro,
  type StatusCobrancaAluno,
} from '../../../../types/pagamentoAluno'
import { formatarMoeda, hojeIso, plural } from '../../../../utils/formatters'
import { formatarData } from '../../../secretaria/components/PainelSecretaria/formatacao'
import { ModalPagamento } from '../../components/ModalPagamento/ModalPagamento'
import { useDadosDoAluno } from '../../hooks/useDadosDoAluno'
import { AlunoLayout } from '../../layouts/AlunoLayout'
import styles from './Pagamentos.module.css'

const TOM_STATUS: Record<StatusCobrancaAluno, 'success' | 'danger' | 'neutral' | 'warning'> = {
  PENDENTE: 'warning',
  EM_ATRASO: 'danger',
  PAGA: 'success',
  CANCELADA: 'neutral',
}

const UM_DIA_MS = 24 * 60 * 60 * 1000

/** "vence hoje", "vence em 3 dias", "venceu há 2 dias" (datas ISO, sem fuso). */
function prazo(vencimento: string): string {
  const dias = Math.round((Date.parse(vencimento) - Date.parse(hojeIso())) / UM_DIA_MS)
  if (dias === 0) return 'Vence hoje'
  if (dias > 0) return `Vence em ${dias} ${plural(dias, 'dia', 'dias')}`
  return `Venceu há ${-dias} ${plural(-dias, 'dia', 'dias')}`
}

/** Como a cobrança foi paga: pagamento online registrado, ou só a data da baixa feita pela escola. */
function descreverPagamento(c: CobrancaDoHistorico): string | null {
  if (c.pagamento) {
    const como = c.pagamento.forma ? ROTULOS_FORMA_PAGAMENTO[c.pagamento.forma] : 'Baixa pela escola'
    return `${como} em ${formatarData(c.pagamento.dataPagamento)}`
  }
  if (c.pagaEm) return `Baixa pela escola em ${new Date(c.pagaEm).toLocaleDateString('pt-BR')}`
  return null
}

type FiltroHistorico = 'TODAS' | 'EM_ABERTO' | 'PAGAS' | 'CANCELADAS'

const FILTROS: FiltroHistorico[] = ['TODAS', 'EM_ABERTO', 'PAGAS', 'CANCELADAS']

const ROTULOS_FILTRO: Record<FiltroHistorico, string> = {
  TODAS: 'Todas',
  EM_ABERTO: 'Em aberto',
  PAGAS: 'Pagas',
  CANCELADAS: 'Canceladas',
}

function passaNaSituacao(cobranca: CobrancaDoHistorico, filtro: FiltroHistorico): boolean {
  switch (filtro) {
    case 'TODAS':
      return true
    case 'EM_ABERTO':
      return podePagar(cobranca.status)
    case 'PAGAS':
      return cobranca.status === 'PAGA'
    case 'CANCELADAS':
      return cobranca.status === 'CANCELADA'
  }
}

/** Datas ISO: a comparação de texto já segue a ordem do calendário. */
function passaNoPeriodo(cobranca: CobrancaDoHistorico, periodo: Periodo | null): boolean {
  return !periodo || (cobranca.dataVencimento >= periodo.de && cobranca.dataVencimento <= periodo.ate)
}

/**
 * Pagamentos do aluno (IU17): a próxima mensalidade em destaque, os cards de
 * resumo e o histórico de todas as cobranças, com filtros de situação e de
 * período de vencimento. Em aberto tem Pagar (escolhe PIX ou boleto e vê o QR
 * Code ou a linha digitável no Eduka); paga online tem Ver comprovante.
 */
export function Pagamentos() {
  const { dados: historico, carregando, erro, recarregar } = useDadosDoAluno<HistoricoFinanceiro | null>(
    buscarHistoricoFinanceiro,
    null,
  )
  /** Cobrança com o modal de pagamento aberto. */
  const [pagando, setPagando] = useState<CobrancaDoHistorico | null>(null)
  const [pagouAlguma, setPagouAlguma] = useState(false)
  /** `comprovanteUrl` do pagamento cujo comprovante está aberto. */
  const [comprovanteDe, setComprovanteDe] = useState<string | null>(null)
  const [filtro, setFiltro] = useState<FiltroHistorico>('TODAS')
  const [periodo, setPeriodo] = useState<Periodo | null>(null)

  const cobrancas = historico?.cobrancas ?? []
  // Em aberto: a que vence (ou venceu) primeiro vem antes. A primeira é a "próxima".
  const emAberto = cobrancas
    .filter((c) => podePagar(c.status))
    .sort((a, b) => a.dataVencimento.localeCompare(b.dataVencimento))
  const proxima = emAberto[0]
  // Os contadores das pílulas respeitam o período: mostram o que aparece ao clicar.
  // O histórico já vem do vencimento mais recente para o mais antigo.
  const noPeriodo = (historico?.cobrancas ?? []).filter((c) => passaNoPeriodo(c, periodo))
  const filtradas = noPeriodo.filter((c) => passaNaSituacao(c, filtro))
  const { paginacao: paginasHistorico, topo: topoHistorico } = usePaginacao<CobrancaDoHistorico, HTMLElement>(
    filtradas,
    { reiniciarCom: `${filtro}|${periodo?.de ?? ''}|${periodo?.ate ?? ''}` },
  )

  const buscarComprovanteAberto = useCallback(() => buscarComprovante(comprovanteDe ?? ''), [comprovanteDe])

  function pagar(cobranca: CobrancaDoHistorico) {
    setPagando(cobranca)
  }

  function fecharPagamento() {
    setPagando(null)
    // A lista só muda se algum pagamento caiu enquanto o modal estava aberto.
    if (pagouAlguma) {
      setPagouAlguma(false)
      recarregar()
    }
  }

  return (
    <AlunoLayout titulo="Pagamentos" subtitulo="Suas mensalidades e taxas. Pague com PIX ou boleto sem sair do Eduka.">
      {carregando && <div className={styles.mensagem} role="status">Carregando seus pagamentos…</div>}

      {!carregando && erro && (
        <div className={styles.mensagem} role="alert">
          <span>{erro}</span>
          <Button size="sm" onClick={recarregar}>
            Tentar novamente
          </Button>
        </div>
      )}

      {!carregando && !erro && historico && (
        <>
          {proxima ? (
            <ProximaMensalidade cobranca={proxima} onPagar={pagar} />
          ) : (
            cobrancas.length > 0 && (
              <div className={styles.mensagem}>
                <strong>Tudo em dia!</strong>
                <span>Você não tem nenhuma cobrança em aberto.</span>
              </div>
            )
          )}

          {cobrancas.length > 0 && (
            <StatCards rotulo="Resumo financeiro">
              <StatCard
                label="Total em aberto"
                value={formatarMoeda(historico.resumo.valorEmAberto)}
                descricao={plural(emAberto.length, '1 cobrança', `${emAberto.length} cobranças`)}
              />
              <StatCard
                label="Em atraso"
                value={formatarMoeda(historico.resumo.valorEmAtraso)}
                descricao={historico.resumo.quantidadeEmAtraso === 0 ? 'Nada vencido' : 'Já passou do vencimento'}
                alerta={historico.resumo.quantidadeEmAtraso > 0}
              />
              <StatCard
                label="Total pago"
                value={formatarMoeda(historico.resumo.valorPago)}
                descricao={plural(
                  historico.resumo.quantidadePaga,
                  '1 cobrança paga',
                  `${historico.resumo.quantidadePaga} cobranças pagas`,
                )}
              />
            </StatCards>
          )}

          {cobrancas.length === 0 && (
            <div className={styles.mensagem}>
              <strong>Nenhuma cobrança por aqui.</strong>
              <span>Quando a escola lançar uma mensalidade ou taxa, ela aparece nesta tela.</span>
            </div>
          )}

          {cobrancas.length > 0 && (
            <section ref={topoHistorico} className={styles.cartao} aria-labelledby="titulo-historico">
              <h2 id="titulo-historico" className={styles.tituloSecao}>
                Histórico
              </h2>

              <div className={styles.filtros}>
                <div className={styles.pilulas} role="group" aria-label="Filtrar por situação">
                  {FILTROS.map((opcao) => (
                    <button
                      key={opcao}
                      type="button"
                      aria-pressed={filtro === opcao}
                      className={`${styles.pilula} ${filtro === opcao ? styles.pilulaAtiva : ''}`}
                      onClick={() => setFiltro(opcao)}
                    >
                      {ROTULOS_FILTRO[opcao]}
                      <span className={styles.contador}>{noPeriodo.filter((c) => passaNaSituacao(c, opcao)).length}</span>
                    </button>
                  ))}
                </div>
                <div className={styles.periodo}>
                  <CampoPeriodo
                    valor={periodo}
                    onChange={setPeriodo}
                    placeholder="Período de vencimento"
                    // Mensalidades futuras também têm vencimento: o calendário vai até o fim do ano que vem.
                    max={`${Number(hojeIso().slice(0, 4)) + 1}-12-31`}
                    mesInicial={hojeIso()}
                    aria-pressed={periodo !== null}
                    className={`${styles.pilula} ${periodo ? styles.pilulaAtiva : ''}`}
                  />
                  {periodo && (
                    <Button size="sm" onClick={() => setPeriodo(null)}>
                      Limpar período
                    </Button>
                  )}
                </div>
              </div>

              {filtradas.length === 0 ? (
                <p className={styles.semResultado}>Nenhuma cobrança encontrada para estes filtros.</p>
              ) : (
                <>
                  <div className={styles.rolagem}>
                    <table className={styles.tabela}>
                      <thead>
                        <tr>
                          <th scope="col">Descrição</th>
                          <th scope="col">Vencimento</th>
                          <th scope="col" className={styles.numero}>
                            Valor
                          </th>
                          <th scope="col">Situação</th>
                          <th scope="col">Pagamento</th>
                          <th scope="col" className={styles.acao}>
                            <span className={styles.somenteLeitor}>Ações</span>
                          </th>
                        </tr>
                      </thead>
                      <tbody>
                        {paginasHistorico.itensDaPagina.map((c) => (
                          <tr key={c.id}>
                            <td>
                              <span className={styles.descricao}>{c.descricao}</span>
                              {c.turmaNome && <span className={styles.secundario}>{c.turmaNome}</span>}
                            </td>
                            <td className={styles.data}>{formatarData(c.dataVencimento)}</td>
                            <td className={styles.numero}>{formatarMoeda(c.pagamento?.valorPago ?? c.valor)}</td>
                            <td>
                              <Badge tone={TOM_STATUS[c.status]}>{ROTULOS_STATUS_COBRANCA_ALUNO[c.status]}</Badge>
                            </td>
                            <td className={styles.secundario}>{descreverPagamento(c) ?? '—'}</td>
                            <td className={styles.acao}>
                              <AcaoDaCobranca cobranca={c} onPagar={pagar} onVerComprovante={setComprovanteDe} />
                            </td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                  <Paginacao paginacao={paginasHistorico} rotulo="Páginas do histórico" />
                </>
              )}
            </section>
          )}
        </>
      )}

      {pagando && (
        <ModalPagamento
          cobranca={pagando}
          onPago={() => setPagouAlguma(true)}
          onFechar={fecharPagamento}
        />
      )}

      {comprovanteDe && <ModalComprovante buscar={buscarComprovanteAberto} onFechar={() => setComprovanteDe(null)} />}
    </AlunoLayout>
  )
}

type AcaoPagar = (cobranca: CobrancaDoHistorico) => void

/** O que dá para fazer com a cobrança: pagar, ver o comprovante ou nada. */
function AcaoDaCobranca({
  cobranca,
  onPagar,
  onVerComprovante,
}: {
  cobranca: CobrancaDoHistorico
  onPagar: AcaoPagar
  onVerComprovante: (comprovanteUrl: string) => void
}) {
  if (podePagar(cobranca.status)) return <BotaoPagar cobranca={cobranca} onPagar={onPagar} tamanho="sm" />
  const comprovanteUrl = cobranca.pagamento?.comprovanteUrl
  if (comprovanteUrl) {
    return (
      <Button
        size="sm"
        variant="outline"
        icon="eye"
        onClick={() => onVerComprovante(comprovanteUrl)}
        aria-label={`Ver o comprovante de ${cobranca.descricao}`}
      >
        Ver comprovante
      </Button>
    )
  }
  // Baixa feita pela escola: o Eduka não tem comprovante para mostrar.
  return cobranca.status === 'PAGA' ? <span className={styles.secundario}>Sem comprovante</span> : null
}

/** Cartão de destaque da cobrança em aberto que vence primeiro. */
function ProximaMensalidade({ cobranca, onPagar }: { cobranca: CobrancaDoHistorico; onPagar: AcaoPagar }) {
  const atrasada = cobranca.status === 'EM_ATRASO'
  return (
    <section className={`${styles.proxima} ${atrasada ? styles.proximaAtrasada : ''}`} aria-labelledby="titulo-proxima">
      <div className={styles.proximaInfo}>
        <span id="titulo-proxima" className={styles.proximaRotulo}>
          {atrasada ? 'Cobrança em atraso' : 'Próxima mensalidade'}
        </span>
        <strong className={styles.proximaValor}>{formatarMoeda(cobranca.valor)}</strong>
        <span className={styles.proximaDescricao}>{cobranca.descricao}</span>
        <span className={styles.proximaPrazo}>
          <Badge tone={TOM_STATUS[cobranca.status]}>{prazo(cobranca.dataVencimento)}</Badge>
          <span className={styles.secundarioInline}>Vencimento {formatarData(cobranca.dataVencimento)}</span>
        </span>
      </div>
      <div className={styles.proximaAcoes}>
        <BotaoPagar cobranca={cobranca} onPagar={onPagar} tamanho="md" />
      </div>
    </section>
  )
}

/** "Pagar": abre o modal, onde o aluno escolhe PIX ou boleto. */
function BotaoPagar({
  cobranca,
  onPagar,
  tamanho,
}: {
  cobranca: CobrancaDoHistorico
  onPagar: AcaoPagar
  tamanho: 'sm' | 'md'
}) {
  return (
    <div className={styles.botoes}>
      <Button
        size={tamanho}
        variant="primary"
        icon="card"
        onClick={() => onPagar(cobranca)}
        aria-label={`Pagar ${cobranca.descricao} com PIX ou boleto`}
      >
        Pagar
      </Button>
    </div>
  )
}
