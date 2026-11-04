import { useState, type FormEvent } from 'react'
import { Button } from '../../../../components/Button/Button'
import { Modal } from '../../../../components/Modal/Modal'
import { ApiError } from '../../../../services/api'
import { definirHorariosDaDisciplina } from '../../../../services/horarioService'
import { DIAS_SEMANA, horaCurta, type HorarioAula } from '../../../../types/horario'
import styles from './ModalHorarioDisciplina.module.css'

type Linha = { diaSemana: number; horaInicio: string }

type ModalHorarioDisciplinaProps = {
  turmaId: number
  turmaNome: string
  disciplinaId: number
  disciplinaNome: string
  /** Horário atual da disciplina na turma. */
  horarios: HorarioAula[]
  onSalvo: (daTurma: HorarioAula[]) => void
  onFechar: () => void
}

function mensagemErro(erro: unknown): string {
  if (erro instanceof ApiError) {
    const detalhe = erro.detalhe?.toLowerCase() ?? ''
    if (detalhe.includes('outra aula')) return 'A turma já tem outra aula num desses dias e horários.'
    if (detalhe.includes('repetido')) return 'Há aulas repetidas no mesmo dia e horário.'
    return erro.detalhe ?? erro.message
  }
  return 'Erro inesperado ao salvar o horário.'
}

/**
 * Horário da disciplina na turma (IU10, para a IU13): uma linha por aula
 * semanal, com o dia e a hora de início. Grava na hora, independente da
 * alocação do professor.
 */
export function ModalHorarioDisciplina({
  turmaId,
  turmaNome,
  disciplinaId,
  disciplinaNome,
  horarios,
  onSalvo,
  onFechar,
}: ModalHorarioDisciplinaProps) {
  const [linhas, setLinhas] = useState<Linha[]>(() =>
    horarios.map((h) => ({ diaSemana: h.diaSemana, horaInicio: horaCurta(h.horaInicio) })),
  )
  const [salvando, setSalvando] = useState(false)
  const [erro, setErro] = useState<string | null>(null)

  function alterar(indice: number, campo: keyof Linha, valor: string) {
    setErro(null)
    setLinhas((atuais) =>
      atuais.map((l, i) => (i === indice ? { ...l, [campo]: campo === 'diaSemana' ? Number(valor) : valor } : l)),
    )
  }

  async function enviar(evento: FormEvent<HTMLFormElement>) {
    evento.preventDefault()
    if (linhas.some((l) => !l.horaInicio)) {
      setErro('Informe a hora de início de cada aula.')
      return
    }
    setErro(null)
    setSalvando(true)
    try {
      onSalvo(await definirHorariosDaDisciplina(turmaId, disciplinaId, linhas))
    } catch (e) {
      setErro(mensagemErro(e))
      setSalvando(false)
    }
  }

  return (
    <Modal
      titulo={`Horário · ${disciplinaNome}`}
      subtitulo={turmaNome}
      tamanho="medio"
      ocupado={salvando}
      onFechar={onFechar}
      onEnviar={enviar}
      rodape={
        <>
          <Button onClick={onFechar} disabled={salvando}>
            Cancelar
          </Button>
          <Button type="submit" variant="primary" disabled={salvando}>
            {salvando ? 'Salvando…' : 'Salvar horário'}
          </Button>
        </>
      }
    >
      {linhas.length === 0 && <p className={styles.vazio}>Nenhuma aula definida para esta disciplina.</p>}
      <ul className={styles.linhas}>
        {linhas.map((linha, indice) => (
          <li key={indice} className={styles.linha}>
            <select
              className={styles.campo}
              aria-label={`Dia da aula ${indice + 1}`}
              value={linha.diaSemana}
              onChange={(e) => alterar(indice, 'diaSemana', e.target.value)}
            >
              {DIAS_SEMANA.map((d) => (
                <option key={d.dia} value={d.dia}>
                  {d.longo}
                </option>
              ))}
            </select>
            <input
              type="time"
              className={styles.campo}
              aria-label={`Hora da aula ${indice + 1}`}
              value={linha.horaInicio}
              onChange={(e) => alterar(indice, 'horaInicio', e.target.value)}
            />
            <Button
              size="sm"
              variant="dangerOutline"
              icon="trash"
              aria-label={`Remover aula ${indice + 1}`}
              onClick={() => setLinhas((atuais) => atuais.filter((_, i) => i !== indice))}
            />
          </li>
        ))}
      </ul>
      <Button
        size="sm"
        icon="plus"
        onClick={() => setLinhas((atuais) => [...atuais, { diaSemana: 1, horaInicio: '07:30' }])}
      >
        Adicionar aula
      </Button>
      {erro && (
        <p className={styles.erro} role="alert">
          {erro}
        </p>
      )}
    </Modal>
  )
}
