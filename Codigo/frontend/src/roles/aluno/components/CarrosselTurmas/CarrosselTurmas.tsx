import { useRef, useState, type KeyboardEvent } from 'react'
import { Badge } from '../../../../components/ui/Badge/Badge'
import { Button } from '../../../../components/ui/Button/Button'
import { Icon } from '../../../../components/ui/Icon/Icon'
import { ROTULOS_TURNO } from '../../../../types/academico/alocacao'
import { ROTULOS_STATUS_MATRICULA, TOM_DO_STATUS } from '../../../../types/academico/matricula'
import type { TurmaDoAluno } from '../../../../types/portal/portalAluno'
import { formatarData } from '../../../../utils/formatacao'
import { plural } from '../../../../utils/formatters'
import styles from './CarrosselTurmas.module.css'

type CarrosselTurmasProps = {
  turmas: TurmaDoAluno[]
  carregando: boolean
  erro: string | null
  onTentarNovamente: () => void
}

/**
 * Turmas do aluno num carrossel (ativas primeiro, como o backend devolve): um
 * cartão por turma com a grade de disciplinas. Desliza com o dedo, com as
 * setas do teclado ou pelos botões; com uma turma só, o cartão ocupa a largura toda.
 */
export function CarrosselTurmas({ turmas, carregando, erro, onTentarNovamente }: CarrosselTurmasProps) {
  const trilhaRef = useRef<HTMLUListElement>(null)
  const [atual, setAtual] = useState(0)
  // Enquanto a rolagem suave de um clique anda, o cartão mais perto ainda é o
  // de partida: sem isto o indicador voltaria e um segundo clique rápido não avançaria.
  const destinoRef = useRef<{ indice: number; ate: number } | null>(null)
  const total = turmas.length
  const varias = total > 1

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
    <section className={styles.carrossel} aria-roledescription="carrossel" aria-label="Minhas turmas">
      <header className={styles.topo}>
        <div>
          <h3 className={styles.titulo}>Minhas turmas</h3>
          {!carregando && !erro && total > 0 && (
            <p className={styles.resumo}>
              {total} {plural(total, 'turma', 'turmas')} com as disciplinas e os professores de cada uma.
            </p>
          )}
        </div>
        {varias && !carregando && !erro && (
          <div className={styles.controles}>
            <span className={styles.posicao} aria-live="polite">
              {atual + 1} de {total}
            </span>
            <Button
              size="sm"
              icon="chevronLeft"
              aria-label="Turma anterior"
              disabled={atual === 0}
              onClick={() => irPara(atual - 1)}
            />
            <Button
              size="sm"
              icon="chevronRight"
              aria-label="Próxima turma"
              disabled={atual === total - 1}
              onClick={() => irPara(atual + 1)}
            />
          </div>
        )}
      </header>

      {carregando && (
        <p className={styles.vazio} role="status">
          Carregando suas turmas…
        </p>
      )}

      {!carregando && erro && (
        <div className={styles.vazio} role="alert">
          <span>{erro}</span>
          <Button size="sm" onClick={onTentarNovamente}>
            Tentar novamente
          </Button>
        </div>
      )}

      {!carregando && !erro && total === 0 && (
        <p className={styles.vazio}>
          <Icon name="graduationCap" size={22} />
          Você ainda não está matriculado em nenhuma turma.
        </p>
      )}

      {!carregando && !erro && total > 0 && (
        <>
          <ul
            ref={trilhaRef}
            className={`${styles.trilha} ${varias ? '' : styles.trilhaUnica}`}
            aria-label="Turmas"
            tabIndex={varias ? 0 : undefined}
            onScroll={varias ? sincronizar : undefined}
            onPointerDown={varias ? soltarDestino : undefined}
            onWheel={varias ? soltarDestino : undefined}
            onKeyDown={varias ? aoTeclar : undefined}
          >
            {turmas.map((t, i) => (
              <li key={t.matriculaId} className={styles.slide} aria-roledescription="slide">
                <CartaoTurma turma={t} posicao={varias ? `${i + 1} de ${total}` : null} />
              </li>
            ))}
          </ul>

          {varias && (
            <div className={styles.pontos}>
              {turmas.map((t, i) => (
                <button
                  key={t.matriculaId}
                  type="button"
                  className={`${styles.ponto} ${i === atual ? styles.pontoAtivo : ''}`}
                  aria-label={`Ir para a turma ${t.turmaNome}`}
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

function CartaoTurma({ turma: t, posicao }: { turma: TurmaDoAluno; posicao: string | null }) {
  const cargaTotal = t.disciplinas.reduce((soma, d) => soma + d.cargaHoraria, 0)
  const ativa = t.situacaoMatricula === 'ATIVA'

  return (
    <article className={`${styles.cartao} ${ativa ? styles.cartaoAtivo : ''}`} aria-label={`Turma ${t.turmaNome}`}>
      <header className={styles.cartaoTopo}>
        <div className={styles.cartaoTitulo}>
          {posicao && <span className={styles.cartaoPosicao}>Turma {posicao}</span>}
          <h4 className={styles.turmaNome}>
            {t.turmaNome}
            {t.turmaCodigo && <span className={styles.codigo}> · {t.turmaCodigo}</span>}
          </h4>
        </div>
        <Badge tone={TOM_DO_STATUS[t.situacaoMatricula]}>{ROTULOS_STATUS_MATRICULA[t.situacaoMatricula]}</Badge>
      </header>

      <ul className={styles.etiquetas} aria-label={`Dados da turma ${t.turmaNome}`}>
        <li>
          <Icon name="graduationCap" size={14} />
          {t.cursoNome}
        </li>
        <li>
          <Icon name="calendar" size={14} />
          Período {t.periodoLetivoNome}
        </li>
        <li>
          <Icon name="clock" size={14} />
          {ROTULOS_TURNO[t.turno]}
        </li>
        <li>
          <Icon name="check" size={14} />
          Matrícula em {formatarData(t.dataMatricula)}
        </li>
      </ul>

      {t.disciplinas.length === 0 ? (
        <p className={styles.semDisciplinas}>A grade desta turma ainda não tem disciplinas.</p>
      ) : (
        <>
          <p className={styles.gradeResumo}>
            {t.disciplinas.length} {plural(t.disciplinas.length, 'disciplina', 'disciplinas')} · {cargaTotal} h no
            total
          </p>
          <ul className={styles.disciplinas} aria-label={`Disciplinas de ${t.turmaNome}`}>
            {t.disciplinas.map((d) => (
              <li key={d.id} className={styles.disciplina}>
                <span className={styles.disciplinaNome}>
                  {d.nome}
                  {d.codigo && <span className={styles.codigo}> · {d.codigo}</span>}
                </span>
                <span className={styles.disciplinaDetalhe}>
                  {d.professorNome ?? 'Professor a definir'} <span className={styles.semQuebra}>· {d.cargaHoraria} h</span>
                </span>
              </li>
            ))}
          </ul>
        </>
      )}
    </article>
  )
}
