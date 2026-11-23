import { Badge } from '../../../../../components/ui/Badge/Badge'
import { Button } from '../../../../../components/ui/Button/Button'
import { ROTULOS_STATUS_MATRICULA, TOM_DO_STATUS, type Matricula } from '../../../../../types/academico/matricula'
import { TabelaDoBackend, type ListaDoBackend } from '../../academico/PainelSecretaria/comum'
import { formatarData } from '../../../../../utils/formatacao'
import styles from './MatriculasTable.module.css'

type MatriculasTableProps = {
  /** A página de matrículas da turma, vinda do useListaPaginada. */
  lista: ListaDoBackend<Matricula>
  mensagemVazia: string
  onVer: (matricula: Matricula) => void
  onAlterar: (matricula: Matricula) => void
}

/**
 * Alunos de uma turma, com a situação da matrícula e as ações de ver e
 * alterar. Fica dentro do cartão "Matrículas da turma", sem cartão próprio. A
 * observação, quase sempre vazia, aparece embaixo do nome em vez de ocupar
 * uma coluna. A página vem do backend.
 */
export function MatriculasTable({ lista, mensagemVazia, onVer, onAlterar }: MatriculasTableProps) {
  return (
    <TabelaDoBackend
      colunas={['Aluno', 'Matriculado em', 'Situação', '']}
      vazio={mensagemVazia}
      rotuloPaginacao="Páginas das matrículas"
      carregandoTexto="Carregando as matrículas da turma…"
      className={styles.cartao}
      lista={lista}
      linha={(m) => (
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
      )}
    />
  )
}
