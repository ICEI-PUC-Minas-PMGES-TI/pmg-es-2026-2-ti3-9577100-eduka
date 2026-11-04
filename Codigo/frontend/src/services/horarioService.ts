import type { HorarioAula } from '../types/horario'
import { apiRequest } from './api'

// Horário das aulas de cada disciplina da turma (IU10 e IU13).

export function listarHorariosDaTurma(turmaId: number, signal?: AbortSignal): Promise<HorarioAula[]> {
  return apiRequest<HorarioAula[]>(`/turmas/${turmaId}/horarios`, { signal })
}

/**
 * Substitui o horário da disciplina na turma e devolve o da turma toda. 400
 * se a turma já tem outra aula no mesmo dia e hora.
 */
export function definirHorariosDaDisciplina(
  turmaId: number,
  disciplinaId: number,
  horarios: { diaSemana: number; horaInicio: string }[],
): Promise<HorarioAula[]> {
  return apiRequest<HorarioAula[]>(`/turmas/${turmaId}/disciplinas/${disciplinaId}/horarios`, {
    method: 'PUT',
    body: JSON.stringify({ horarios }),
  })
}
