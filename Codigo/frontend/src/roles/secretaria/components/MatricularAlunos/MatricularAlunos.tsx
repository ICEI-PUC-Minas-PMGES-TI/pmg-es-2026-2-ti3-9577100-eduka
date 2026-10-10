import { useState } from 'react'
import { Badge } from '../../../../components/Badge/Badge'
import { Button } from '../../../../components/Button/Button'
import { Icon } from '../../../../components/Icon/Icon'
import { mensagemErroMatricula } from '../../../../services/matriculaService'
import type { TurmaAcademica } from '../../../../types/academico'
import type { AlunoResumo } from '../../../../types/aluno'
import {
  ROTULOS_STATUS_MATRICULA,
  TOM_DO_STATUS,
  type Matricula,
  type MatriculaRequest,
  type StatusMatricula,
} from '../../../../types/matricula'
import { iniciais, plural } from '../../../../utils/formatters'
import styles from './MatricularAlunos.module.css'

/** Resultado de uma matrícula em lote: quem entrou e quem foi recusado, com o motivo. */
export type ResultadoMatriculas = {
  matriculados: string[]
  falhas: { nome: string; motivo: string }[]
}

type MatricularAlunosProps = {
  turma: TurmaAcademica
  alunos: AlunoResumo[]
  /** Todas as matrículas da escola: marcam quem já está na turma e onde cada aluno estuda. */
  matriculas: Matricula[]
  vagasLivres: number
  /** Matricula um aluno; deve lançar ApiError em caso de falha. */
  onMatricular: (dados: MatriculaRequest) => Promise<Matricula>
  onConcluido: (resultado: ResultadoMatriculas) => void
}

function corresponde(aluno: AlunoResumo, busca: string): boolean {
  const termo = busca.trim().toLowerCase()
  if (!termo) return true
  return aluno.nome.toLowerCase().includes(termo) || aluno.matricula.toLowerCase().includes(termo)
}

/**
 * Passo 2 da tela de matrículas: os alunos da escola, cada um com o botão
 * "Matricular" (que só marca), e "Confirmar matrículas" no fim, que grava os
 * marcados. Quem já tem registro na turma aparece com a situação, sem botão:
 * o backend aceita um registro por aluno e turma. Remonte (key) ao trocar de
 * turma para começar sem ninguém marcado.
 */
export function MatricularAlunos({
  turma,
  alunos,
  matriculas,
  vagasLivres,
  onMatricular,
  onConcluido,
}: MatricularAlunosProps) {
  const [busca, setBusca] = useState('')
  const [selecionados, setSelecionados] = useState<string[]>([])
  const [enviando, setEnviando] = useState(false)

  const naTurma = new Map<string, StatusMatricula>(
    matriculas.filter((m) => m.turmaId === turma.id).map((m) => [m.alunoId, m.status]),
  )
  // De onde o aluno vem: a turma ativa dele em outro lugar, se houver.
  const outraTurmaAtiva = new Map(
    matriculas.filter((m) => m.status === 'ATIVA' && m.turmaId !== turma.id).map((m) => [m.alunoId, m.turmaNome]),
  )
  // Quem pode entrar vem primeiro; quem já está na turma, no fim.
  const visiveis = alunos
    .filter((a) => corresponde(a, busca))
    .sort((a, b) => Number(naTurma.has(a.id)) - Number(naTurma.has(b.id)))
  const limiteAtingido = selecionados.length >= vagasLivres
  const podeMatricular = turma.ativa && vagasLivres > 0

  function alternar(id: string) {
    setSelecionados((atuais) => (atuais.includes(id) ? atuais.filter((a) => a !== id) : [...atuais, id]))
  }

  async function confirmar() {
    setEnviando(true)
    const resultado: ResultadoMatriculas = { matriculados: [], falhas: [] }
    // Em sequência, não em paralelo: cada uma ocupa vaga, e o backend conta
    // as vagas na hora de gravar.
    for (const alunoId of selecionados) {
      const nome = alunos.find((a) => a.id === alunoId)?.nome ?? 'Aluno'
      try {
        await onMatricular({ alunoId, turmaId: turma.id, dataMatricula: null, observacao: null })
        resultado.matriculados.push(nome)
      } catch (e) {
        resultado.falhas.push({ nome, motivo: mensagemErroMatricula(e) })
      }
    }
    setSelecionados([])
    setEnviando(false)
    onConcluido(resultado)
  }

  return (
    <section className={styles.card} aria-label={`Matricular alunos na turma ${turma.nome}`}>
      <header className={styles.topo}>
        <h3 className={styles.titulo}>Matricule os alunos</h3>
        <span className={styles.vagas}>
          {turma.ativa
            ? `${vagasLivres} ${plural(vagasLivres, 'vaga livre', 'vagas livres')}`
            : 'Turma inativa: não recebe matrícula'}
        </span>
      </header>

      <label className={styles.busca}>
        <Icon name="search" size={16} />
        <input
          type="search"
          placeholder="Procurar aluno pelo nome ou número de matrícula"
          aria-label="Procurar aluno"
          value={busca}
          onChange={(e) => setBusca(e.target.value)}
        />
      </label>

      {alunos.length === 0 ? (
        <p className={styles.vazio}>Nenhum aluno cadastrado ainda.</p>
      ) : visiveis.length === 0 ? (
        <p className={styles.vazio}>Nenhum aluno encontrado para a busca.</p>
      ) : (
        <ul className={styles.lista}>
          {visiveis.map((aluno) => {
            const status = naTurma.get(aluno.id)
            const marcado = selecionados.includes(aluno.id)
            const origem = outraTurmaAtiva.get(aluno.id)
            return (
              <li key={aluno.id} className={`${styles.linha} ${marcado ? styles.marcada : ''}`}>
                <span className={styles.avatar} aria-hidden="true">
                  {iniciais(aluno.nome)}
                </span>
                <span className={styles.aluno}>
                  <strong className={styles.nome}>{aluno.nome}</strong>
                  <span className={styles.detalhe}>
                    Matrícula {aluno.matricula} ·{' '}
                    {status ? 'nesta turma' : origem ? `ativo em ${origem}` : 'sem turma ativa'}
                  </span>
                </span>
                {status ? (
                  <Badge tone={TOM_DO_STATUS[status]} icon={status === 'ATIVA' ? 'check' : undefined}>
                    {status === 'ATIVA' ? 'Matriculado' : ROTULOS_STATUS_MATRICULA[status]}
                  </Badge>
                ) : (
                  <Button
                    size="sm"
                    variant={marcado ? 'accent' : 'primary'}
                    icon={marcado ? 'check' : undefined}
                    aria-pressed={marcado}
                    disabled={enviando || !podeMatricular || (!marcado && limiteAtingido)}
                    title={!marcado && limiteAtingido && podeMatricular ? 'Todas as vagas livres já foram escolhidas' : undefined}
                    onClick={() => alternar(aluno.id)}
                  >
                    {marcado ? 'Selecionado' : 'Matricular'}
                  </Button>
                )}
              </li>
            )
          })}
        </ul>
      )}

      <footer className={styles.rodape}>
        <span className={styles.contagem}>
          {selecionados.length} {plural(selecionados.length, 'aluno selecionado', 'alunos selecionados')} para
          matrícula
        </span>
        <Button variant="primary" icon="check" disabled={selecionados.length === 0 || enviando} onClick={confirmar}>
          {enviando ? 'Matriculando…' : 'Confirmar matrículas'}
        </Button>
      </footer>
    </section>
  )
}
