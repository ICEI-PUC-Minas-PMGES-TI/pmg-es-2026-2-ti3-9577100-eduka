import { Badge } from '../../../../components/Badge/Badge'
import { Button } from '../../../../components/Button/Button'
import { ROTULOS_STATUS_MATRICULA, TOM_DO_STATUS, type Matricula } from '../../../../types/matricula'
import { Tabela } from '../PainelSecretaria/comum'
import { formatarData } from '../PainelSecretaria/formatacao'
import styles from './MatriculasTable.module.css'

type MatriculasTableProps = {
  matriculas: Matricula[]
  mensagemVazia: string
  /** Quando muda (turma ou filtro), a tabela volta para a primeira página. */
  reiniciarCom?: string
  onVer: (matricula: Matricula) => void
  onAlterar: (matricula: Matricula) => void
}

/** Alunos de uma turma, com a situação da matrícula e a ação de alterar. */
export function MatriculasTable({ matriculas, mensagemVazia, reiniciarCom, onVer, onAlterar }: MatriculasTableProps) {
  return (
    <Tabela
      colunas={['Aluno', 'Matriculado em', 'Situação', 'Observação', '']}
      vazio={mensagemVazia}
      rotuloPaginacao="Páginas das matrículas"
      reiniciarCom={reiniciarCom}
      className={styles.cartao}
      linhas={matriculas.map((m) => (
        <tr key={m.id}>
          <td className={styles.aluno}>
            <span className={styles.nome}>{m.alunoNome ?? '—'}</span>
            <span className={styles.numero}>Nº {m.alunoNumeroMatricula}</span>
          </td>
          {/* data-rotulo: no card estreito, as colunas viram linhas e o rótulo vai junto. */}
          <td className={styles.data} data-rotulo="Matriculado em">
            {formatarData(m.dataMatricula)}
          </td>
          <td className={styles.situacao}>
            <Badge tone={TOM_DO_STATUS[m.status]}>{ROTULOS_STATUS_MATRICULA[m.status]}</Badge>
          </td>
          <td className={styles.observacao} data-rotulo="Observação">
            {m.observacao ?? '—'}
          </td>
          <td className={styles.acoes}>
            <div className={styles.botoes}>
              <Button size="sm" icon="eye" onClick={() => onVer(m)}>
                Ver
              </Button>
              <Button size="sm" icon="edit" onClick={() => onAlterar(m)}>
                Alterar
              </Button>
            </div>
          </td>
        </tr>
      ))}
    />
  )
}
