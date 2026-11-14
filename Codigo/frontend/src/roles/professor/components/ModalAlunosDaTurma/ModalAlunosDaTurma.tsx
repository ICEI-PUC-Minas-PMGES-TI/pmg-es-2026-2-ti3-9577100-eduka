import { useCallback } from 'react'
import { Badge } from '../../../../components/ui/Badge/Badge'
import { Button } from '../../../../components/ui/Button/Button'
import { EstadoDashboard } from '../../../../components/layout/Dashboard/Dashboard'
import { Modal } from '../../../../components/modal/Modal/Modal'
import { useCarregamento } from '../../../../hooks/useCarregamento'
import { listarAlunosDaTurma } from '../../../../services/portal/portalProfessorService'
import { ROTULOS_STATUS_MATRICULA } from '../../../../types/academico/matricula'
import type { AlunoDaTurma, TurmaDoProfessor } from '../../../../types/academico/turmaProfessor'
import styles from './ModalAlunosDaTurma.module.css'

const SEM_ALUNOS: AlunoDaTurma[] = []

/** Ver alunos (IU13): quem está matriculado na turma do professor, só para consulta. */
export function ModalAlunosDaTurma({ turma, onFechar }: { turma: TurmaDoProfessor; onFechar: () => void }) {
  const buscar = useCallback((signal: AbortSignal) => listarAlunosDaTurma(turma.id, signal), [turma.id])
  const { dados: alunos, carregando, erro, recarregar } = useCarregamento(
    buscar,
    SEM_ALUNOS,
    'Turma não encontrada entre as suas.',
  )
  const ativos = alunos.filter((a) => a.situacao === 'ATIVA').length

  return (
    <Modal
      titulo={`Alunos · ${turma.nome}`}
      subtitulo={carregando || erro ? turma.cursoNome : `${ativos} ${ativos === 1 ? 'aluno ativo' : 'alunos ativos'}`}
      tamanho="medio"
      onFechar={onFechar}
      rodape={
        <Button variant="primary" onClick={onFechar}>
          Fechar
        </Button>
      }
    >
      <EstadoDashboard carregando={carregando} erro={erro} onTentarDeNovo={recarregar} />
      {!carregando && !erro && alunos.length === 0 && (
        <p className={styles.vazio}>Nenhum aluno matriculado nesta turma.</p>
      )}
      {alunos.length > 0 && (
        <ul className={styles.lista} aria-label={`Alunos da turma ${turma.nome}`}>
          {alunos.map((aluno) => (
            <li key={aluno.alunoId} className={styles.aluno}>
              <span className={styles.nome}>
                <strong>{aluno.nome ?? 'Aluno'}</strong>
                <small>Matrícula {aluno.numeroMatricula}</small>
              </span>
              <Badge tone={aluno.situacao === 'ATIVA' ? 'success' : 'neutral'}>
                {ROTULOS_STATUS_MATRICULA[aluno.situacao]}
              </Badge>
            </li>
          ))}
        </ul>
      )}
    </Modal>
  )
}
