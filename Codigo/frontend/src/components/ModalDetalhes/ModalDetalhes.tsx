import { useEffect, useId, useRef, type ReactNode } from 'react'
import { Button } from '../Button/Button'
import { Icon } from '../Icon/Icon'
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
  const dialogRef = useRef<HTMLDialogElement>(null)
  const tituloId = useId()

  useEffect(() => {
    const dialog = dialogRef.current
    dialog?.showModal()
    return () => dialog?.close()
  }, [])

  return (
    <dialog
      ref={dialogRef}
      className={styles.dialog}
      aria-labelledby={tituloId}
      onCancel={(e) => {
        e.preventDefault()
        onFechar()
      }}
    >
      <div className={styles.conteudo}>
        <header className={styles.header}>
          <div className={styles.cabecalho}>
            <h2 id={tituloId} className={styles.titulo}>
              {titulo}
            </h2>
            {subtitulo && <div className={styles.subtitulo}>{subtitulo}</div>}
          </div>
          <button type="button" className={styles.fechar} aria-label="Fechar" onClick={onFechar}>
            <Icon name="close" size={16} />
          </button>
        </header>

        <div className={styles.corpo}>
          {carregando && <p className={styles.aviso}>Carregando…</p>}
          {erro && (
            <p className={styles.alerta} role="alert">
              {erro}
            </p>
          )}

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
        </div>

        <footer className={styles.footer}>
          <Button onClick={onFechar}>Fechar</Button>
        </footer>
      </div>
    </dialog>
  )
}
