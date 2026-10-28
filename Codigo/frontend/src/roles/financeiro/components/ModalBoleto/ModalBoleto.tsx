import { useState } from 'react'
import { Button } from '../../../../components/Button/Button'
import { Modal } from '../../../../components/Modal/Modal'
import type { Cobranca } from '../../../../types/cobranca'
import { formatarMoeda } from '../../../../utils/formatters'
import styles from './ModalBoleto.module.css'

type ModalBoletoProps = {
  cobranca: Cobranca | null
  onFechar: () => void
}

function formatarDataBr(isoDate: string): string {
  if (!isoDate) return ''
  const partes = isoDate.split('-')
  if (partes.length === 3) return `${partes[2]}/${partes[1]}/${partes[0]}`
  return isoDate
}

export function ModalBoleto({ cobranca, onFechar }: ModalBoletoProps) {
  const [copiado, setCopiado] = useState(false)

  if (!cobranca) return null

  const linhaDigitavel = '34191.79001 01043.510047 91020.150008 8 10250000089000'

  function copiarLinha() {
    navigator.clipboard?.writeText(linhaDigitavel.replace(/\s+/g, ''))
    setCopiado(true)
    setTimeout(() => setCopiado(false), 2500)
  }

  function handleImprimir() {
    window.print()
  }

  return (
    <Modal
      titulo="Boleto Bancário"
      subtitulo={cobranca.descricao}
      tamanho="medio"
      onFechar={onFechar}
      rodape={
        <div className={styles.rodapeBotoes}>
          <Button variant="outline" onClick={handleImprimir}>
            Imprimir / Salvar PDF
          </Button>
          <Button variant="primary" onClick={onFechar}>
            Fechar
          </Button>
        </div>
      }
    >
      <div className={styles.container}>
        <div className={styles.cabecalhoBoleto}>
          <span className={styles.banco}>Banco Eduka</span>
          <span className={styles.codigoBanco}>341-7</span>
        </div>

        <div>
          <span className={styles.rotulo}>Linha digitável</span>
          <div className={styles.linhaDigitavelBox}>
            <span className={styles.linhaTexto}>{linhaDigitavel}</span>
            <Button size="sm" variant="outline" onClick={copiarLinha}>
              {copiado ? 'Copiado!' : 'Copiar'}
            </Button>
          </div>
        </div>

        <div className={styles.gridInfo}>
          <div className={styles.campo}>
            <span className={styles.rotulo}>Beneficiário</span>
            <span className={styles.dado}>Colégio Santo Agostinho (CNPJ: 61.693.123/0001-45)</span>
          </div>

          <div className={styles.campo}>
            <span className={styles.rotulo}>Pagador / Aluno</span>
            <span className={styles.dado}>
              {cobranca.alunoNome ?? 'Aluno sem nome'}
              {cobranca.responsavelNome && ` (Resp: ${cobranca.responsavelNome})`}
            </span>
          </div>

          <div className={styles.campo}>
            <span className={styles.rotulo}>Vencimento</span>
            <span className={styles.dado}>{formatarDataBr(cobranca.dataVencimento)}</span>
          </div>

          <div className={styles.campo}>
            <span className={styles.rotulo}>Valor do Documento</span>
            <span className={styles.dado}>{formatarMoeda(cobranca.valor)}</span>
          </div>
        </div>

        <div>
          <span className={styles.rotulo}>Código de barras</span>
          <div className={styles.codigoBarrasGrafico} aria-hidden="true">
            {Array.from({ length: 48 }).map((_, i) => (
              <div
                key={i}
                className={styles.barraPreta}
                style={{
                  width: `${(i % 3 === 0 ? 3 : i % 2 === 0 ? 2 : 1) * 2}px`,
                  opacity: i % 5 === 0 ? 0.9 : 1,
                }}
              />
            ))}
          </div>
        </div>
      </div>
    </Modal>
  )
}

