import { useState } from 'react'
import { Button } from '../../../../components/Button/Button'
import { Icon } from '../../../../components/Icon/Icon'
import { Paginacao } from '../../../../components/Paginacao/Paginacao'
import { usePaginacao } from '../../../../hooks/usePaginacao'
import { mensagemErroMatricula } from '../../../../services/matriculaService'
import type { TurmaAcademica } from '../../../../types/academico'
import type { AlunoDaLista, AlunoResumo } from '../../../../types/aluno'
import type { Matricula, MatriculaRequest } from '../../../../types/matricula'
import { iniciais, plural } from '../../../../utils/formatters'
import { Dica } from '../../../../components/Dica/Dica'
import styles from './MatricularAlunos.module.css'

/** Resultado de uma matrícula em lote: quem entrou e quem foi recusado, com o motivo. */
export type ResultadoMatriculas = {
  matriculados: string[]
  falhas: { nome: string; motivo: string }[]
}

type MatricularAlunosProps = {
  turma: TurmaAcademica
  /** Com a turma ativa de cada um, para mostrar de onde o aluno vem. */
  alunos: AlunoDaLista[]
  /** As matrículas desta turma, em qualquer situação: quem já tem uma não aparece aqui. */
  matriculasDaTurma: Matricula[]
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
 * Aba "Matricular alunos" da turma: só quem ainda pode entrar nela (quem já tem
 * registro na turma fica na aba "Alunos da turma", porque o backend aceita um
 * registro por aluno e turma). Quem está sem turma vem primeiro. "Matricular"
 * só marca, em qualquer página; "Confirmar matrículas" grava os marcados.
 * Remonte (key) ao trocar de turma para começar sem ninguém marcado.
 */
export function MatricularAlunos({
  turma,
  alunos,
  matriculasDaTurma,
  vagasLivres,
  onMatricular,
  onConcluido,
}: MatricularAlunosProps) {
  const [busca, setBusca] = useState('')
  const [selecionados, setSelecionados] = useState<string[]>([])
  const [enviando, setEnviando] = useState(false)

  const naTurma = new Set(matriculasDaTurma.map((m) => m.alunoId))
  // De onde o aluno vem: a turma ativa dele em outro lugar, se houver.
  const outraTurmaAtiva = new Map(
    alunos
      .filter((a) => a.situacao === 'MATRICULADO' && a.turmaId !== turma.id)
      .map((a) => [a.id, a.turmaNome]),
  )
  // Quem pode entrar: sem registro nesta turma e com cadastro ativo. Sem turma primeiro.
  const disponiveis = alunos
    .filter((a) => !naTurma.has(a.id) && a.situacao !== 'ANONIMIZADO')
    .sort((a, b) => Number(a.situacao !== 'SEM_TURMA') - Number(b.situacao !== 'SEM_TURMA'))
  const visiveis = disponiveis.filter((a) => corresponde(a, busca))
  const { paginacao, topo } = usePaginacao<AlunoDaLista, HTMLDivElement>(visiveis, { reiniciarCom: busca })
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
    <div ref={topo} className={styles.conteudo}>
      <div className={styles.topo}>
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
        <span className={`${styles.vagas} ${podeMatricular ? '' : styles.semVaga}`}>
          {!turma.ativa
            ? 'Turma inativa: não recebe matrícula'
            : `${vagasLivres} ${plural(vagasLivres, 'vaga livre', 'vagas livres')}`}
        </span>
      </div>

      {disponiveis.length === 0 ? (
        <p className={styles.vazio}>Todos os alunos da escola já estão nesta turma.</p>
      ) : visiveis.length === 0 ? (
        <p className={styles.vazio}>Nenhum aluno encontrado para a busca.</p>
      ) : (
        <ul className={styles.lista} aria-label={`Alunos que podem entrar na turma ${turma.nome}`}>
          {paginacao.itensDaPagina.map((aluno) => {
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
                    Matrícula {aluno.matricula} · {origem ? `ativo em ${origem}` : 'sem turma ativa'}
                  </span>
                </span>
                <Dica
                  texto={
                    !marcado && limiteAtingido && podeMatricular ? 'Todas as vagas livres já foram escolhidas' : null
                  }
                >
                  <Button
                    size="sm"
                    variant={marcado ? 'accent' : 'primary'}
                    icon={marcado ? 'check' : undefined}
                    aria-pressed={marcado}
                    disabled={enviando || !podeMatricular || (!marcado && limiteAtingido)}
                    onClick={() => alternar(aluno.id)}
                  >
                    {marcado ? 'Selecionado' : 'Matricular'}
                  </Button>
                </Dica>
              </li>
            )
          })}
        </ul>
      )}

      <Paginacao paginacao={paginacao} rotulo="Páginas dos alunos para matricular" />

      <footer className={styles.rodape}>
        <span className={styles.contagem}>
          {selecionados.length} {plural(selecionados.length, 'aluno selecionado', 'alunos selecionados')} para
          matrícula
        </span>
        <Button variant="primary" icon="check" disabled={selecionados.length === 0 || enviando} onClick={confirmar}>
          {enviando ? 'Matriculando…' : 'Confirmar matrículas'}
        </Button>
      </footer>
    </div>
  )
}
