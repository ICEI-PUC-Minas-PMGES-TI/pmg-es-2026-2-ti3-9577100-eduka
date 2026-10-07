import { useEffect, useId, useRef, useState, type KeyboardEvent } from 'react'
import { createPortal } from 'react-dom'
import { Icon } from '../../../../components/Icon/Icon'
import { usePopoverFlutuante } from '../../../../hooks/usePopoverFlutuante'
import { iniciais } from '../../../../utils/formatters'
import styles from './SeletorProfessor.module.css'

export type OpcaoProfessor = {
  id: string
  nome: string
  /** Linha pequena abaixo do nome: onde o professor já dá aula. */
  descricao: string
}

type SeletorProfessorProps = {
  professores: OpcaoProfessor[]
  /** Id do professor escolhido; '' é "sem professor". */
  valor: string
  onChange: (professorId: string) => void
  disabled?: boolean
  'aria-label': string
}

/** A partir de quantos professores a lista ganha o campo de busca. */
const MINIMO_PARA_BUSCA = 7
const ALTURA_MAXIMA = 340

/**
 * Escolhe o professor de uma disciplina. Fechado, mostra quem está nela (ou a
 * vaga em aberto); aberto, lista os professores com o que cada um já leciona,
 * para a secretaria distribuir a carga sem sair da tela.
 */
export function SeletorProfessor({
  professores,
  valor,
  onChange,
  disabled,
  'aria-label': ariaLabel,
}: SeletorProfessorProps) {
  const [aberto, setAberto] = useState(false)
  const [busca, setBusca] = useState('')
  const [destacado, setDestacado] = useState(0)
  const {
    ancoraRef: botaoRef,
    popoverRef,
    estilo,
  } = usePopoverFlutuante<HTMLButtonElement, HTMLDivElement>(aberto, () => setAberto(false), {
    alturaMinimaAbaixo: 220,
    alturaMaxima: ALTURA_MAXIMA,
  })
  const buscaRef = useRef<HTMLInputElement>(null)
  const idBase = useId()

  const atual = professores.find((p) => p.id === valor) ?? null
  const comBusca = professores.length >= MINIMO_PARA_BUSCA
  const termo = busca.trim().toLowerCase()
  const visiveis = termo ? professores.filter((p) => p.nome.toLowerCase().includes(termo)) : professores
  // A última posição da lista é "deixar sem professor", quando há quem tirar.
  const total = visiveis.length + (atual ? 1 : 0)

  useEffect(() => {
    if (!aberto) return
    if (buscaRef.current) buscaRef.current.focus({ preventScroll: true })
    else popoverRef.current?.focus({ preventScroll: true })
  }, [aberto, popoverRef])

  useEffect(() => {
    if (!aberto) return
    document.getElementById(`${idBase}-opcao-${destacado}`)?.scrollIntoView({ block: 'nearest' })
  }, [aberto, destacado, idBase])

  function abrir() {
    setBusca('')
    setDestacado(Math.max(professores.findIndex((p) => p.id === valor), 0))
    setAberto(true)
  }

  function fechar() {
    setAberto(false)
    botaoRef.current?.focus()
  }

  function escolher(indice: number) {
    if (indice < 0 || indice >= total) return
    onChange(indice < visiveis.length ? visiveis[indice].id : '')
    fechar()
  }

  function handleTecla(event: KeyboardEvent<HTMLDivElement>) {
    switch (event.key) {
      case 'ArrowDown':
        event.preventDefault()
        setDestacado((i) => Math.min(i + 1, total - 1))
        break
      case 'ArrowUp':
        event.preventDefault()
        setDestacado((i) => Math.max(i - 1, 0))
        break
      case 'Enter':
        event.preventDefault()
        escolher(destacado)
        break
      case 'Escape':
        event.preventDefault()
        event.stopPropagation()
        fechar()
        break
      case 'Tab':
        setAberto(false)
        break
    }
  }

  return (
    <>
      <button
        ref={botaoRef}
        type="button"
        className={`${styles.gatilho} ${atual ? '' : styles.vaga}`}
        disabled={disabled}
        aria-haspopup="listbox"
        aria-expanded={aberto}
        aria-label={`${ariaLabel}: ${atual ? atual.nome : 'sem professor'}`}
        onClick={() => (aberto ? setAberto(false) : abrir())}
        onKeyDown={(event) => {
          if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
            event.preventDefault()
            abrir()
          }
        }}
      >
        {atual ? (
          <>
            <span className={styles.avatar} aria-hidden="true">
              {iniciais(atual.nome)}
            </span>
            <span className={styles.nome}>{atual.nome}</span>
          </>
        ) : (
          <>
            <span className={`${styles.avatar} ${styles.avatarVago}`} aria-hidden="true">
              <Icon name="plus" size={15} />
            </span>
            <span className={styles.nome}>Atribuir professor</span>
          </>
        )}
        <Icon name="chevronDown" size={16} className={`${styles.seta} ${aberto ? styles.setaAberta : ''}`} />
      </button>

      {/* No body, e não dentro do painel: o backdrop-filter do vidro vira a
          referência de position: fixed dos filhos e a lista abriria fora do lugar. */}
      {aberto &&
        createPortal(
        <div ref={popoverRef} className={styles.menu} style={estilo} tabIndex={-1} onKeyDown={handleTecla}>
          {comBusca && (
            <label className={styles.busca}>
              <Icon name="search" size={15} />
              <input
                ref={buscaRef}
                type="search"
                placeholder="Procurar professor"
                aria-label="Procurar professor"
                value={busca}
                onChange={(e) => {
                  setBusca(e.target.value)
                  setDestacado(0)
                }}
              />
            </label>
          )}

          <ul className={styles.lista} role="listbox" aria-label={ariaLabel}>
            {visiveis.map((professor, indice) => {
              const selecionado = professor.id === valor
              return (
                <li
                  key={professor.id}
                  id={`${idBase}-opcao-${indice}`}
                  role="option"
                  aria-selected={selecionado}
                  className={`${styles.opcao} ${indice === destacado ? styles.destacada : ''}`}
                  onMouseEnter={() => setDestacado(indice)}
                  // Evita que a busca perca o foco antes do clique.
                  onMouseDown={(e) => e.preventDefault()}
                  onClick={() => escolher(indice)}
                >
                  <span className={styles.avatar} aria-hidden="true">
                    {iniciais(professor.nome)}
                  </span>
                  <span className={styles.texto}>
                    <strong className={styles.nome}>{professor.nome}</strong>
                    <span className={styles.descricao}>{professor.descricao}</span>
                  </span>
                  {selecionado && <Icon name="check" size={16} className={styles.marcado} />}
                </li>
              )
            })}

            {visiveis.length === 0 && (
              <li className={styles.vazio} role="presentation">
                {professores.length === 0 ? 'Nenhum professor ativo na escola.' : 'Nenhum professor encontrado.'}
              </li>
            )}

            {atual && (
              <li
                id={`${idBase}-opcao-${visiveis.length}`}
                role="option"
                aria-selected={false}
                className={`${styles.opcao} ${styles.remover} ${
                  destacado === visiveis.length ? styles.destacada : ''
                }`}
                onMouseEnter={() => setDestacado(visiveis.length)}
                onMouseDown={(e) => e.preventDefault()}
                onClick={() => escolher(visiveis.length)}
              >
                <Icon name="close" size={15} />
                Deixar sem professor
              </li>
            )}
          </ul>
        </div>,
          document.body,
        )}
    </>
  )
}
