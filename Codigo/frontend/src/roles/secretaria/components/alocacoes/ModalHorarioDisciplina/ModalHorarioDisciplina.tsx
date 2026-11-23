import { useState, type FormEvent } from 'react'
import { Select } from '../../../../../components/formulario/Select/Select'
import { Button } from '../../../../../components/ui/Button/Button'
import { Modal } from '../../../../../components/modal/Modal/Modal'
import { ApiError } from '../../../../../services/api'
import { atualizarAulaDaGrade, criarAulaNaGrade, removerAulaDaGrade } from '../../../../../services/academico/horarioService'
import { DIAS_SEMANA, horaCurta, type DiaSemana, type HorarioTurma } from '../../../../../types/academico/horario'
import styles from './ModalHorarioDisciplina.module.css'

const OPCOES_DIA = DIAS_SEMANA.map((d) => ({ valor: d.dia, rotulo: d.longo }))

/** Uma aula no formulário; sem id ainda não está na grade. */
type Linha = { id?: string; diaSemana: DiaSemana; horaInicio: string; horaFim: string }

type ModalHorarioDisciplinaProps = {
  turmaId: number
  turmaNome: string
  disciplinaNome: string
  /** A alocação (disciplina e professor) a que as aulas pertencem. */
  alocacaoId: string
  /** As aulas da disciplina já na grade da turma. */
  horarios: HorarioTurma[]
  /** Gravou: a tela relê a grade. */
  onSalvo: () => void
  onFechar: () => void
}

function mensagemErro(erro: unknown): string {
  if (erro instanceof ApiError) {
    if (erro.codigo === 'HORARIO_TURMA_CONFLITANTE') return 'A turma já tem outra aula nesse dia e horário.'
    if (erro.codigo === 'HORARIO_PROFESSOR_CONFLITANTE') {
      return 'O professor já tem aula em outra turma nesse dia e horário.'
    }
    return erro.detalhe ?? erro.message
  }
  return 'Erro inesperado ao salvar o horário.'
}

function linhaDe(h: HorarioTurma): Linha {
  return { id: h.id, diaSemana: h.diaSemana, horaInicio: horaCurta(h.horaInicio), horaFim: horaCurta(h.horaFim) }
}

/**
 * Horário da disciplina na grade da turma (IU10, para a IU13): uma linha por
 * aula semanal, com o dia e as horas. Grava na hora, só o que mudou.
 */
export function ModalHorarioDisciplina({
  turmaId,
  turmaNome,
  disciplinaNome,
  alocacaoId,
  horarios,
  onSalvo,
  onFechar,
}: ModalHorarioDisciplinaProps) {
  const [linhas, setLinhas] = useState<Linha[]>(() => horarios.map(linhaDe))
  const [salvando, setSalvando] = useState(false)
  const [erro, setErro] = useState<string | null>(null)

  function alterar(indice: number, campo: 'diaSemana' | 'horaInicio' | 'horaFim', valor: string) {
    setErro(null)
    setLinhas((atuais) => atuais.map((l, i) => (i === indice ? { ...l, [campo]: valor } : l)))
  }

  async function enviar(evento: FormEvent<HTMLFormElement>) {
    evento.preventDefault()
    if (linhas.some((l) => !l.horaInicio || !l.horaFim)) {
      setErro('Informe o início e o fim de cada aula.')
      return
    }
    if (linhas.some((l) => l.horaFim <= l.horaInicio)) {
      setErro('O fim da aula precisa ser depois do início.')
      return
    }
    setErro(null)
    setSalvando(true)
    try {
      // Só o que mudou: remove as tiradas, altera as mexidas e cria as novas.
      const ficam = new Set(linhas.flatMap((l) => (l.id ? [l.id] : [])))
      for (const h of horarios.filter((h) => !ficam.has(h.id))) await removerAulaDaGrade(turmaId, h.id)
      for (const l of linhas) {
        const aula = { diaSemana: l.diaSemana, horaInicio: l.horaInicio, horaFim: l.horaFim }
        const antes = l.id ? horarios.find((h) => h.id === l.id) : undefined
        if (!l.id) await criarAulaNaGrade(turmaId, alocacaoId, aula)
        else if (antes && JSON.stringify(linhaDe(antes)) !== JSON.stringify(l)) {
          await atualizarAulaDaGrade(turmaId, l.id, aula)
        }
      }
      onSalvo()
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
      {linhas.length === 0 && <p className={styles.vazio}>Nenhuma aula desta disciplina na grade.</p>}
      <ul className={styles.linhas}>
        {linhas.map((linha, indice) => (
          <li key={linha.id ?? `nova-${indice}`} className={styles.linha}>
            <Select
              className={styles.campo}
              aria-label={`Dia da aula ${indice + 1}`}
              opcoes={OPCOES_DIA}
              valor={linha.diaSemana}
              onChange={(dia) => alterar(indice, 'diaSemana', dia)}
            />
            <input
              type="time"
              className={styles.campo}
              aria-label={`Início da aula ${indice + 1}`}
              value={linha.horaInicio}
              onChange={(e) => alterar(indice, 'horaInicio', e.target.value)}
            />
            <input
              type="time"
              className={styles.campo}
              aria-label={`Fim da aula ${indice + 1}`}
              value={linha.horaFim}
              onChange={(e) => alterar(indice, 'horaFim', e.target.value)}
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
        onClick={() =>
          setLinhas((atuais) => [...atuais, { diaSemana: 'SEGUNDA', horaInicio: '07:30', horaFim: '08:20' }])
        }
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
