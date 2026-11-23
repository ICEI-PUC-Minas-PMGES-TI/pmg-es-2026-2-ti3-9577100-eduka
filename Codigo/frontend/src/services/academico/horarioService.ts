import type { DiaSemana, HorarioTurma } from '../../types/academico/horario'
import { apiRequest } from '../api'

// Grade semanal da turma (IU10 e IU13): cada aula é de uma alocação
// (disciplina e professor). 409 HORARIO_TURMA_CONFLITANTE ou
// HORARIO_PROFESSOR_CONFLITANTE quando sobrepõe outra aula.

export type AulaDaGrade = { diaSemana: DiaSemana; horaInicio: string; horaFim: string }

export function listarHorarioDaTurma(turmaId: number, signal?: AbortSignal): Promise<HorarioTurma[]> {
  return apiRequest<HorarioTurma[]>(`/turmas/${turmaId}/horario`, { signal })
}

export function criarAulaNaGrade(turmaId: number, alocacaoId: string, aula: AulaDaGrade): Promise<HorarioTurma> {
  return apiRequest<HorarioTurma>(`/turmas/${turmaId}/horario`, {
    method: 'POST',
    body: JSON.stringify({ alocacaoId, ...aula }),
  })
}

export function atualizarAulaDaGrade(turmaId: number, horarioId: string, aula: AulaDaGrade): Promise<HorarioTurma> {
  return apiRequest<HorarioTurma>(`/turmas/${turmaId}/horario/${horarioId}`, {
    method: 'PUT',
    body: JSON.stringify(aula),
  })
}

export function removerAulaDaGrade(turmaId: number, horarioId: string): Promise<void> {
  return apiRequest<void>(`/turmas/${turmaId}/horario/${horarioId}`, { method: 'DELETE' })
}
