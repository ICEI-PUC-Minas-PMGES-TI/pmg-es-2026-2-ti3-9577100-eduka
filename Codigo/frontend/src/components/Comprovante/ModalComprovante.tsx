import { useEffect, useState } from 'react'
import { formatarData } from '../../roles/secretaria/components/PainelSecretaria/formatacao'
import { ApiError } from '../../services/api'
import type { Comprovante } from '../../types/faturaPlataforma'
import { ROTULOS_FORMA_PAGAMENTO } from '../../types/pagamentoAluno'
import { formatarMoeda } from '../../utils/formatters'
import { Button } from '../Button/Button'
import { Modal, ModalAlerta, ModalAviso } from '../Modal/Modal'
import styles from './ModalComprovante.module.css'
import { baixarPdfComprovante } from './pdfComprovante'

type ModalComprovanteProps = {
  /** Busca o comprovante (rota do perfil que está vendo). */
  buscar: () => Promise<Comprovante>
  onFechar: () => void
}

/** Comprovante de pagamento montado pelo Eduka (R30): nada vem da página do provedor. */
export function ModalComprovante({ buscar, onFechar }: ModalComprovanteProps) {
  const [comprovante, setComprovante] = useState<Comprovante | null>(null)
  const [erro, setErro] = useState<string | null>(null)

  useEffect(() => {
    let ativo = true
    buscar()
      .then((dados) => ativo && setComprovante(dados))
      .catch((e: unknown) => {
        if (!ativo) return
        setErro(
          e instanceof ApiError && e.codigo === 'COMPROVANTE_INDISPONIVEL'
            ? 'O comprovante fica disponível depois que o pagamento é confirmado.'
            : e instanceof ApiError
              ? (e.detalhe ?? e.message)
              : 'Não foi possível carregar o comprovante.',
        )
      })
    return () => {
      ativo = false
    }
  }, [buscar])

  return (
    <Modal
      titulo="Comprovante de pagamento"
      tamanho="medio"
      onFechar={onFechar}
      rodape={
        <>
          {comprovante && (
            <Button variant="outline" icon="download" onClick={() => baixarPdfComprovante(comprovante)}>
              Baixar comprovante (PDF)
            </Button>
          )}
          <Button variant="primary" onClick={onFechar}>
            Fechar
          </Button>
        </>
      }
    >
      {!comprovante && !erro && <ModalAviso>Carregando o comprovante…</ModalAviso>}
      {erro && <ModalAlerta>{erro}</ModalAlerta>}
      {comprovante && (
        <dl className={styles.dados}>
          <dt>Número</dt>
          <dd className={styles.codigo}>{comprovante.numero}</dd>
          <dt>Beneficiário</dt>
          <dd>
            {comprovante.beneficiarioNome}
            {comprovante.beneficiarioDocumento && ` · ${comprovante.beneficiarioDocumento}`}
          </dd>
          <dt>Pagador</dt>
          <dd>
            {comprovante.pagadorNome}
            {comprovante.pagadorDocumentoMascarado && ` · ${comprovante.pagadorDocumentoMascarado}`}
          </dd>
          <dt>Descrição</dt>
          <dd>{comprovante.descricao}</dd>
          <dt>Vencimento</dt>
          <dd>{formatarData(comprovante.vencimento)}</dd>
          <dt>Valor pago</dt>
          <dd className={styles.valor}>{formatarMoeda(comprovante.valorPago)}</dd>
          {comprovante.valorEstornado > 0 && (
            <>
              <dt>Valor devolvido</dt>
              <dd>{formatarMoeda(comprovante.valorEstornado)}</dd>
            </>
          )}
          <dt>Data do pagamento</dt>
          <dd>{formatarData(comprovante.dataPagamento)}</dd>
          <dt>Forma</dt>
          <dd>{ROTULOS_FORMA_PAGAMENTO[comprovante.forma]}</dd>
          {comprovante.codigoTransacao && (
            <>
              <dt>Código da transação</dt>
              <dd className={styles.codigo}>{comprovante.codigoTransacao}</dd>
            </>
          )}
        </dl>
      )}
    </Modal>
  )
}
