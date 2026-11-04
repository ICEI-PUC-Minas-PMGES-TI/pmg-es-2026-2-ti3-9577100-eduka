import { useCallback, useEffect, useState } from 'react'
import { useBlocker } from 'react-router'
import { Button } from '../../../../components/Button/Button'
import { Medidor } from '../../../../components/Dashboard/Dashboard'
import { Modal } from '../../../../components/Modal/Modal'
import { StatCard, StatCards } from '../../../../components/StatCard/StatCard'
import { useCarregamento } from '../../../../hooks/useCarregamento'
import { obterToken } from '../../../../services/authService'
import { listarHorariosDaTurma } from '../../../../services/horarioService'
import type { DisciplinaDaTurma, Turma } from '../../../../types/alocacao'
import type { HorarioAula } from '../../../../types/horario'
import { GradeAlocacao } from '../../components/GradeAlocacao/GradeAlocacao'
import { ModalHorarioDisciplina } from '../../components/ModalHorarioDisciplina/ModalHorarioDisciplina'
import type { OpcaoProfessor } from '../../components/SeletorProfessor/SeletorProfessor'
import { TurmasAlocacao } from '../../components/TurmasAlocacao/TurmasAlocacao'
import { useAlocacoes, type EscolhasDaTurma } from '../../hooks/useAlocacoes'
import { SecretariaLayout } from '../../layouts/SecretariaLayout'
import styles from './Alocacoes.module.css'

const SEM_HORARIOS: HorarioAula[] = []

export function Alocacoes() {
  const { turmas, professores, alocacoes, carregando, erro, recarregar, salvar } = useAlocacoes()
  const [turmaId, setTurmaId] = useState<number | null>(null)
  // Escolhas ainda não gravadas, por turma: trocar de turma não perde o que foi marcado.
  const [rascunhos, setRascunhos] = useState<Record<number, EscolhasDaTurma>>({})
  const [salvando, setSalvando] = useState(false)
  const [sucesso, setSucesso] = useState<string | null>(null)
  const [erroAcao, setErroAcao] = useState<string | null>(null)
  const [editandoHorario, setEditandoHorario] = useState<DisciplinaDaTurma | null>(null)

  // Sem escolha feita (ou com a turma escolhida fora da lista), abre na primeira.
  const turma = turmas.find((t) => t.id === turmaId) ?? turmas[0] ?? null

  const alocadasPorTurma = new Map<number, number>()
  // O que cada professor já leciona, para a secretaria distribuir a carga.
  const cargaPorProfessor = new Map<string, { disciplinas: number; turmas: Set<number> }>()
  for (const alocacao of alocacoes) {
    alocadasPorTurma.set(alocacao.turmaId, (alocadasPorTurma.get(alocacao.turmaId) ?? 0) + 1)
    const carga = cargaPorProfessor.get(alocacao.professorId) ?? { disciplinas: 0, turmas: new Set() }
    carga.disciplinas += 1
    carga.turmas.add(alocacao.turmaId)
    cargaPorProfessor.set(alocacao.professorId, carga)
  }

  function descricaoDaCarga(professorId: string): string {
    const carga = cargaPorProfessor.get(professorId)
    if (!carga) return 'Ainda sem turma'
    const disciplinasDele = carga.disciplinas === 1 ? '1 disciplina' : `${carga.disciplinas} disciplinas`
    const turmasDele = carga.turmas.size === 1 ? '1 turma' : `${carga.turmas.size} turmas`
    return `${disciplinasDele} em ${turmasDele}`
  }

  const ativos = professores.filter((p) => p.ativo)
  const opcoesProfessor: OpcaoProfessor[] = ativos
    .sort((a, b) => a.nome.localeCompare(b.nome, 'pt-BR'))
    .map((p) => ({ id: p.id, nome: p.nome, descricao: descricaoDaCarga(p.id) }))

  const totalDisciplinas = turmas.reduce((soma, t) => soma + t.disciplinas.length, 0)
  const semProfessor = totalDisciplinas - alocacoes.length
  const turmasCompletas = turmas.filter(
    (t) => t.disciplinas.length > 0 && alocadasPorTurma.get(t.id) === t.disciplinas.length,
  ).length
  const percentualGeral = totalDisciplinas > 0 ? Math.round((alocacoes.length / totalDisciplinas) * 100) : 0
  const semTurma = ativos.filter((p) => !cargaPorProfessor.has(p.id)).length

  // Horário das aulas da turma aberta (IU13); a secretaria define por disciplina.
  const turmaAbertaId = turma?.id ?? null
  const buscarHorarios = useCallback(
    (signal: AbortSignal) =>
      turmaAbertaId === null ? Promise.resolve(SEM_HORARIOS) : listarHorariosDaTurma(turmaAbertaId, signal),
    [turmaAbertaId],
  )
  const horariosDaTurma = useCarregamento(buscarHorarios, SEM_HORARIOS, 'Turma não encontrada.')

  const disciplinas = [...(turma?.disciplinas ?? [])].sort((a, b) => a.nome.localeCompare(b.nome, 'pt-BR'))
  const gravadas = new Map<number, OpcaoProfessor>(
    alocacoes
      .filter((a) => a.turmaId === turma?.id)
      .map((a) => [
        a.disciplinaId,
        { id: a.professorId, nome: a.professorNome, descricao: descricaoDaCarga(a.professorId) },
      ]),
  )
  const rascunho = (turma && rascunhos[turma.id]) ?? {}
  const escolhas: EscolhasDaTurma = {}
  for (const disciplina of disciplinas) {
    escolhas[disciplina.id] = rascunho[disciplina.id] ?? gravadas.get(disciplina.id)?.id ?? ''
  }
  const turmasComRascunho = new Set(
    turmas
      .filter((t) => {
        const doRascunho = rascunhos[t.id] ?? {}
        return t.disciplinas.some((d) => {
          const gravado = alocacoes.find((a) => a.turmaId === t.id && a.disciplinaId === d.id)?.professorId ?? ''
          return doRascunho[d.id] !== undefined && doRascunho[d.id] !== gravado
        })
      })
      .map((t) => t.id),
  )
  const mudancas = disciplinas.filter((d) => escolhas[d.id] !== (gravadas.get(d.id)?.id ?? '')).length

  // Escolhas não salvas em qualquer turma: pede confirmação antes de sair da
  // tela, como o diário de notas do professor. O "Sair" da conta já apagou a
  // sessão antes de navegar: esse não é bloqueado, ou a tela ficaria sem sessão.
  const pendente = turmasComRascunho.size > 0
  const blocker = useBlocker(
    ({ currentLocation, nextLocation }) =>
      pendente && obterToken() !== null && currentLocation.pathname !== nextLocation.pathname,
  )
  useEffect(() => {
    if (!pendente) return
    const avisar = (e: BeforeUnloadEvent) => e.preventDefault()
    window.addEventListener('beforeunload', avisar)
    return () => window.removeEventListener('beforeunload', avisar)
  }, [pendente])

  function limparAvisos() {
    setSucesso(null)
    setErroAcao(null)
  }

  function handleSelecionar(selecionada: Turma) {
    limparAvisos()
    setTurmaId(selecionada.id)
  }

  function handleEscolher(disciplinaId: number, professorId: string) {
    if (!turma) return
    limparAvisos()
    setRascunhos((atuais) => ({
      ...atuais,
      [turma.id]: { ...atuais[turma.id], [disciplinaId]: professorId },
    }))
  }

  function descartarRascunho(id: number, manter: EscolhasDaTurma = {}) {
    setRascunhos((atuais) => ({ ...atuais, [id]: manter }))
  }

  function handleDesfazer() {
    if (!turma) return
    limparAvisos()
    descartarRascunho(turma.id)
  }

  async function handleSalvar() {
    if (!turma) return
    limparAvisos()
    setSalvando(true)
    try {
      const falhas = await salvar(turma, escolhas)
      if (falhas.length === 0) {
        descartarRascunho(turma.id)
        setSucesso(`Alocação da turma ${turma.nome} salva.`)
      } else {
        // O que falhou continua marcado, para dar para tentar de novo.
        descartarRascunho(
          turma.id,
          Object.fromEntries(falhas.map((d) => [d.id, escolhas[d.id]])),
        )
        setErroAcao(
          `Não foi possível salvar ${falhas.map((d) => d.nome).join(', ')}. ` +
            'Confira quem está em cada disciplina e tente de novo.',
        )
      }
    } finally {
      setSalvando(false)
    }
  }

  return (
    <SecretariaLayout
      titulo="Alocação de professores"
      subtitulo="Escolha a turma e defina quem dá cada disciplina."
    >
      {carregando && <p className={styles.estado} role="status">Carregando turmas e professores…</p>}

      {!carregando && erro && (
        <div className={styles.estado}>
          <span>{erro}</span>
          <Button size="sm" onClick={recarregar}>
            Tentar novamente
          </Button>
        </div>
      )}

      {!carregando && !erro && !turma && (
        <p className={styles.estado}>
          Nenhuma turma ativa ainda. Cadastre uma turma para alocar os professores.
        </p>
      )}

      {!carregando && !erro && turma && (
        <>
        <StatCards rotulo="Resumo da alocação da escola">
          <StatCard
            label="Grade com professor"
            value={`${percentualGeral}%`}
            descricao={<Medidor valor={alocacoes.length} maximo={totalDisciplinas} rotulo="Grade com professor" />}
            destaque
          />
          <StatCard
            label="Disciplinas sem professor"
            value={semProfessor}
            descricao={semProfessor > 0 ? 'Faltam alocar' : 'Grade completa'}
            atencao={semProfessor > 0}
          />
          <StatCard label="Turmas completas" value={`${turmasCompletas} de ${turmas.length}`} descricao="Todas as disciplinas com professor" />
          <StatCard label="Professores sem turma" value={`${semTurma} de ${ativos.length}`} descricao="Entre os ativos" />
        </StatCards>

        <div className={styles.colunas}>
          <div className={styles.turmas}>
            <TurmasAlocacao
              turmas={turmas}
              turmaSelecionadaId={turma.id}
              alocadasPorTurma={alocadasPorTurma}
              turmasComRascunho={turmasComRascunho}
              onSelecionar={handleSelecionar}
            />
          </div>

          <div className={styles.grade}>
            {sucesso && (
              <p className={styles.sucesso} role="status">
                {sucesso}
              </p>
            )}

            {erroAcao && (
              <p className={styles.alerta} role="alert">
                {erroAcao}
              </p>
            )}

            <GradeAlocacao
              turma={turma}
              disciplinas={disciplinas}
              professores={opcoesProfessor}
              gravadas={gravadas}
              escolhas={escolhas}
              mudancas={mudancas}
              salvando={salvando}
              horarios={horariosDaTurma.dados}
              onEditarHorario={(disciplina) => {
                setSucesso(null)
                setEditandoHorario(disciplina)
              }}
              onEscolher={handleEscolher}
              onDesfazer={handleDesfazer}
              onSalvar={handleSalvar}
            />
          </div>
        </div>
        </>
      )}

      {editandoHorario && turma && (
        <ModalHorarioDisciplina
          turmaId={turma.id}
          turmaNome={turma.nome}
          disciplinaId={editandoHorario.id}
          disciplinaNome={editandoHorario.nome}
          horarios={horariosDaTurma.dados.filter((h) => h.disciplinaId === editandoHorario.id)}
          onFechar={() => setEditandoHorario(null)}
          onSalvo={(daTurma) => {
            horariosDaTurma.atualizar(() => daTurma)
            setSucesso(`Horário de ${editandoHorario.nome} salvo.`)
            setEditandoHorario(null)
          }}
        />
      )}

      {blocker.state === 'blocked' && (
        <Modal
          titulo="Sair sem salvar?"
          tamanho="pequeno"
          onFechar={() => blocker.reset()}
          rodape={
            <>
              <Button onClick={() => blocker.reset()}>Continuar editando</Button>
              <Button variant="danger" onClick={() => blocker.proceed()}>
                Sair sem salvar
              </Button>
            </>
          }
        >
          <p className={styles.avisoSaida}>
            Há professores escolhidos que ainda não foram salvos e essas escolhas serão perdidas.
          </p>
        </Modal>
      )}
    </SecretariaLayout>
  )
}
