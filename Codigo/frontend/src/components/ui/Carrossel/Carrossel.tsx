import { useRef, useState, type KeyboardEvent, type ReactNode } from 'react'
import { Button } from '../Button/Button'
import styles from './Carrossel.module.css'

export type ItemCarrossel = {
  chave: string
  /** Nome do item, usado no indicador "Ir para ..." */
  rotulo: string
  conteudo: ReactNode
}

type CarrosselProps = {
  /** Título do card e nome acessível da região (ex.: "Minhas turmas"). */
  titulo: string
  /** Linha abaixo do título, só com itens carregados. */
  resumo?: ReactNode
  itens: ItemCarrossel[]
  carregando?: boolean
  erro?: string | null
  onTentarNovamente?: () => void
  textoCarregando: string
  /** Conteúdo quando não há itens. */
  vazio: ReactNode
  rotulos: {
    /** Nome acessível da trilha (ex.: "Turmas"). */
    lista: string
    anterior: string
    proxima: string
    irPara: (rotulo: string) => string
  }
}

/**
 * Card com um carrossel horizontal: um cartão por vez, com a ponta do
 * próximo aparecendo na borda. Desliza com o dedo, com as setas do teclado,
 * pelos botões ou pelos indicadores; com um item só, ele ocupa a largura toda
 * e os controles somem.
 */
export function Carrossel({
  titulo,
  resumo,
  itens,
  carregando = false,
  erro = null,
  onTentarNovamente,
  textoCarregando,
  vazio,
  rotulos,
}: CarrosselProps) {
  const trilhaRef = useRef<HTMLUListElement>(null)
  const [atual, setAtual] = useState(0)
  // Enquanto a rolagem suave de um clique anda, o cartão mais perto ainda é o
  // de partida: sem isto o indicador voltaria e um segundo clique rápido não avançaria.
  const destinoRef = useRef<{ indice: number; ate: number } | null>(null)
  const total = itens.length
  const varios = total > 1
  const pronto = !carregando && !erro

  function irPara(indice: number) {
    const alvo = Math.max(0, Math.min(total - 1, indice))
    setAtual(alvo)
    destinoRef.current = { indice: alvo, ate: Date.now() + 1000 }
    const trilha = trilhaRef.current
    const cartao = trilha?.children[alvo] as HTMLElement | undefined
    // A trilha é position: relative, então offsetLeft já é a posição dentro dela.
    if (trilha && cartao && typeof trilha.scrollTo === 'function') trilha.scrollTo({ left: cartao.offsetLeft })
  }

  /** Quem desliza com o dedo também move o indicador: vale o cartão mais perto do início. */
  function sincronizar() {
    const trilha = trilhaRef.current
    if (!trilha) return
    let maisPerto = 0
    if (trilha.scrollLeft + trilha.clientWidth >= trilha.scrollWidth - 2) {
      maisPerto = total - 1
    } else {
      let menorDistancia = Infinity
      Array.from(trilha.children).forEach((filho, i) => {
        const distancia = Math.abs((filho as HTMLElement).offsetLeft - trilha.scrollLeft)
        if (distancia < menorDistancia) {
          menorDistancia = distancia
          maisPerto = i
        }
      })
    }
    const destino = destinoRef.current
    if (destino) {
      if (maisPerto !== destino.indice && Date.now() < destino.ate) return
      destinoRef.current = null
    }
    setAtual(maisPerto)
  }

  /** Quem pega a trilha com o dedo ou a roda do mouse assume o controle. */
  function soltarDestino() {
    destinoRef.current = null
  }

  function aoTeclar(evento: KeyboardEvent) {
    if (evento.key === 'ArrowRight') {
      evento.preventDefault()
      irPara(atual + 1)
    } else if (evento.key === 'ArrowLeft') {
      evento.preventDefault()
      irPara(atual - 1)
    }
  }

  return (
    <section className={styles.carrossel} aria-roledescription="carrossel" aria-label={titulo}>
      <header className={styles.topo}>
        <div>
          <h3 className={styles.titulo}>{titulo}</h3>
          {pronto && total > 0 && resumo && <p className={styles.resumo}>{resumo}</p>}
        </div>
        {varios && pronto && (
          <div className={styles.controles}>
            <span className={styles.posicao} aria-live="polite">
              {atual + 1} de {total}
            </span>
            <Button
              size="sm"
              icon="chevronLeft"
              aria-label={rotulos.anterior}
              disabled={atual === 0}
              onClick={() => irPara(atual - 1)}
            />
            <Button
              size="sm"
              icon="chevronRight"
              aria-label={rotulos.proxima}
              disabled={atual === total - 1}
              onClick={() => irPara(atual + 1)}
            />
          </div>
        )}
      </header>

      {carregando && (
        <p className={styles.vazio} role="status">
          {textoCarregando}
        </p>
      )}

      {!carregando && erro && (
        <div className={styles.vazio} role="alert">
          <span>{erro}</span>
          {onTentarNovamente && (
            <Button size="sm" onClick={onTentarNovamente}>
              Tentar novamente
            </Button>
          )}
        </div>
      )}

      {pronto && total === 0 && <div className={styles.vazio}>{vazio}</div>}

      {pronto && total > 0 && (
        <>
          <ul
            ref={trilhaRef}
            className={`${styles.trilha} ${varios ? '' : styles.trilhaUnica}`}
            aria-label={rotulos.lista}
            tabIndex={varios ? 0 : undefined}
            onScroll={varios ? sincronizar : undefined}
            onPointerDown={varios ? soltarDestino : undefined}
            onWheel={varios ? soltarDestino : undefined}
            onKeyDown={varios ? aoTeclar : undefined}
          >
            {itens.map((item) => (
              <li key={item.chave} className={styles.slide} aria-roledescription="slide">
                {item.conteudo}
              </li>
            ))}
          </ul>

          {varios && (
            <div className={styles.pontos}>
              {itens.map((item, i) => (
                <button
                  key={item.chave}
                  type="button"
                  className={`${styles.ponto} ${i === atual ? styles.pontoAtivo : ''}`}
                  aria-label={rotulos.irPara(item.rotulo)}
                  aria-current={i === atual ? 'true' : undefined}
                  onClick={() => irPara(i)}
                />
              ))}
            </div>
          )}
        </>
      )}
    </section>
  )
}
