import { Badge } from '../../../../../components/ui/Badge/Badge'
import { Button } from '../../../../../components/ui/Button/Button'
import { ROTULOS_STATUS_MATRICULA, TOM_DO_STATUS, type Matricula } from '../../../../../types/academico/matricula'
import { Tabela } from '../../academico/PainelSecretaria/comum'
import { formatarData } from '../../../../../utils/formatacao'
import styles from './MatriculasTable.module.css'

type MatriculasTableProps = {
  matriculas: Matricula[]
  mensagemVazia: string
  /** Quando muda (turma ou filtro), a tabela volta para a primeira página. */
  reiniciarCom?: string
  onVer: (matricula: Matricula) => void
  onAlterar: (matricula: Matricula) => void
}

/**
 * Alunos de uma turma, com a situação da matrícula e as ações de ver e
 * alterar. Fica dentro do cartão "Matrículas da turma", sem cartão próprio. A
 * observação, quase sempre vazia, aparece embaixo do nome em vez de ocupar
 * uma coluna.
 */
export function MatriculasTable({ matriculas, mensagemVazia, reiniciarCom, onVer, onAlterar }: MatriculasTableProps) {
  return (
    <Tabela
      colunas={['Aluno', 'Matriculado em', 'Situação', '']}
      vazio={mensagemVazia}
      rotuloPaginacao="Páginas das matrículas"
      reiniciarCom={reiniciarCom}
      className={styles.cartao}
      linhas={matriculas.map((m) => (
        <tr key={m.id}>
          <td className={styles.aluno}>
            <span className={styles.nome}>{m.alunoNome ?? '—'}</span>
            <span className={styles.numero}>Nº {m.alunoNumeroMatricula}</span>
            {m.observacao && <span className={styles.observacao}>{m.observacao}</span>}
          </td>
          <td className={styles.data}>{formatarData(m.dataMatricula)}</td>
          <td className={styles.situacao}>
            <Badge tone={TOM_DO_STATUS[m.status]}>{ROTULOS_STATUS_MATRICULA[m.status]}</Badge>
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
