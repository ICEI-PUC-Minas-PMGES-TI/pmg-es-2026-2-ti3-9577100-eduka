import type { ReactNode } from 'react'
import { Button } from '../Button/Button'
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
