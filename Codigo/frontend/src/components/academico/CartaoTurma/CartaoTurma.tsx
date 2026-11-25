import type { ReactNode } from 'react'
import { Icon, type IconName } from '../../ui/Icon/Icon'
import styles from './CartaoTurma.module.css'

export type DisciplinaDoCartao = {
  id: number
  nome: string
  codigo: string | null
  /** Linha abaixo do nome (ex.: professor e carga horária). */
  detalhe: ReactNode
  /** Atalhos da disciplina (ex.: Avaliações e Chamada do professor). */
  acoes?: ReactNode
}

type CartaoTurmaProps = {
  nome: string
  codigo: string | null
  /** "1 de 3" dentro de um carrossel; nulo com uma turma só. */
  posicao: string | null
  /** Situação no canto (ex.: Badge "Ativa"). */
  situacao: ReactNode
  etiquetas: { icone: IconName; texto: string }[]
  /** Resumo acima das disciplinas (ex.: "7 disciplinas · 760 h no total"). */
  resumo: string
  disciplinas: DisciplinaDoCartao[]
  semDisciplinas: string
  /** A turma em andamento ganha o brilho lilás. */
  destaque?: boolean
  /** Sozinho no carrossel, com a largura toda: as disciplinas usam mais colunas. */
  largo?: boolean
}

/**
 * Cartão de uma turma com a grade de disciplinas, usado nos carrosséis de
 * turmas do aluno (Meus dados) e do professor (Meu perfil).
 */
export function CartaoTurma({
  nome,
  codigo,
  posicao,
  situacao,
  etiquetas,
  resumo,
  disciplinas,
  semDisciplinas,
  destaque = false,
  largo = false,
}: CartaoTurmaProps) {
  return (
    <article className={`${styles.cartao} ${destaque ? styles.cartaoAtivo : ''}`} aria-label={`Turma ${nome}`}>
      <header className={styles.cartaoTopo}>
        <div className={styles.cartaoTitulo}>
          {posicao && <span className={styles.cartaoPosicao}>Turma {posicao}</span>}
          <h4 className={styles.turmaNome}>
            {nome}
            {codigo && <span className={styles.codigo}> · {codigo}</span>}
          </h4>
        </div>
        {situacao}
      </header>

      <ul className={styles.etiquetas} aria-label={`Dados da turma ${nome}`}>
        {etiquetas.map((e) => (
          <li key={e.texto}>
            <Icon name={e.icone} size={14} />
            {e.texto}
          </li>
        ))}
      </ul>

      {disciplinas.length === 0 ? (
        <p className={styles.semDisciplinas}>{semDisciplinas}</p>
      ) : (
        <>
          <p className={styles.gradeResumo}>{resumo}</p>
          <ul
            className={`${styles.disciplinas} ${largo ? styles.disciplinasLargas : ''}`}
            aria-label={`Disciplinas de ${nome}`}
          >
            {disciplinas.map((d) => (
              <li key={d.id} className={styles.disciplina}>
                <span className={styles.disciplinaNome}>
                  {d.nome}
                  {d.codigo && <span className={styles.codigo}> · {d.codigo}</span>}
                </span>
                <span className={styles.disciplinaDetalhe}>{d.detalhe}</span>
                {d.acoes && <div className={styles.disciplinaAcoes}>{d.acoes}</div>}
              </li>
            ))}
          </ul>
        </>
      )}
    </article>
  )
}
