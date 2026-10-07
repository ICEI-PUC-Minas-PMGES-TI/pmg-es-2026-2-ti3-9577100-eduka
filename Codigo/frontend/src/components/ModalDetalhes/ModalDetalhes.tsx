import type { ReactNode } from 'react'
import { Button } from '../Button/Button'
import { Modal, ModalAlerta, ModalAviso } from '../Modal/Modal'
import styles from './ModalDetalhes.module.css'

export type ItemDetalhe = {
  rotulo: string
  /** Vazio (null, undefined ou '') aparece como "—". */
  valor: ReactNode
  /** Ocupa a linha inteira (textos longos, listas). */
  largo?: boolean
}

export type SecaoDetalhe = {
  titulo?: string
  itens: ItemDetalhe[]
}

type ModalDetalhesProps = {
  titulo: string
  /** Linha abaixo do título (ex.: código, situação). */
  subtitulo?: ReactNode
  secoes: SecaoDetalhe[]
  /** Enquanto busca o detalhe no backend. */
  carregando?: boolean
  erro?: string | null
  /** Conteúdo extra depois das seções (ex.: uma lista). */
  children?: ReactNode
  onFechar: () => void
}

function vazio(valor: ReactNode): boolean {
  return valor === null || valor === undefined || valor === ''
}

/**
 * Diálogo só de leitura com os dados de uma entidade cadastrada, em seções de
 * rótulo/valor. Montado só enquanto está aberto.
 */
export function ModalDetalhes({
  titulo,
  subtitulo,
  secoes,
  carregando = false,
  erro = null,
  children,
  onFechar,
}: ModalDetalhesProps) {
  return (
    <Modal
      titulo={titulo}
      subtitulo={subtitulo}
      tamanho="grande"
      onFechar={onFechar}
      rodape={<Button onClick={onFechar}>Fechar</Button>}
    >
      {carregando && <ModalAviso>Carregando…</ModalAviso>}
      {erro && <ModalAlerta>{erro}</ModalAlerta>}

      {!carregando &&
        !erro &&
        secoes.map((secao, indice) => (
          <section key={secao.titulo ?? indice} className={styles.secao}>
            {secao.titulo && <h3 className={styles.secaoTitulo}>{secao.titulo}</h3>}
            <dl className={styles.grid}>
              {secao.itens.map((item) => (
                <div key={item.rotulo} className={item.largo ? styles.itemLargo : styles.item}>
                  <dt>{item.rotulo}</dt>
                  <dd>{vazio(item.valor) ? '—' : item.valor}</dd>
                </div>
              ))}
            </dl>
          </section>
        ))}

      {!carregando && !erro && children}
    </Modal>
  )
}
