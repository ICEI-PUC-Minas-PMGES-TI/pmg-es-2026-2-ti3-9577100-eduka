import { useCallback, useMemo, useState } from 'react'
import { ModalComprovante } from '../../../../components/financeiro/Comprovante/ModalComprovante'
import { ConfirmarExclusao } from '../../../../components/modal/ConfirmarExclusao/ConfirmarExclusao'
import { EstadoDashboard } from '../../../../components/layout/Dashboard/Dashboard'
import { StatCard, StatCards } from '../../../../components/ui/StatCard/StatCard'
import { useCarregamento } from '../../../../hooks/useCarregamento'
import { ApiError } from '../../../../services/api'
import { listarEscolas } from '../../../../services/escola/escolaService'
import {
  buscarComprovanteDaFatura,
  cancelarFatura,
  lancarFatura,
  listarFaturasDaPlataforma,
} from '../../../../services/escola/faturaPlataformaService'
import type { Escola } from '../../../../types/escola/escola'
import type { FaturaPlataforma, StatusFatura } from '../../../../types/escola/faturaPlataforma'
import { formatarMoeda, hojeIso, plural } from '../../../../utils/formatters'
import { FormLancarFatura } from '../../components/faturas/FormLancarFatura/FormLancarFatura'
import { nomeDoMes } from '../../components/faturas/FormLancarFatura/regrasFatura'
import { TabelaFaturasPlataforma } from '../../components/faturas/TabelaFaturasPlataforma/TabelaFaturasPlataforma'
import { SuperAdminLayout } from '../../layouts/SuperAdminLayout'
import estilosPainel from '../PainelEscolas/PainelEscolas.module.css'
import styles from './FaturasPlataforma.module.css'

const SEM_FATURAS: FaturaPlataforma[] = []
const SEM_ESCOLAS: Escola[] = []

type Filtro = 'TODAS' | Exclude<StatusFatura, 'CANCELADA'>

const FILTROS: { filtro: Filtro; rotulo: string }[] = [
  { filtro: 'TODAS', rotulo: 'Todas' },
  { filtro: 'PENDENTE', rotulo: 'A pagar' },
  { filtro: 'EM_ATRASO', rotulo: 'Em atraso' },
  { filtro: 'PAGA', rotulo: 'Pagas' },
]

function traduzirErroCancelar(erro: unknown): string {
  if (erro instanceof ApiError) {
    switch (erro.codigo) {
      case 'COBRANCA_JA_PAGA':
        return 'O pagamento desta fatura já entrou; ela foi marcada como paga e não pode ser cancelada.'
      case 'PAGAMENTO_EM_PROCESSAMENTO':
        return 'A escola está gerando o pagamento agora. Tente cancelar de novo em alguns segundos.'
      case 'PROVEDOR_INDISPONIVEL':
        return 'O serviço de pagamento está fora do ar. Tente de novo em alguns minutos.'
      default:
        return erro.detalhe ?? erro.message
    }
  }
  return 'Erro inesperado ao cancelar a fatura.'
}

/** Recebido no mês corrente e o que está em atraso, sobre as faturas carregadas. */
function indicadores(faturas: FaturaPlataforma[]) {
  const mes = hojeIso().slice(0, 7)
  const pagasNoMes = faturas.filter((f) => f.status === 'PAGA' && f.pagamento?.dataPagamento.startsWith(mes))
  const atrasadas = faturas.filter((f) => f.status === 'EM_ATRASO')
  return {
    recebido: pagasNoMes.reduce((soma, f) => soma + (f.pagamento?.valorPago ?? f.valor), 0),
    pagas: pagasNoMes.length,
    emAtraso: atrasadas.reduce((soma, f) => soma + f.valor, 0),
    escolasEmAtraso: new Set(atrasadas.map((f) => f.escolaId)).size,
  }
}

/**
 * Faturas da plataforma (IU24): o que cada escola paga ao Edúka. O Super
 * Admin lança (escolhendo a escola), cancela e vê o comprovante; a escola
 * paga pelo próprio painel (IU24-A).
 */
export function FaturasPlataforma() {
  const { dados: faturas, carregando, erro, recarregar } = useCarregamento(
    listarFaturasDaPlataforma,
    SEM_FATURAS,
    'Não foi possível carregar as faturas da plataforma.',
  )
  const { dados: escolas } = useCarregamento(listarEscolas, SEM_ESCOLAS, 'Não foi possível carregar as escolas.')
  const [filtro, setFiltro] = useState<Filtro>('TODAS')
  const [selecionadaId, setSelecionadaId] = useState<string | null>(null)
  const [cancelando, setCancelando] = useState<FaturaPlataforma | null>(null)
  const [comprovanteDe, setComprovanteDe] = useState<FaturaPlataforma | null>(null)
  const [sucesso, setSucesso] = useState<string | null>(null)

  const buscarComprovante = useCallback(
    () => buscarComprovanteDaFatura(comprovanteDe?.escolaId ?? '', comprovanteDe?.id ?? ''),
    [comprovanteDe],
  )

  const resumo = useMemo(() => indicadores(faturas), [faturas])
  const contagem = (f: Filtro) => (f === 'TODAS' ? faturas.length : faturas.filter((x) => x.status === f).length)
  const visiveis = filtro === 'TODAS' ? faturas : faturas.filter((f) => f.status === filtro)
  // Derivada da lista: some sozinha se o filtro a esconder ou se ela deixar de ser cancelável.
  const selecionada = visiveis.find((f) => f.id === selecionadaId && f.acoes.includes('CANCELAR')) ?? null

  return (
    <SuperAdminLayout
      titulo="Faturas da plataforma"
      subtitulo="O que cada escola paga ao Edúka. A escola paga pelo próprio painel, por PIX ou boleto."
    >
      {sucesso && (
        <p role="status" className={estilosPainel.sucesso}>
          {sucesso}
        </p>
      )}
      <EstadoDashboard carregando={carregando} erro={erro} onTentarDeNovo={recarregar} />
      {!carregando && !erro && (
        <div className={styles.grade}>
          <div className={styles.principal}>
            <div className={styles.filtros} role="group" aria-label="Filtrar por situação">
              {FILTROS.map(({ filtro: f, rotulo }) => (
                <button
                  key={f}
                  type="button"
                  aria-pressed={filtro === f}
                  className={`${styles.pilula} ${filtro === f ? styles.pilulaAtiva : ''}`}
                  onClick={() => setFiltro(f)}
                >
                  {rotulo} ({contagem(f)})
                </button>
              ))}
            </div>

            <StatCards rotulo="Resumo das faturas">
              <StatCard
                label={`Recebido em ${nomeDoMes(hojeIso())}`}
                value={formatarMoeda(resumo.recebido)}
                descricao={`${resumo.pagas} ${plural(resumo.pagas, 'fatura paga', 'faturas pagas')}`}
                destaque
              />
              <StatCard
                label="Em atraso"
                value={formatarMoeda(resumo.emAtraso)}
                descricao={`${resumo.escolasEmAtraso} ${plural(resumo.escolasEmAtraso, 'escola', 'escolas')}`}
                alerta={resumo.emAtraso > 0}
              />
            </StatCards>

            <TabelaFaturasPlataforma
              faturas={visiveis}
              reiniciarCom={filtro}
              selecionadaId={selecionada?.id ?? null}
              onSelecionar={setSelecionadaId}
              onVerComprovante={setComprovanteDe}
            />
          </div>

          <FormLancarFatura
            escolas={escolas}
            faturas={faturas}
            onLancar={async (escolaId, dados) => {
              setSucesso(null)
              const lancada = await lancarFatura(escolaId, dados)
              setSucesso(`Fatura de ${lancada.competencia} lançada para ${lancada.escolaNome}.`)
              recarregar()
            }}
          >
            <button
              type="button"
              className={styles.cancelar}
              disabled={!selecionada}
              onClick={() => {
                setSucesso(null)
                setCancelando(selecionada)
              }}
            >
              Cancelar fatura selecionada
            </button>
          </FormLancarFatura>
        </div>
      )}

      {cancelando && (
        <ConfirmarExclusao
          titulo="Cancelar fatura"
          nome={`${cancelando.escolaNome} · ${cancelando.competencia}`}
          rotuloConfirmar="Cancelar fatura"
          iconeConfirmar="close"
          traduzirErro={traduzirErroCancelar}
          onFechar={() => {
            setCancelando(null)
            // COBRANCA_JA_PAGA registra o pagamento: a lista precisa refletir.
            recarregar()
          }}
          onConfirmar={async () => {
            await cancelarFatura(cancelando.escolaId, cancelando.id)
            setCancelando(null)
            setSucesso(`Fatura de ${cancelando.competencia} de ${cancelando.escolaNome} cancelada.`)
            recarregar()
          }}
        >
          Cancelar a fatura de <strong>{cancelando.competencia}</strong> de {cancelando.escolaNome}? Se a escola já
          gerou o PIX ou o boleto, ele deixa de valer.
        </ConfirmarExclusao>
      )}

      {comprovanteDe && <ModalComprovante buscar={buscarComprovante} onFechar={() => setComprovanteDe(null)} />}
    </SuperAdminLayout>
  )
}
