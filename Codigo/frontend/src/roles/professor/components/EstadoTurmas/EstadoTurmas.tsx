import { Button } from '../../../../components/ui/Button/Button'
import styles from './EstadoTurmas.module.css'

/** Carregando, erro ou "sem turmas" enquanto não há turma/disciplina para escolher. */
export function EstadoTurmas({
  carregando,
  erro,
  onTentarDeNovo,
}: {
  carregando: boolean
  erro: string | null
  onTentarDeNovo: () => void
}) {
  if (carregando) return <div className={styles.mensagem} role="status">Carregando suas turmas…</div>
  if (erro) {
    return (
      <div className={styles.mensagem} role="alert">
        <span>{erro}</span>
        <Button size="sm" onClick={onTentarDeNovo}>
          Tentar novamente
        </Button>
      </div>
    )
  }
  return (
    <div className={styles.mensagem}>
      <strong>Você ainda não tem turmas.</strong>
      <span>Assim que a secretaria alocar você em uma turma, você pode cadastrar avaliações e lançar notas.</span>
    </div>
  )
}
