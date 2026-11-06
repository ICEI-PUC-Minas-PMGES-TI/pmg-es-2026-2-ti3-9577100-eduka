import type { ReactNode } from 'react'
import { formatarSaldoCompacto } from '../../utils/formatters'
import { Button } from '../Button/Button'
import { Dica } from '../Dica/Dica'
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
  /** O que o leitor de tela lê como valor; o padrão é "valor de máximo". */
  textoDoValor?: string
}

/** Barra horizontal de proporção (ocupação de vagas, parte de um total). */
export function Medidor({ valor, maximo, rotulo, alerta = false, textoDoValor }: MedidorProps) {
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
      aria-valuetext={textoDoValor ?? `${valor} de ${maximo}`}
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
          <Dica key={item.rotulo} texto={item.onClick ? null : 'Em breve'} className={styles.alvoAtalho}>
            <button
              type="button"
              className={`${styles.atalho} ${indice === 0 ? styles.atalhoDestaque : ''}`}
              disabled={!item.onClick}
              onClick={item.onClick}
            >
              <Icon name={item.icon} size={16} />
              <span>{item.rotulo}</span>
            </button>
          </Dica>
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
          <Dica texto={item.rotulo} soSeCortado className={styles.alvoRotulo}>
            <span className={styles.rotuloBarra}>{item.rotulo}</span>
          </Dica>
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
  /** Saldo do grupo: aparece acima das barras, se couber na coluna, e sempre no tooltip. */
  saldo?: number
}

type GraficoComparativoProps = {
  rotulo: string
  /** Nomes das séries (até duas), na ordem das cores. */
  series: string[]
  grupos: GrupoBarras[]
  /** Texto de cada valor no tooltip e no leitor de tela. */
  formatar: (valor: number) => string
  /** Texto curto das marcas do eixo (ex.: "R$ 1,5 mil"). */
  formatarEixo: (valor: number) => string
  /** Um texto por série, ao lado do nome na legenda (ex.: o total do período). */
  resumo?: string[]
  /** Fica na ponta direita da legenda (ex.: o saldo do período). */
  aoLadoDaLegenda?: ReactNode
}

/** Divisões do eixo: quatro faixas, cinco marcas (zero incluído). */
const FAIXAS_DO_EIXO = 4

/**
 * Topo do eixo: o primeiro valor "redondo" que cobre o maior valor, para as
 * marcas caírem em números fáceis de ler (ex.: 1.180 -> 2.000, de 500 em 500).
 */
function topoDoEixo(maximo: number): number {
  const ordem = 10 ** Math.floor(Math.log10(maximo))
  return ([1, 2, 4, 6, 8, 10].find((fator) => maximo <= fator * ordem) ?? 10) * ordem
}

/**
 * Barras verticais lado a lado por grupo (ex.: entrou e saiu em cada mês), com
 * eixo, linhas de grade e legenda.
 */
export function GraficoComparativo({
  rotulo,
  series,
  grupos,
  formatar,
  formatarEixo,
  resumo,
  aoLadoDaLegenda,
}: GraficoComparativoProps) {
  const topo = topoDoEixo(Math.max(1, ...grupos.flatMap((grupo) => grupo.valores)))
  // Do topo para o zero, na ordem em que aparecem no eixo.
  const marcas = Array.from({ length: FAIXAS_DO_EIXO + 1 }, (_, i) => (topo * (FAIXAS_DO_EIXO - i)) / FAIXAS_DO_EIXO)
  const mostrarSaldos = grupos.some((grupo) => grupo.saldo !== undefined)
  return (
    <div className={styles.comparativo}>
      <div className={styles.cabecalhoGrafico}>
        <ul className={styles.legenda}>
          {series.map((serie, indice) => (
            <li key={serie}>
              <span className={`${styles.amostra} ${CORES_SERIE[indice]}`} aria-hidden="true" />
              {serie}
              {resumo?.[indice] && <strong className={styles.resumoSerie}>{resumo[indice]}</strong>}
            </li>
          ))}
        </ul>
        {aoLadoDaLegenda}
      </div>
      <div className={`${styles.areaGrafico} ${mostrarSaldos ? styles.areaComSaldos : ''}`}>
        <div className={styles.eixo} aria-hidden="true">
          {marcas.map((marca) => (
            <span key={marca}>{formatarEixo(marca)}</span>
          ))}
        </div>
        <div className={styles.plotagem}>
          <div className={styles.grade} aria-hidden="true">
            {marcas.map((marca) => (
              <span key={marca} />
            ))}
          </div>
          <ul className={styles.grafico} aria-label={rotulo}>
            {grupos.map((grupo) => (
              <li key={grupo.titulo ?? grupo.rotulo} className={styles.colunaGrafico}>
                {/* A dica é da coluna inteira: o mês e as séries juntas. As barras já se
                    descrevem (aria-label) para o leitor de tela. */}
                <Dica
                  descrever={false}
                  className={styles.alvoColuna}
                  texto={
                    <span className={styles.dicaGrafico}>
                      <strong className={styles.dicaTitulo}>{grupo.titulo ?? grupo.rotulo}</strong>
                      {grupo.valores.map((valor, indice) => (
                        <span key={series[indice]} className={styles.dicaLinha}>
                          <span className={`${styles.amostra} ${CORES_SERIE[indice]}`} aria-hidden="true" />
                          {series[indice]}
                          <strong>{formatar(valor)}</strong>
                        </span>
                      ))}
                      {grupo.saldo !== undefined && (
                        <span className={styles.dicaLinha}>
                          Saldo
                          <strong>{formatar(grupo.saldo)}</strong>
                        </span>
                      )}
                    </span>
                  }
                >
                  <div className={`${styles.trilho} ${styles.trilhoAgrupado}`}>
                    {mostrarSaldos && grupo.saldo !== undefined && (
                      <span
                        role="img"
                        aria-label={`Saldo em ${grupo.titulo ?? grupo.rotulo}: ${formatar(grupo.saldo)}`}
                        className={`${styles.saldoGrupo} ${grupo.saldo < 0 ? styles.saldoNegativo : ''}`}
                        style={{ bottom: `${(Math.max(0, ...grupo.valores) / topo) * 100}%` }}
                      >
                        {formatarSaldoCompacto(grupo.saldo)}
                      </span>
                    )}
                    {grupo.valores.map((valor, indice) => (
                      <div
                        key={series[indice]}
                        role="img"
                        aria-label={`${series[indice]} em ${grupo.titulo ?? grupo.rotulo}: ${formatar(valor)}`}
                        className={`${styles.barraAgrupada} ${CORES_SERIE[indice]}`}
                        // Um mês só com estornos tem receita negativa: a barra fica no zero.
                        style={{ height: `${(Math.max(0, valor) / topo) * 100}%` }}
                      />
                    ))}
                  </div>
                </Dica>
                <span className={styles.rotuloBarra}>{grupo.rotulo}</span>
              </li>
            ))}
          </ul>
        </div>
      </div>
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
  if (carregando) return <div className={styles.mensagem} role="status">Carregando o resumo…</div>
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
