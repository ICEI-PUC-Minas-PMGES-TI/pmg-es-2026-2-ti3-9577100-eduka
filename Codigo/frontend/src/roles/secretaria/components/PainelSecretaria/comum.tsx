import type { ReactNode } from 'react'
import { Button } from '../../../../components/Button/Button'
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
}

/** Tabela em card de vidro, no mesmo visual da lista de professores. */
export function Tabela({ colunas, vazio, linhas }: TabelaProps) {
  return (
    <div className={styles.card}>
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
          <tbody>{linhas}</tbody>
        </table>
      </div>
      {linhas.length === 0 && <p className={styles.mensagem}>{vazio}</p>}
    </div>
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
      <Button
        size="sm"
        icon="trash"
        variant="dangerOutline"
        aria-label={`Excluir ${nome}`}
        title="Excluir"
        onClick={onExcluir}
      />
    </span>
  )
}
