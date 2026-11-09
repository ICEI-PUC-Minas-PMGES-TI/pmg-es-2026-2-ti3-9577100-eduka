import { useId, useState, type ReactNode } from 'react'
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

/** Aba além da de dados. O conteúdo só é montado enquanto a aba está aberta. */
export type AbaDetalhe = {
  id: string
  rotulo: string
  conteudo: ReactNode
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
  /**
   * Abas além da de dados (ex.: "Financeiro"). Sem abas extras, o diálogo
   * mostra só as seções, sem barra de abas.
   */
  abas?: AbaDetalhe[]
  /** Rótulo da aba das seções, quando há abas extras. */
  rotuloDados?: string
  /** Botões de ação no rodapé, antes do "Fechar". */
  acoes?: ReactNode
  onFechar: () => void
}

const ABA_DADOS = 'dados'

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
  abas = [],
  rotuloDados = 'Dados',
  acoes,
  onFechar,
}: ModalDetalhesProps) {
  const [abaAtiva, setAbaAtiva] = useState(ABA_DADOS)
  const prefixo = useId()
  const extra = abas.find((a) => a.id === abaAtiva)
  const todas = [{ id: ABA_DADOS, rotulo: rotuloDados }, ...abas]

  return (
    <Modal
      titulo={titulo}
      subtitulo={subtitulo}
      tamanho="grande"
      onFechar={onFechar}
      rodape={
        <>
          {acoes}
          <Button onClick={onFechar}>Fechar</Button>
        </>
      }
    >
      {abas.length > 0 && (
        <div className={styles.abas} role="tablist" aria-label="Seções do cadastro">
          {todas.map((a) => (
            <button
              key={a.id}
              type="button"
              role="tab"
              id={`${prefixo}-aba-${a.id}`}
              aria-selected={abaAtiva === a.id}
              aria-controls={`${prefixo}-painel`}
              className={`${styles.aba} ${abaAtiva === a.id ? styles.abaAtiva : ''}`}
              onClick={() => setAbaAtiva(a.id)}
            >
              {a.rotulo}
            </button>
          ))}
        </div>
      )}

      {extra ? (
        <div id={`${prefixo}-painel`} role="tabpanel" aria-labelledby={`${prefixo}-aba-${extra.id}`} className={styles.painel}>
          {extra.conteudo}
        </div>
      ) : (
        <PainelDados
          id={abas.length > 0 ? `${prefixo}-painel` : undefined}
          rotuladoPor={abas.length > 0 ? `${prefixo}-aba-${ABA_DADOS}` : undefined}
          secoes={secoes}
          carregando={carregando}
          erro={erro}
        >
          {children}
        </PainelDados>
      )}
    </Modal>
  )
}

type PainelDadosProps = {
  id?: string
  rotuladoPor?: string
  secoes: SecaoDetalhe[]
  carregando: boolean
  erro: string | null
  children?: ReactNode
}

/** As seções de rótulo/valor (a aba "Dados", quando há abas). */
function PainelDados({ id, rotuladoPor, secoes, carregando, erro, children }: PainelDadosProps) {
  return (
    <div id={id} role={id ? 'tabpanel' : undefined} aria-labelledby={rotuladoPor} className={styles.painel}>
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
    </div>
  )
}
