import { Children, cloneElement, isValidElement, type ReactNode, type Ref } from 'react'
import { Button } from '../../../../../components/ui/Button/Button'
import { Paginacao } from '../../../../../components/ui/Paginacao/Paginacao'
import { usePaginacao, type PaginaDoBackend, type useListaPaginada } from '../../../../../hooks/usePaginacao'
import { Dica } from '../../../../../components/ui/Dica/Dica'
import styles from './PainelSecretaria.module.css'

type CabecalhoAbaProps = {
  titulo: string
  rotuloNovo: string
  onNovo: () => void
  sucesso: string | null
}

/** Título da aba, botão de cadastro e o aviso do último cadastro feito. */
export function CabecalhoAba({ titulo, rotuloNovo, onNovo, sucesso }: CabecalhoAbaProps) {
  return (
    <>
      <div className={styles.toolbar}>
        <h2 className={styles.tituloAba}>{titulo}</h2>
        <Button variant="primary" icon="plus" onClick={onNovo}>
          {rotuloNovo}
        </Button>
      </div>
      {sucesso && (
        <p className={styles.sucesso} role="status">
          {sucesso}
        </p>
      )}
    </>
  )
}

type TabelaProps = {
  colunas: string[]
  vazio: string
  /** Linhas (<tr>); a tabela mostra `vazio` quando não há nenhuma. */
  linhas: ReactNode[]
  /** Rótulo acessível da paginação, ex.: "Páginas dos cursos". */
  rotuloPaginacao?: string
  /** Quando muda (filtro, turma), a tabela volta para a primeira página. */
  reiniciarCom?: string | number
  /** Classe extra do card, para quem precisa ajustar a tabela (ex.: sem largura mínima). */
  className?: string
}

/** Dá a cada célula o nome da coluna (data-rotulo), que no celular aparece em cima do dado. */
function comRotulos(linha: ReactNode, colunas: string[]): ReactNode {
  if (!isValidElement<{ children?: ReactNode }>(linha)) return linha
  return cloneElement(
    linha,
    undefined,
    Children.toArray(linha.props.children).map((celula, indice) =>
      isValidElement<{ 'data-rotulo'?: string }>(celula) && colunas[indice]
        ? cloneElement(celula, { 'data-rotulo': colunas[indice] })
        : celula,
    ),
  )
}

type CartaoTabelaProps = {
  colunas: string[]
  /** As linhas da página atual. */
  linhas: ReactNode[]
  topo: Ref<HTMLDivElement>
  className?: string
  /** Outra página a caminho do backend: a atual fica, marcada como ocupada. */
  atualizando?: boolean
  /** O que aparece embaixo do cabeçalho no lugar das linhas (vazio, carregando, erro). */
  aviso?: ReactNode
}

/** O card de vidro com a tabela; quem pagina (no front ou no backend) passa só a página atual. */
function CartaoTabela({ colunas, linhas, topo, className, atualizando = false, aviso }: CartaoTabelaProps) {
  return (
    <div ref={topo} className={[styles.card, className].filter(Boolean).join(' ')} aria-busy={atualizando}>
      <div className={styles.rolagem}>
        <table className={styles.tabela}>
          <thead>
            <tr>
              {colunas.map((coluna) => (
                <th key={coluna} scope="col">
                  {coluna}
                </th>
              ))}
            </tr>
          </thead>
          <tbody>{linhas.map((linha) => comRotulos(linha, colunas))}</tbody>
        </table>
      </div>
      {aviso}
    </div>
  )
}

/** Tabela em card de vidro, no mesmo visual da lista de professores, com 10 linhas por página. */
export function Tabela({
  colunas,
  vazio,
  linhas,
  rotuloPaginacao = 'Páginas da tabela',
  reiniciarCom,
  className,
}: TabelaProps) {
  const { paginacao, topo } = usePaginacao(linhas, { reiniciarCom })
  return (
    <>
      <CartaoTabela
        colunas={colunas}
        linhas={paginacao.itensDaPagina}
        topo={topo}
        className={className}
        aviso={linhas.length === 0 && <p className={styles.mensagem}>{vazio}</p>}
      />
      <Paginacao paginacao={paginacao} rotulo={rotuloPaginacao} />
    </>
  )
}

/** O que a TabelaDoBackend usa do que o useListaPaginada devolve. */
export type ListaDoBackend<T> = Pick<
  ReturnType<typeof useListaPaginada<PaginaDoBackend<T>>>,
  'paginacao' | 'topo' | 'carregando' | 'atualizando' | 'erro' | 'recarregar'
>

type TabelaDoBackendProps<T> = {
  colunas: string[]
  vazio: string
  lista: ListaDoBackend<T>
  /** A linha (<tr>) de cada item da página. */
  linha: (item: T) => ReactNode
  rotuloPaginacao: string
  /** Texto enquanto a primeira página não chega. */
  carregandoTexto: string
  className?: string
}

/**
 * A mesma tabela, com a página que o backend devolve: "Carregando" só na
 * primeira vez, a página anterior na tela enquanto a próxima chega e o erro
 * com o Tentar novamente.
 */
export function TabelaDoBackend<T>({
  colunas,
  vazio,
  lista,
  linha,
  rotuloPaginacao,
  carregandoTexto,
  className,
}: TabelaDoBackendProps<T>) {
  const { paginacao, topo, carregando, atualizando, erro, recarregar } = lista
  const itens = !erro && paginacao ? paginacao.itensDaPagina : []

  let aviso: ReactNode = null
  if (carregando) {
    aviso = (
      <p className={styles.mensagem} role="status">
        {carregandoTexto}
      </p>
    )
  } else if (erro) {
    aviso = (
      <div className={styles.mensagem}>
        <p role="alert">{erro}</p>
        <Button size="sm" onClick={recarregar}>
          Tentar novamente
        </Button>
      </div>
    )
  } else if (itens.length === 0) {
    aviso = <p className={styles.mensagem}>{vazio}</p>
  }

  return (
    <>
      <CartaoTabela
        colunas={colunas}
        linhas={itens.map(linha)}
        topo={topo}
        className={className}
        atualizando={atualizando}
        aviso={aviso}
      />
      {paginacao && !erro && <Paginacao paginacao={paginacao} rotulo={rotuloPaginacao} carregando={atualizando} />}
    </>
  )
}

type AcoesLinhaProps = {
  /** Nome do registro, para o rótulo acessível do botão de excluir. */
  nome: string
  onVer: () => void
  onEditar: () => void
  onExcluir: () => void
}

/** Botões Ver, Editar e Excluir da linha de uma tabela do painel. */
export function AcoesLinha({ nome, onVer, onEditar, onExcluir }: AcoesLinhaProps) {
  return (
    <span className={styles.acoesLinha}>
      <Button size="sm" icon="eye" onClick={onVer}>
        Ver
      </Button>
      <Button size="sm" icon="edit" onClick={onEditar}>
        Editar
      </Button>
      <Dica texto="Excluir" descrever={false}>
        <Button size="sm" icon="trash" variant="dangerOutline" aria-label={`Excluir ${nome}`} onClick={onExcluir} />
      </Dica>
    </span>
  )
}
