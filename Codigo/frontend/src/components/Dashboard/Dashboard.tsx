import type { ReactNode } from 'react'
import { Button } from '../Button/Button'
import { Icon, type IconName } from '../Icon/Icon'
import styles from './Dashboard.module.css'

// Peças comuns das telas de "Início" (dashboard) de cada perfil.

/** Painéis lado a lado; empilham em tela estreita. */
export function Colunas({ children }: { children: ReactNode }) {
  return <div className={styles.colunas}>{children}</div>
}

type PainelProps = {
  titulo: string
  descricao?: ReactNode
  /** Botão ou link no canto do cabeçalho. */
  acao?: ReactNode
  children: ReactNode
}

/** Card de vidro com título, para um bloco do dashboard. */
export function Painel({ titulo, descricao, acao, children }: PainelProps) {
  return (
    <section className={styles.painel} aria-label={titulo}>
      <header className={styles.cabecalho}>
        <div>
          <h2 className={styles.titulo}>{titulo}</h2>
          {descricao && <p className={styles.descricao}>{descricao}</p>}
        </div>
        {acao}
      </header>
      {children}
    </section>
  )
}

type MedidorProps = {
  valor: number
  maximo: number
  /** Lido pelo leitor de tela, ex.: "Ocupação da turma 1A". */
  rotulo: string
  /** Vermelho para o que pede atenção (ex.: turma lotada). */
  alerta?: boolean
}

/** Barra horizontal de proporção (ocupação de vagas, parte de um total). */
export function Medidor({ valor, maximo, rotulo, alerta = false }: MedidorProps) {
  // Acima do máximo (ou com máximo 0) a barra enche; o meter não aceita valor maior que o teto.
  const teto = Math.max(maximo, valor)
  const percentual = teto > 0 ? (valor / teto) * 100 : 0
  return (
    <div
      className={styles.medidor}
      role="meter"
      aria-label={rotulo}
      aria-valuemin={0}
      aria-valuemax={teto}
      aria-valuenow={valor}
      aria-valuetext={`${valor} de ${maximo}`}
    >
      <div
        className={`${styles.preenchimento} ${alerta ? styles.preenchimentoAlerta : ''}`}
        style={{ width: `${percentual}%` }}
      />
    </div>
  )
}

export type Atalho = {
  rotulo: string
  icon: IconName
  /** Sem ação, o atalho ainda não tem tela e aparece desabilitado ("Em breve"). */
  onClick?: () => void
}

type AtalhosProps = {
  titulo: string
  itens: Atalho[]
  /** Botões lado a lado, para quando o card ocupa a linha inteira. */
  horizontal?: boolean
}

/** Card lavanda com os atalhos do dia, um botão por linha (o primeiro em destaque). */
export function Atalhos({ titulo, itens, horizontal = false }: AtalhosProps) {
  return (
    <section className={`${styles.atalhos} ${horizontal ? styles.atalhosHorizontal : ''}`} aria-label={titulo}>
      <h2 className={styles.atalhosTitulo}>{titulo}</h2>
      <div className={styles.atalhosLista}>
        {itens.map((item, indice) => (
          <button
            key={item.rotulo}
            type="button"
            className={`${styles.atalho} ${indice === 0 ? styles.atalhoDestaque : ''}`}
            disabled={!item.onClick}
            title={item.onClick ? undefined : 'Em breve'}
            onClick={item.onClick}
          >
            <Icon name={item.icon} size={16} />
            <span>{item.rotulo}</span>
          </button>
        ))}
      </div>
    </section>
  )
}

export type Barra = {
  rotulo: string
  valor: number
  /** Texto acima da barra; o padrão é o próprio valor. */
  legenda?: string
}

/** Gráfico de barras verticais simples, com a maior barra ocupando a altura toda. */
export function GraficoBarras({ itens, rotulo }: { itens: Barra[]; rotulo: string }) {
  const maximo = Math.max(1, ...itens.map((item) => item.valor))
  return (
    <ul className={styles.grafico} aria-label={rotulo}>
      {itens.map((item) => (
        <li key={item.rotulo} className={styles.colunaGrafico}>
          <span className={styles.legendaBarra}>{item.legenda ?? item.valor}</span>
          <div className={styles.trilho}>
            <div className={styles.barra} style={{ height: `${(item.valor / maximo) * 100}%` }} />
          </div>
          <span className={styles.rotuloBarra} title={item.rotulo}>
            {item.rotulo}
          </span>
        </li>
      ))}
    </ul>
  )
}

/** Cor de cada série, pela posição: a primeira lavanda, a segunda verde-azulada. */
const CORES_SERIE = [styles.serie1, styles.serie2]

export type GrupoBarras = {
  rotulo: string
  /** Um valor por série, na ordem das séries. */
  valores: number[]
  /** Rótulo completo no tooltip (ex.: "outubro de 2026"); o padrão é o rótulo. */
  titulo?: string
}

type GraficoComparativoProps = {
  rotulo: string
  /** Nomes das séries (até duas), na ordem das cores. */
  series: string[]
  grupos: GrupoBarras[]
  /** Texto de cada valor no tooltip e no leitor de tela. */
  formatar: (valor: number) => string
}

/** Barras verticais lado a lado por grupo (ex.: entrou e saiu em cada mês), com legenda. */
export function GraficoComparativo({ rotulo, series, grupos, formatar }: GraficoComparativoProps) {
  const maximo = Math.max(1, ...grupos.flatMap((grupo) => grupo.valores))
  return (
    <div className={styles.comparativo}>
      <ul className={styles.legenda}>
        {series.map((serie, indice) => (
          <li key={serie}>
            <span className={`${styles.amostra} ${CORES_SERIE[indice]}`} aria-hidden="true" />
            {serie}
          </li>
        ))}
      </ul>
      <ul className={styles.grafico} aria-label={rotulo}>
        {grupos.map((grupo) => (
          <li key={grupo.rotulo} className={styles.colunaGrafico}>
            <div className={`${styles.trilho} ${styles.trilhoAgrupado}`}>
              {grupo.valores.map((valor, indice) => {
                const texto = `${series[indice]} em ${grupo.titulo ?? grupo.rotulo}: ${formatar(valor)}`
                return (
                  <div
                    key={series[indice]}
                    role="img"
                    aria-label={texto}
                    title={texto}
                    className={`${styles.barraAgrupada} ${CORES_SERIE[indice]}`}
                    style={{ height: `${(valor / maximo) * 100}%` }}
                  />
                )
              })}
            </div>
            <span className={styles.rotuloBarra} title={grupo.titulo}>
              {grupo.rotulo}
            </span>
          </li>
        ))}
      </ul>
    </div>
  )
}

/** Lista de pares rótulo/valor. */
export function Linhas({ itens }: { itens: { rotulo: string; valor: ReactNode }[] }) {
  return (
    <dl className={styles.linhas}>
      {itens.map((item) => (
        <div key={item.rotulo} className={styles.linha}>
          <dt>{item.rotulo}</dt>
          <dd>{item.valor}</dd>
        </div>
      ))}
    </dl>
  )
}

/** Texto de "nada por aqui" dentro de um Painel. */
export function PainelVazio({ children }: { children: ReactNode }) {
  return <p className={styles.vazio}>{children}</p>
}

type EstadoDashboardProps = {
  carregando: boolean
  erro: string | null
  onTentarDeNovo: () => void
}

/** Carregando ou erro do dashboard inteiro; nada quando os dados chegaram. */
export function EstadoDashboard({ carregando, erro, onTentarDeNovo }: EstadoDashboardProps) {
  if (carregando) return <div className={styles.mensagem}>Carregando o resumo…</div>
  if (!erro) return null
  return (
    <div className={styles.mensagem} role="alert">
      <span>{erro}</span>
      <Button size="sm" onClick={onTentarDeNovo}>
        Tentar novamente
      </Button>
    </div>
  )
}
