import { useState } from 'react'
import { Campo } from '../../../../components/Campo/Campo'
import { CampoData } from '../../../../components/CampoData/CampoData'
import { Icon } from '../../../../components/Icon/Icon'
import { mensagemErroMatricula } from '../../../../services/matriculaService'
import type { TurmaAcademica } from '../../../../types/academico'
import type { AlunoResumo } from '../../../../types/aluno'
import type { Matricula, MatriculaRequest } from '../../../../types/matricula'
import { hojeIso } from '../../../../utils/formatters'
import { formatarData } from '../PainelSecretaria/formatacao'
import { ModalCadastro } from '../PainelSecretaria/ModalCadastro'
import styles from './MatricularAlunosModal.module.css'

const MAX_OBSERVACAO = 500

/** Resultado de uma matrícula em lote: quem entrou e quem foi recusado, com o motivo. */
export type ResultadoMatriculas = {
  matriculados: string[]
  falhas: { nome: string; motivo: string }[]
}

type MatricularAlunosModalProps = {
  turma: TurmaAcademica
  alunos: AlunoResumo[]
  /** Todas as matrículas da escola: tiram da lista quem já está na turma. */
  matriculas: Matricula[]
  vagasLivres: number
  onFechar: () => void
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
 * Matrícula de um ou mais alunos na turma. Montado só enquanto está aberto,
 * então cada abertura começa limpa. As matrículas vão uma a uma: se uma for
 * recusada (ex.: a turma encheu nesse meio-tempo), as outras seguem, e o
 * resultado diz quem entrou e quem não.
 */
export function MatricularAlunosModal({
  turma,
  alunos,
  matriculas,
  vagasLivres,
  onFechar,
  onMatricular,
  onConcluido,
}: MatricularAlunosModalProps) {
  const [busca, setBusca] = useState('')
  const [selecionados, setSelecionados] = useState<string[]>([])
  const [dataMatricula, setDataMatricula] = useState(hojeIso())
  const [observacao, setObservacao] = useState('')
  const [erroSelecao, setErroSelecao] = useState<string | undefined>()

  // O backend aceita um registro por aluno e turma, em qualquer situação:
  // quem já tem (até cancelada) sai da lista. Para voltar, reativa-se a dela.
  const jaNaTurma = new Set(matriculas.filter((m) => m.turmaId === turma.id).map((m) => m.alunoId))
  // Aviso, não bloqueio: aluno que já está ativo em outra turma do mesmo período.
  const outraTurmaAtiva = new Map(
    matriculas
      .filter((m) => m.status === 'ATIVA' && m.turmaId !== turma.id && m.periodoLetivoId === turma.periodoLetivoId)
      .map((m) => [m.alunoId, m.turmaNome]),
  )
  const disponiveis = alunos.filter((a) => !jaNaTurma.has(a.id))
  const visiveis = disponiveis.filter((a) => corresponde(a, busca))
  const limiteAtingido = selecionados.length >= vagasLivres

  function alternar(id: string) {
    setErroSelecao(undefined)
    setSelecionados((atuais) => (atuais.includes(id) ? atuais.filter((a) => a !== id) : [...atuais, id]))
  }

  function validar(): boolean {
    const erro = selecionados.length === 0 ? 'Selecione pelo menos um aluno.' : undefined
    setErroSelecao(erro)
    return !erro
  }

  async function enviar() {
    const resultado: ResultadoMatriculas = { matriculados: [], falhas: [] }
    // Em sequência, não em paralelo: cada uma ocupa vaga, e o backend conta
    // as vagas na hora de gravar.
    for (const alunoId of selecionados) {
      const aluno = alunos.find((a) => a.id === alunoId)
      const nome = aluno?.nome ?? 'Aluno'
      try {
        await onMatricular({
          alunoId,
          turmaId: turma.id,
          dataMatricula: dataMatricula || null,
          observacao: observacao.trim() || null,
        })
        resultado.matriculados.push(nome)
      } catch (e) {
        resultado.falhas.push({ nome, motivo: mensagemErroMatricula(e) })
      }
    }
    onConcluido(resultado)
  }

  return (
    <ModalCadastro
      titulo={`Matricular na turma ${turma.nome}`}
      rotuloSalvar={selecionados.length > 1 ? `Matricular ${selecionados.length} alunos` : 'Matricular aluno'}
      onFechar={onFechar}
      validar={validar}
      onEnviar={enviar}
      traduzirErro={mensagemErroMatricula}
    >
      <p className={styles.resumo}>
        {turma.cursoNome} · {turma.periodoLetivoNome} ·{' '}
        <strong>{vagasLivres === 1 ? '1 vaga livre' : `${vagasLivres} vagas livres`}</strong>
      </p>

      <fieldset className={styles.selecao} aria-invalid={Boolean(erroSelecao)} aria-describedby="erro-selecao">
        <legend className={styles.legenda}>
          Alunos
          <span className={styles.contagem}>
            {selecionados.length} de {vagasLivres} selecionados
          </span>
        </legend>

        <label className={styles.busca}>
          <Icon name="search" size={16} />
          <input
            type="search"
            placeholder="Procurar pelo nome ou número de matrícula"
            aria-label="Procurar aluno"
            value={busca}
            onChange={(e) => setBusca(e.target.value)}
          />
        </label>

        {disponiveis.length === 0 ? (
          <p className={styles.vazio}>Todos os alunos da escola já têm matrícula nesta turma.</p>
        ) : visiveis.length === 0 ? (
          <p className={styles.vazio}>Nenhum aluno encontrado para a busca.</p>
        ) : (
          <ul className={styles.lista}>
            {visiveis.map((aluno) => {
              const marcado = selecionados.includes(aluno.id)
              const emOutra = outraTurmaAtiva.get(aluno.id)
              return (
                <li key={aluno.id}>
                  <label className={`${styles.opcao} ${marcado ? styles.marcada : ''}`}>
                    <input
                      type="checkbox"
                      checked={marcado}
                      disabled={!marcado && limiteAtingido}
                      onChange={() => alternar(aluno.id)}
                    />
                    <span className={styles.aluno}>
                      <span className={styles.nome}>{aluno.nome}</span>
                      <span className={styles.numero}>Nº {aluno.matricula}</span>
                    </span>
                    {emOutra && <span className={styles.aviso}>Ativo em {emOutra}</span>}
                  </label>
                </li>
              )
            })}
          </ul>
        )}

        {limiteAtingido && selecionados.length > 0 && (
          <p className={styles.limite}>Todas as vagas livres da turma já estão selecionadas.</p>
        )}
        {erroSelecao && (
          <p id="erro-selecao" className={styles.erro}>
            {erroSelecao}
          </p>
        )}
      </fieldset>

      <Campo label="Data da matrícula" dica={`Padrão: hoje (${formatarData(hojeIso())}).`}>
        {(props) => (
          <CampoData {...props} name="dataMatricula" valor={dataMatricula} onChange={setDataMatricula} />
        )}
      </Campo>
      <Campo label="Observação">
        {(props) => (
          <input
            {...props}
            name="observacao"
            maxLength={MAX_OBSERVACAO}
            placeholder="Opcional"
            value={observacao}
            onChange={(e) => setObservacao(e.target.value)}
          />
        )}
      </Campo>
    </ModalCadastro>
  )
}
