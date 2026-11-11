import { useMemo, useState } from 'react'
import { Badge } from '../../../../components/Badge/Badge'
import { Button } from '../../../../components/Button/Button'
import { Campo } from '../../../../components/Campo/Campo'
import { CampoData } from '../../../../components/CampoData/CampoData'
import type { Periodo } from '../../../../components/CampoPeriodo/CampoPeriodo'
import {
  EstadoDashboard,
  GraficoComparativo,
  Medidor,
  Painel,
  PainelVazio,
} from '../../../../components/Dashboard/Dashboard'
import { Pilulas } from '../../../../components/PainelFinanceiro/PainelFinanceiro'
import { erroDoPeriodo, nomeDoMes, quantasCobrancas, rotuladorDeMeses } from '../../../../components/PainelFinanceiro/textos'
import { StatCard, StatCards } from '../../../../components/StatCard/StatCard'
import { useCarregamento } from '../../../../hooks/useCarregamento'
import { obterRelatorioFinanceiro } from '../../../../services/relatorioFinanceiroService'
import { ROTULOS_TIPO_COBRANCA, type TipoCobranca } from '../../../../types/cobranca'
import { ROTULOS_FORMA_PAGAMENTO } from '../../../../types/pagamentoAluno'
import type { FormaRelatorio, LinhaResumo, RelatorioFinanceiro } from '../../../../types/relatorioFinanceiro'
import {
  formatarMoeda,
  formatarMoedaCompacta,
  formatarPercentual,
  hojeIso,
  plural,
} from '../../../../utils/formatters'
import { ModalExportarCsv } from '../../components/ModalExportarCsv/ModalExportarCsv'
import { FinanceiroLayout } from '../../layouts/FinanceiroLayout'
import { datasDoPeriodoRapido, PERIODOS_RAPIDOS, type PeriodoRapido } from './periodos'
import styles from './Relatorios.module.css'

const PERIODO_INICIAL: PeriodoRapido = 'BIMESTRE'

/** Relatórios financeiros (IU19): o período, os números dele e a exportação em CSV. */
export function Relatorios() {
  const [periodo, setPeriodo] = useState<Periodo>(() => datasDoPeriodoRapido(PERIODO_INICIAL, hojeIso()))
  const [rapido, setRapido] = useState<PeriodoRapido | null>(PERIODO_INICIAL)
  // As datas só valem ao clicar em "Aplicar período"; o relatório na tela segue `periodo`.
  const [de, setDe] = useState(periodo.de)
  const [ate, setAte] = useState(periodo.ate)
  const [erroPeriodo, setErroPeriodo] = useState<string | null>(null)
  const [exportando, setExportando] = useState(false)

  const buscar = useMemo(() => (signal: AbortSignal) => obterRelatorioFinanceiro(periodo, signal), [periodo])
  const { dados, carregando, erro, recarregar } = useCarregamento<RelatorioFinanceiro | null>(
    buscar,
    null,
    'Não foi possível encontrar os dados da escola.',
  )

  function escolherRapido(escolhido: PeriodoRapido) {
    const datas = datasDoPeriodoRapido(escolhido, hojeIso())
    setRapido(escolhido)
    setDe(datas.de)
    setAte(datas.ate)
    setErroPeriodo(null)
    setPeriodo(datas)
  }

  function aplicarPeriodo() {
    const problema =
      !de || !ate
        ? 'Informe as duas datas do período.'
        : de > ate
          ? 'A data inicial não pode ser depois da final.'
          : erroDoPeriodo({ de, ate })
    setErroPeriodo(problema)
    if (!problema) setPeriodo({ de, ate })
  }

  return (
    <FinanceiroLayout
      titulo="Relatórios financeiros"
      subtitulo="Escolha o período, confira os números e exporte em CSV."
      acao={
        <Button variant="primary" icon="download" disabled={!dados} onClick={() => setExportando(true)}>
          Exportar CSV
        </Button>
      }
    >
      <section className={styles.filtros} aria-label="Período do relatório">
        <Pilulas rotulo="Período rápido" opcoes={PERIODOS_RAPIDOS} valor={rapido} onChange={escolherRapido} />
        <div className={styles.datas}>
          <Campo label="De" className={styles.data}>
            {(campo) => (
              <CampoData
                {...campo}
                valor={de}
                max={ate || undefined}
                onChange={(valor) => {
                  setDe(valor)
                  setRapido(null)
                }}
              />
            )}
          </Campo>
          <Campo label="Até" className={styles.data}>
            {(campo) => (
              <CampoData
                {...campo}
                valor={ate}
                min={de || undefined}
                onChange={(valor) => {
                  setAte(valor)
                  setRapido(null)
                }}
              />
            )}
          </Campo>
          <Button onClick={aplicarPeriodo}>
            Aplicar período
          </Button>
        </div>
        {erroPeriodo && (
          <p className={styles.erroPeriodo} role="alert">
            {erroPeriodo}
          </p>
        )}
      </section>

      <EstadoDashboard carregando={carregando} erro={erro} onTentarDeNovo={recarregar} />
      {dados && <Relatorio dados={dados} />}

      {exportando && <ModalExportarCsv periodo={periodo} onFechar={() => setExportando(false)} />}
    </FinanceiroLayout>
  )
}

function Relatorio({ dados }: { dados: RelatorioFinanceiro }) {
  return (
    <>
      <Numeros dados={dados} />
      <div className={styles.graficos}>
        <ReceitasEDespesas dados={dados} />
        <FormasDePagamento formas={dados.formasPagamento} />
      </div>
      <Resumo resumo={dados.resumo} />
    </>
  )
}

function Numeros({ dados }: { dados: RelatorioFinanceiro }) {
  const { recebido, estornado, despesas, despesasPagas, saldo, inadimplencia } = dados
  const pagamentos = `${recebido.quantidade} ${plural(recebido.quantidade, 'pagamento confirmado', 'pagamentos confirmados')}`
  return (
    <StatCards rotulo="Números do período">
      <StatCard
        label="Total recebido"
        value={formatarMoeda(recebido.valor)}
        descricao={estornado.valor > 0 ? `${pagamentos} · ${formatarMoeda(estornado.valor)} estornados` : pagamentos}
        destaque
      />
      <StatCard
        label="Total de despesas"
        value={formatarMoeda(despesas.valor)}
        descricao={`${despesas.quantidade} ${plural(despesas.quantidade, 'lançamento', 'lançamentos')} no período`}
      />
      <StatCard
        label="Saldo do período"
        value={formatarMoeda(saldo)}
        descricao={`Recebido menos ${formatarMoeda(despesasPagas.valor)} de despesas pagas`}
        alerta={saldo < 0}
        positivo={saldo > 0}
      />
      <StatCard
        label="Inadimplência"
        value={formatarPercentual(inadimplencia.percentual)}
        descricao={
          inadimplencia.percentual === null
            ? 'Nada venceu no período'
            : inadimplencia.cobrancas === 0
              ? 'Nenhuma cobrança em atraso'
              : `${formatarMoeda(inadimplencia.valor)} em ${quantasCobrancas(inadimplencia.cobrancas)}`
        }
        alerta={inadimplencia.cobrancas > 0}
      />
    </StatCards>
  )
}

function ReceitasEDespesas({ dados }: { dados: RelatorioFinanceiro }) {
  const meses = dados.receitasEDespesas
  const rotularMes = rotuladorDeMeses(meses.map((mes) => mes.mes))
  const semMovimento = meses.every((mes) => mes.receitas === 0 && mes.despesas === 0)
  return (
    <Painel titulo="Receitas e despesas no período" descricao="Por mês, o recebido e as despesas pagas, com o saldo.">
      {semMovimento ? (
        <PainelVazio>Nenhum dinheiro entrou ou saiu neste período.</PainelVazio>
      ) : (
        <GraficoComparativo
          rotulo="Receitas e despesas pagas por mês"
          series={['Receitas', 'Despesas pagas']}
          formatar={formatarMoeda}
          formatarEixo={formatarMoedaCompacta}
          resumo={[formatarMoeda(dados.recebido.valor), formatarMoeda(dados.despesasPagas.valor)]}
          grupos={meses.map((mes) => ({
            ...rotularMes(mes.mes),
            valores: [mes.receitas, mes.despesas],
            saldo: mes.saldo,
          }))}
        />
      )}
    </Painel>
  )
}

function FormasDePagamento({ formas }: { formas: FormaRelatorio[] }) {
  return (
    <Painel titulo="Formas de pagamento" descricao="Percentual sobre o total recebido no período.">
      <ul className={styles.formas}>
        {formas.map((forma) => {
          const nome = forma.forma ? ROTULOS_FORMA_PAGAMENTO[forma.forma] : 'Baixa manual'
          const percentual = formatarPercentual(forma.percentual)
          return (
            <li key={forma.forma ?? 'MANUAL'}>
              <div className={styles.linhaForma}>
                <strong>{nome}</strong>
                <span>
                  {formatarMoeda(forma.valor)} · {percentual}
                </span>
              </div>
              <Medidor
                rotulo={`Parte de ${nome} no total recebido`}
                valor={Math.max(0, forma.percentual ?? 0)}
                maximo={100}
                textoDoValor={percentual}
              />
            </li>
          )
        })}
      </ul>
    </Painel>
  )
}

type Agrupamento = 'CURSO' | 'TIPO' | 'MES'

const AGRUPAMENTOS: { valor: Agrupamento; rotulo: string }[] = [
  { valor: 'CURSO', rotulo: 'Por curso' },
  { valor: 'TIPO', rotulo: 'Por tipo de cobrança' },
  { valor: 'MES', rotulo: 'Por mês' },
]

const DETALHES_DO_AGRUPAMENTO: Record<Agrupamento, { titulo: string; coluna: string }> = {
  CURSO: { titulo: 'Resumo por curso', coluna: 'Curso' },
  TIPO: { titulo: 'Resumo por tipo de cobrança', coluna: 'Tipo' },
  MES: { titulo: 'Resumo por mês', coluna: 'Mês' },
}

/** O nome da linha na tabela: o backend manda o enum do tipo e o mês em AAAA-MM. */
function nomeDoGrupo(grupo: string | null, agrupamento: Agrupamento): string {
  if (grupo === null) return ''
  if (agrupamento === 'TIPO') return ROTULOS_TIPO_COBRANCA[grupo as TipoCobranca] ?? grupo
  if (agrupamento === 'MES') {
    const { extenso } = nomeDoMes(grupo)
    return extenso.charAt(0).toUpperCase() + extenso.slice(1)
  }
  return grupo
}

// Faixas do protótipo da IU19: até 9% vai bem, de 20% em diante pede atenção.
const INADIMPLENCIA_BAIXA = 9
const INADIMPLENCIA_ALTA = 20

function SeloInadimplencia({ percentual }: { percentual: number | null }) {
  if (percentual === null) return <Badge tone="neutral">—</Badge>
  const tom = percentual < INADIMPLENCIA_BAIXA ? 'success' : percentual < INADIMPLENCIA_ALTA ? 'warning' : 'danger'
  return <Badge tone={tom}>{formatarPercentual(percentual)}</Badge>
}

function Resumo({ resumo }: { resumo: RelatorioFinanceiro['resumo'] }) {
  const [agrupamento, setAgrupamento] = useState<Agrupamento>('CURSO')
  const { titulo, coluna } = DETALHES_DO_AGRUPAMENTO[agrupamento]
  const linhas: LinhaResumo[] = { CURSO: resumo.porCurso, TIPO: resumo.porTipo, MES: resumo.porMes }[agrupamento]
  return (
    <Painel
      titulo={titulo}
      descricao="Cobranças que vencem no período, sem as canceladas."
      acao={<Pilulas rotulo="Agrupar o resumo" opcoes={AGRUPAMENTOS} valor={agrupamento} onChange={setAgrupamento} />}
    >
      {linhas.length === 0 ? (
        <PainelVazio>Nenhuma cobrança vence neste período.</PainelVazio>
      ) : (
        <div className={styles.rolagem}>
          <table className={styles.tabela} aria-label={titulo}>
            <thead>
              <tr>
                <th scope="col">{coluna}</th>
                <th scope="col" className={styles.numero}>Cobrado</th>
                <th scope="col" className={styles.numero}>Recebido</th>
                <th scope="col" className={styles.numero}>Em aberto</th>
                <th scope="col" className={styles.numero}>Inadimplência</th>
              </tr>
            </thead>
            <tbody>
              {linhas.map((linha) => (
                <tr key={linha.grupo}>
                  <th scope="row">{nomeDoGrupo(linha.grupo, agrupamento)}</th>
                  <td className={styles.numero} data-rotulo="Cobrado">
                    {formatarMoeda(linha.cobrado)}
                  </td>
                  <td className={`${styles.numero} ${styles.recebido}`} data-rotulo="Recebido">
                    {formatarMoeda(linha.recebido)}
                  </td>
                  <td className={styles.numero} data-rotulo="Em aberto">
                    {formatarMoeda(linha.emAberto)}
                  </td>
                  <td className={styles.numero} data-rotulo="Inadimplência">
                    <SeloInadimplencia percentual={linha.inadimplencia} />
                  </td>
                </tr>
              ))}
            </tbody>
            <tfoot>
              <tr>
                <th scope="row">Total do período</th>
                <td className={styles.numero} data-rotulo="Cobrado">
                  {formatarMoeda(resumo.total.cobrado)}
                </td>
                <td className={styles.numero} data-rotulo="Recebido">
                  {formatarMoeda(resumo.total.recebido)}
                </td>
                <td className={styles.numero} data-rotulo="Em aberto">
                  {formatarMoeda(resumo.total.emAberto)}
                </td>
                <td className={styles.numero} data-rotulo="Inadimplência">
                  {formatarPercentual(resumo.total.inadimplencia)}
                </td>
              </tr>
            </tfoot>
          </table>
        </div>
      )}
    </Painel>
  )
}
