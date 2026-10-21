import { useCallback, useState } from 'react'
import { Link, useParams } from 'react-router'
import { Button } from '../../../../components/Button/Button'
import { ModalComprovante } from '../../../../components/Comprovante/ModalComprovante'
import { ConfirmarExclusao } from '../../../../components/ConfirmarExclusao/ConfirmarExclusao'
import { EstadoDashboard } from '../../../../components/Dashboard/Dashboard'
import { Icon } from '../../../../components/Icon/Icon'
import { TabelaFaturas } from '../../../../components/TabelaFaturas/TabelaFaturas'
import { useCarregamento } from '../../../../hooks/useCarregamento'
import { paths } from '../../../../routes/paths'
import { ApiError } from '../../../../services/api'
import { buscarEscola } from '../../../../services/escolaService'
import {
  buscarComprovanteDaFatura,
  cancelarFatura,
  lancarFatura,
  listarFaturasDaEscola,
} from '../../../../services/faturaPlataformaService'
import type { Escola } from '../../../../types/escola'
import type { FaturaPlataforma } from '../../../../types/faturaPlataforma'
import { ModalLancarFatura } from '../../components/ModalLancarFatura/ModalLancarFatura'
import { SuperAdminLayout } from '../../layouts/SuperAdminLayout'
import estilosEdicao from '../EditarEscola/EditarEscola.module.css'
import estilosPainel from '../PainelEscolas/PainelEscolas.module.css'

const SEM_FATURAS: FaturaPlataforma[] = []

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

/** Faturas que uma escola paga ao Eduka (Fluxo A): lançar, cancelar e ver o comprovante. */
export function Faturas() {
  const { id: escolaId = '' } = useParams()
  const buscarFaturas = useCallback((signal: AbortSignal) => listarFaturasDaEscola(escolaId, signal), [escolaId])
  const buscarDadosEscola = useCallback((signal: AbortSignal) => buscarEscola(escolaId, signal), [escolaId])
  const { dados: faturas, carregando, erro, recarregar } = useCarregamento(
    buscarFaturas,
    SEM_FATURAS,
    'Escola não encontrada.',
  )
  const { dados: escola } = useCarregamento<Escola | null>(buscarDadosEscola, null, 'Escola não encontrada.')

  const [lancando, setLancando] = useState(false)
  const [cancelando, setCancelando] = useState<FaturaPlataforma | null>(null)
  const [comprovanteDe, setComprovanteDe] = useState<string | null>(null)
  const [sucesso, setSucesso] = useState<string | null>(null)

  const buscarComprovante = useCallback(
    () => buscarComprovanteDaFatura(escolaId, comprovanteDe ?? ''),
    [escolaId, comprovanteDe],
  )

  const nomeEscola = escola?.nome ?? faturas[0]?.escolaNome ?? ''

  return (
    <SuperAdminLayout
      titulo="Faturas da escola"
      subtitulo={nomeEscola || undefined}
      voltar={
        <Link to={paths.superAdmin.escolas} className={estilosEdicao.voltar}>
          <Icon name="arrowLeft" size={16} />
          Voltar para o painel de escolas
        </Link>
      }
      acao={
        <Button
          variant="primary"
          icon="plus"
          disabled={!escola?.ativo}
          title={escola && !escola.ativo ? 'Escola suspensa não recebe fatura' : undefined}
          onClick={() => {
            setSucesso(null)
            setLancando(true)
          }}
        >
          Lançar fatura
        </Button>
      }
    >
      {sucesso && (
        <p role="status" className={estilosPainel.sucesso}>
          {sucesso}
        </p>
      )}
      <EstadoDashboard carregando={carregando} erro={erro} onTentarDeNovo={recarregar} />
      {!carregando && !erro && (
        <TabelaFaturas
          faturas={faturas}
          vazio="Nenhuma fatura lançada para esta escola."
          acoes={(f) => (
            <>
              {f.acoes.includes('VER_COMPROVANTE') && (
                <Button size="sm" variant="outline" icon="eye" onClick={() => setComprovanteDe(f.id)}>
                  Comprovante
                </Button>
              )}
              {f.acoes.includes('CANCELAR') && (
                <Button size="sm" variant="dangerOutline" icon="close" onClick={() => setCancelando(f)}>
                  Cancelar
                </Button>
              )}
            </>
          )}
        />
      )}

      {lancando && (
        <ModalLancarFatura
          escolaNome={nomeEscola}
          onFechar={() => setLancando(false)}
          onLancar={async (dados) => {
            const lancada = await lancarFatura(escolaId, dados)
            setLancando(false)
            setSucesso(`Fatura de ${lancada.competencia} lançada.`)
            recarregar()
          }}
        />
      )}

      {cancelando && (
        <ConfirmarExclusao
          titulo="Cancelar fatura"
          nome={`${cancelando.competencia} · ${cancelando.descricao}`}
          rotuloConfirmar="Cancelar fatura"
          iconeConfirmar="close"
          traduzirErro={traduzirErroCancelar}
          onFechar={() => {
            setCancelando(null)
            // COBRANCA_JA_PAGA registra o pagamento: a lista precisa refletir.
            recarregar()
          }}
          onConfirmar={async () => {
            await cancelarFatura(escolaId, cancelando.id)
            setCancelando(null)
            setSucesso(`Fatura de ${cancelando.competencia} cancelada.`)
            recarregar()
          }}
        >
          Cancelar a fatura de <strong>{cancelando.competencia}</strong>? Se a escola já gerou o PIX ou o boleto, ele
          deixa de valer.
        </ConfirmarExclusao>
      )}

      {comprovanteDe && <ModalComprovante buscar={buscarComprovante} onFechar={() => setComprovanteDe(null)} />}
    </SuperAdminLayout>
  )
}
