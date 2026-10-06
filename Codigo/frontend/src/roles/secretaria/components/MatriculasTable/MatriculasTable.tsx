import { Badge } from '../../../../components/Badge/Badge'
import { Button } from '../../../../components/Button/Button'
import {
  ROTULOS_STATUS_MATRICULA,
  type Matricula,
  type StatusMatricula,
} from '../../../../types/matricula'
import { Tabela } from '../PainelSecretaria/comum'
import { formatarData } from '../PainelSecretaria/formatacao'
import styles from './MatriculasTable.module.css'

const TOM_DO_STATUS: Record<StatusMatricula, 'success' | 'danger' | 'neutral'> = {
  ATIVA: 'success',
  TRANCADA: 'neutral',
  CANCELADA: 'danger',
  CONCLUIDA: 'neutral',
}

type MatriculasTableProps = {
  matriculas: Matricula[]
  mensagemVazia: string
  onAlterar: (matricula: Matricula) => void
}

/** Alunos de uma turma, com a situação da matrícula e a ação de alterar. */
export function MatriculasTable({ matriculas, mensagemVazia, onAlterar }: MatriculasTableProps) {
  return (
    <Tabela
      colunas={['Aluno', 'Matriculado em', 'Situação', 'Observação', '']}
      vazio={mensagemVazia}
      linhas={matriculas.map((m) => (
        <tr key={m.id}>
          <td>
            <span className={styles.nome}>{m.alunoNome ?? '—'}</span>
            <span className={styles.numero}>Nº {m.alunoNumeroMatricula}</span>
          </td>
          <td>{formatarData(m.dataMatricula)}</td>
          <td>
            <Badge tone={TOM_DO_STATUS[m.status]}>{ROTULOS_STATUS_MATRICULA[m.status]}</Badge>
          </td>
          <td className={styles.observacao}>{m.observacao ?? '—'}</td>
          <td className={styles.acoes}>
            <Button size="sm" icon="edit" onClick={() => onAlterar(m)}>
              Alterar
            </Button>
          </td>
        </tr>
      ))}
    />
  )
}
