import { useEffect, useId, useRef, useState, type KeyboardEvent } from 'react'
import { Icon } from '../../../../components/ui/Icon/Icon'
import { POPOVER, usePopoverFlutuante } from '../../../../hooks/usePopoverFlutuante'
import { agoraNaEscola, proximaAula, rotuloDaProximaAula, type ProximaAula } from '../../../../types/academico/horario'
import { plural } from '../../../../utils/formatters'
import type { TurmaDisciplina } from '../../hooks/useTurmaDisciplina'
import styles from './SeletorTurmaDisciplina.module.css'

type SeletorTurmaDisciplinaProps = {
  opcoes: TurmaDisciplina[]
  selecionada: TurmaDisciplina | null
  onChange: (chave: string) => void
}

const ALTURA_MAXIMA = 460

/** Uma opção da lista, já com a próxima aula calculada. */
type Item = { tipo: 'opcao'; opcao: TurmaDisciplina; proxima: ProximaAula | null } | { tipo: 'encerradas' }

type Grupo = { titulo: string; itens: Extract<Item, { tipo: 'opcao' }>[] }

function corresponde(opcao: TurmaDisciplina, termo: string): boolean {
  if (!termo) return true
  return [opcao.turma.nome, opcao.turma.codigo, opcao.turma.cursoNome, opcao.disciplina.nome, opcao.turma.periodoLetivoNome]
    .filter(Boolean)
    .some((texto) => (texto as string).toLowerCase().includes(termo))
}

/**
 * Agrupa por "Disciplina · Período", na ordem das disciplinas; dentro do
 * grupo, a turma com a aula mais próxima primeiro (sem horário, por nome, no fim).
 */
function agrupar(itens: Extract<Item, { tipo: 'opcao' }>[]): Grupo[] {
  const grupos = new Map<string, Grupo>()
  for (const item of itens) {
    const titulo = `${item.opcao.disciplina.nome} · ${item.opcao.turma.periodoLetivoNome}`
    const grupo = grupos.get(titulo) ?? { titulo, itens: [] }
    grupo.itens.push(item)
    grupos.set(titulo, grupo)
  }
  return [...grupos.values()]
    .sort((a, b) => a.titulo.localeCompare(b.titulo, 'pt-BR'))
    .map((grupo) => ({
      ...grupo,
      itens: [...grupo.itens].sort(
        (a, b) =>
          (a.proxima?.daquiA ?? Infinity) - (b.proxima?.daquiA ?? Infinity) ||
          a.opcao.turma.nome.localeCompare(b.opcao.turma.nome, 'pt-BR', { numeric: true }),
      ),
    }))
}

/**
 * O que a lista mostra para a busca: as ativas e as encerradas agrupadas e,
 * na ordem do teclado, as ativas, o botão das encerradas e, abertas, as encerradas.
 */
function montarLista(opcoes: TurmaDisciplina[], termo: string, verEncerradas: boolean) {
  const agora = agoraNaEscola()
  const paraItem = (opcao: TurmaDisciplina) => ({
    tipo: 'opcao' as const,
    opcao,
    // Turma encerrada não tem próxima aula.
    proxima: opcao.turma.ativa ? proximaAula(opcao.disciplina.horarios, agora) : null,
  })
  const ativas = agrupar(opcoes.filter((o) => o.turma.ativa && corresponde(o, termo)).map(paraItem))
  const encerradas = agrupar(opcoes.filter((o) => !o.turma.ativa && corresponde(o, termo)).map(paraItem))
  const quantasEncerradas = encerradas.reduce((total, g) => total + g.itens.length, 0)
  // Buscando, as encerradas que batem aparecem sem precisar abrir.
  const mostrarEncerradas = verEncerradas || termo !== ''
  const itens: Item[] = [
    ...ativas.flatMap((g) => g.itens),
    ...(quantasEncerradas > 0 ? [{ tipo: 'encerradas' as const }] : []),
    ...(mostrarEncerradas ? encerradas.flatMap((g) => g.itens) : []),
  ]
  return { ativas, encerradas, quantasEncerradas, mostrarEncerradas, itens }
}

/**
 * Escolhe a turma/disciplina das telas do professor. Fechado, mostra a turma
 * e a disciplina; aberto, busca e lista as turmas por disciplina, com quantos
 * alunos cada uma tem e quando é a próxima aula. As turmas encerradas ficam
 * recolhidas no fim da lista.
 */
export function SeletorTurmaDisciplina({ opcoes, selecionada, onChange }: SeletorTurmaDisciplinaProps) {
  const [aberto, setAberto] = useState(false)
  const [busca, setBusca] = useState('')
  const [verEncerradas, setVerEncerradas] = useState(false)
  const [destacado, setDestacado] = useState(0)
  const {
    ancoraRef: botaoRef,
    popoverRef,
    estilo,
    portal,
  } = usePopoverFlutuante<HTMLButtonElement, HTMLDivElement>(aberto, () => setAberto(false), {
    alturaMinimaAbaixo: 280,
    alturaMaxima: ALTURA_MAXIMA,
  })
  const buscaRef = useRef<HTMLInputElement>(null)
  const idBase = useId()

  const termo = busca.trim().toLowerCase()
  const totalEncerradas = opcoes.filter((o) => !o.turma.ativa).length
  const { ativas, encerradas, quantasEncerradas, mostrarEncerradas, itens } = montarLista(
    opcoes,
    termo,
    verEncerradas,
  )
  const indiceDe = (item: Item) => itens.indexOf(item)

  useEffect(() => {
    if (aberto) buscaRef.current?.focus({ preventScroll: true })
  }, [aberto])

  useEffect(() => {
    if (!aberto) return
    document.getElementById(`${idBase}-item-${destacado}`)?.scrollIntoView({ block: 'nearest' })
  }, [aberto, destacado, idBase])

  function abrir() {
    // Aberta numa turma encerrada, a lista já mostra as encerradas.
    const naEncerrada = selecionada ? !selecionada.turma.ativa : false
    const aoAbrir = montarLista(opcoes, '', naEncerrada).itens
    setBusca('')
    setVerEncerradas(naEncerrada)
    setDestacado(Math.max(aoAbrir.findIndex((i) => i.tipo === 'opcao' && i.opcao.chave === selecionada?.chave), 0))
    setAberto(true)
  }

  function fechar() {
    setAberto(false)
    botaoRef.current?.focus()
  }

  function ativar(indice: number) {
    const item = itens[indice]
    if (!item) return
    if (item.tipo === 'encerradas') {
      const abrindo = !mostrarEncerradas
      setVerEncerradas(abrindo)
      // Abertas, as encerradas ficam abaixo da dobra do menu: rola até elas.
      if (abrindo) {
        requestAnimationFrame(() =>
          document.getElementById(`${idBase}-encerradas`)?.scrollIntoView({ block: 'nearest', behavior: 'smooth' }),
        )
      }
      return
    }
    if (item.opcao.chave !== selecionada?.chave) onChange(item.opcao.chave)
    fechar()
  }

  function handleTecla(event: KeyboardEvent<HTMLDivElement>) {
    switch (event.key) {
      case 'ArrowDown':
        event.preventDefault()
        setDestacado((i) => Math.min(i + 1, itens.length - 1))
        break
      case 'ArrowUp':
        event.preventDefault()
        setDestacado((i) => Math.max(i - 1, 0))
        break
      case 'Enter':
        event.preventDefault()
        ativar(destacado)
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

  function renderOpcao(item: Extract<Item, { tipo: 'opcao' }>) {
    const indice = indiceDe(item)
    const { opcao, proxima } = item
    const escolhida = opcao.chave === selecionada?.chave
    const classes = [styles.opcao, escolhida && styles.escolhida, indice === destacado && styles.destacada]
    return (
      <li
        key={opcao.chave}
        id={`${idBase}-item-${indice}`}
        role="option"
        aria-selected={escolhida}
        className={classes.filter(Boolean).join(' ')}
        onMouseEnter={() => setDestacado(indice)}
        // Evita que a busca perca o foco antes do clique.
        onMouseDown={(e) => e.preventDefault()}
        onClick={() => ativar(indice)}
      >
        <span className={styles.texto}>
          <strong className={styles.nomeTurma}>{opcao.turma.nome}</strong>
          <span className={styles.alunos}>
            {opcao.turma.alunosAtivos} {plural(opcao.turma.alunosAtivos, 'aluno', 'alunos')}
          </span>
        </span>
        {proxima &&
          (proxima.emAndamento ? (
            <span className={styles.agora}>{rotuloDaProximaAula(proxima)}</span>
          ) : (
            <span className={styles.proxima}>{rotuloDaProximaAula(proxima)}</span>
          ))}
      </li>
    )
  }

  function renderGrupos(grupos: Grupo[]) {
    return grupos.map((grupo) => (
      <li key={grupo.titulo} role="presentation" className={styles.grupo}>
        <span className={styles.tituloGrupo} aria-hidden="true">
          {grupo.titulo}
        </span>
        <ul role="group" aria-label={grupo.titulo} className={styles.lista}>
          {grupo.itens.map(renderOpcao)}
        </ul>
      </li>
    ))
  }

  const indiceEncerradas = itens.findIndex((i) => i.tipo === 'encerradas')

  return (
    <div className={styles.campo}>
      {/* O nome do botão para o leitor de tela já diz "Turma": aqui o rótulo só aparece. */}
      <span className={styles.rotulo} aria-hidden="true">
        Turma
      </span>
      <button
        ref={botaoRef}
        type="button"
        className={styles.gatilho}
        aria-haspopup="listbox"
        aria-expanded={aberto}
        aria-label={`Turma: ${
          selecionada
            ? `${selecionada.turma.nome} · ${selecionada.disciplina.nome}${selecionada.turma.ativa ? '' : ' (encerrada)'}`
            : 'nenhuma escolhida'
        }`}
        onClick={() => (aberto ? setAberto(false) : abrir())}
        onKeyDown={(event) => {
          if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
            event.preventDefault()
            abrir()
          }
        }}
      >
        <span className={styles.valor}>
          {selecionada ? (
            <>
              <strong className={styles.valorTurma}>{selecionada.turma.nome}</strong>
              <span className={styles.valorDetalhe}>
                {' · '}
                {selecionada.disciplina.nome}
                {selecionada.turma.ativa ? '' : ' (encerrada)'}
              </span>
            </>
          ) : (
            'Escolha a turma'
          )}
        </span>
        <Icon name="chevronDown" size={18} className={`${styles.seta} ${aberto ? styles.setaAberta : ''}`} />
      </button>

      {portal(
        <div ref={popoverRef} {...POPOVER} className={styles.menu} style={estilo} tabIndex={-1} onKeyDown={handleTecla}>
          <label className={styles.busca}>
            <Icon name="search" size={16} />
            <input
              ref={buscaRef}
              type="search"
              placeholder="Buscar turma"
              aria-label="Buscar turma"
              aria-controls={`${idBase}-lista`}
              aria-activedescendant={destacado >= 0 ? `${idBase}-item-${destacado}` : undefined}
              value={busca}
              onChange={(e) => {
                setBusca(e.target.value)
                setDestacado(0)
              }}
            />
          </label>

          <div className={styles.rolagem}>
            <ul id={`${idBase}-lista`} className={styles.lista} role="listbox" aria-label="Turmas">
              {renderGrupos(ativas)}
              {ativas.length === 0 && !(termo && quantasEncerradas > 0) && (
                <li className={styles.vazio} role="presentation">
                  {termo ? 'Nenhuma turma encontrada.' : 'Nenhuma turma ativa.'}
                </li>
              )}
            </ul>

            {quantasEncerradas > 0 && (
              <>
                {/* Fora da lista: abre e fecha as encerradas, não é uma turma. */}
                <button
                  type="button"
                  tabIndex={-1}
                  id={`${idBase}-item-${indiceEncerradas}`}
                  aria-expanded={mostrarEncerradas}
                  className={`${styles.alternarEncerradas} ${indiceEncerradas === destacado ? styles.destacada : ''}`}
                  onMouseEnter={() => setDestacado(indiceEncerradas)}
                  onMouseDown={(e) => e.preventDefault()}
                  onClick={() => ativar(indiceEncerradas)}
                >
                  <Icon name="archive" size={16} />
                  <span className={styles.textoEncerradas}>
                    Turmas encerradas ({termo ? quantasEncerradas : totalEncerradas})
                  </span>
                  <Icon
                    name="chevronDown"
                    size={16}
                    className={`${styles.seta} ${mostrarEncerradas ? styles.setaAberta : ''}`}
                  />
                </button>
                {mostrarEncerradas && (
                  <ul id={`${idBase}-encerradas`} className={styles.lista} role="listbox" aria-label="Turmas encerradas">
                    {renderGrupos(encerradas)}
                  </ul>
                )}
              </>
            )}
          </div>
        </div>,
      )}
    </div>
  )
}
