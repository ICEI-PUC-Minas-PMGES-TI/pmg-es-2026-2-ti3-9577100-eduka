import { Children, cloneElement, isValidElement, type ReactNode } from 'react'
import { Button } from '../../../../components/Button/Button'
import { Paginacao } from '../../../../components/Paginacao/Paginacao'
import { usePaginacao } from '../../../../hooks/usePaginacao'
import { Dica } from '../../../../components/Dica/Dica'
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
      <div ref={topo} className={[styles.card, className].filter(Boolean).join(' ')}>
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
            <tbody>{paginacao.itensDaPagina.map((linha) => comRotulos(linha, colunas))}</tbody>
          </table>
        </div>
        {linhas.length === 0 && <p className={styles.mensagem}>{vazio}</p>}
      </div>
      <Paginacao paginacao={paginacao} rotulo={rotuloPaginacao} />
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
