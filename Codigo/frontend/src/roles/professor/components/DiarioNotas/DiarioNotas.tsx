import { useEffect, useRef, useState, type KeyboardEvent } from 'react'
import { Badge } from '../../../../components/Badge/Badge'
import { Button } from '../../../../components/Button/Button'
import { StatCard, StatCards } from '../../../../components/StatCard/StatCard'
import { lancarNotas } from '../../../../services/portalProfessorService'
import type { AvaliacaoDoProfessor, LancamentoNota, NotaDoAluno } from '../../../../types/avaliacaoProfessor'
import { ROTULOS_STATUS_MATRICULA } from '../../../../types/matricula'
import { ROTULOS_TIPO_AVALIACAO } from '../../../../types/portalAluno'
import { formatarPontos } from '../../../../utils/formatters'
import { formatarData, opcional } from '../../../secretaria/components/PainelSecretaria/formatacao'
import { atingiuMedia, lerPontos, mascararPontos, mensagemErroAvaliacao, pontosParaCampo } from '../../notas'
import styles from './DiarioNotas.module.css'

// Limite da coluna OBSERVACAO de NOTA (V19).
const MAX_OBSERVACAO = 500

type Linha = { nota: string; observacao: string }

type DiarioNotasProps = {
  avaliacao: AvaliacaoDoProfessor
  /** Diário vindo do backend; volta atualizado depois de salvar. */
  notas: NotaDoAluno[]
  /** Avisa se há alteração não salva, para a página não deixar sair sem querer. */
  onPendente: (pendente: boolean) => void
  onSalvo: (notas: NotaDoAluno[]) => void
}

function linhaDe(nota: NotaDoAluno): Linha {
  return { nota: pontosParaCampo(nota.valor), observacao: nota.observacao ?? '' }
}

/** Etiqueta de resultado do aluno na avaliação; sem nota (ou nota inválida), nada. */
function Situacao({ nota, valor }: { nota: number | null; valor: number }) {
  if (nota === null || Number.isNaN(nota)) return <span className={styles.secundario}>—</span>
  return atingiuMedia(nota, valor) ? (
    <Badge tone="success">Aprovado na avaliação</Badge>
  ) : (
    <Badge tone="danger">Abaixo da média</Badge>
  )
}

/** Só aluno com matrícula ativa recebe nota; os demais aparecem para consulta. */
function editavel(nota: NotaDoAluno): boolean {
  return nota.situacaoMatricula === 'ATIVA'
}

/**
 * Diário de notas de uma avaliação: uma linha por aluno, com a nota e a
 * observação editáveis. Salva em lote só as linhas alteradas. Montado com
 * key = avaliação, então trocar de avaliação começa do zero.
 */
export function DiarioNotas({ avaliacao, notas, onPendente, onSalvo }: DiarioNotasProps) {
  const [linhas, setLinhas] = useState<Record<string, Linha>>(() =>
    Object.fromEntries(notas.map((n) => [n.alunoId, linhaDe(n)])),
  )
  const [erros, setErros] = useState<Record<string, string>>({})
  const [salvando, setSalvando] = useState(false)
  const [erroEnvio, setErroEnvio] = useState<string | null>(null)
  const [sucesso, setSucesso] = useState<string | null>(null)
  const tabelaRef = useRef<HTMLTableElement>(null)

  const alterados = notas.filter((n) => {
    const linha = linhas[n.alunoId]
    if (!linha || !editavel(n)) return false
    // Compara o número, não o texto: "8,50" e 8.5 são a mesma nota.
    return lerPontos(linha.nota) !== n.valor || opcional(linha.observacao) !== (n.observacao ?? null)
  })
  const pendente = alterados.length > 0

  useEffect(() => onPendente(pendente), [pendente, onPendente])
  useEffect(() => () => onPendente(false), [onPendente])

  const ativos = notas.filter(editavel)
  const lancadas = ativos.filter((n) => n.valor !== null)
  const media = lancadas.length > 0 ? lancadas.reduce((soma, n) => soma + (n.valor as number), 0) / lancadas.length : null

  function alterar(alunoId: string, campo: keyof Linha, valor: string) {
    setLinhas((atuais) => ({ ...atuais, [alunoId]: { ...atuais[alunoId], [campo]: valor } }))
    if (erros[alunoId]) {
      setErros((atuais) => {
        const resto = { ...atuais }
        delete resto[alunoId]
        return resto
      })
    }
    setSucesso(null)
  }

  function validar(): boolean {
    const novos: Record<string, string> = {}
    for (const n of alterados) {
      const linha = linhas[n.alunoId]
      const nota = lerPontos(linha.nota)
      if (nota !== null && Number.isNaN(nota)) novos[n.alunoId] = 'Nota inválida.'
      else if (nota !== null && nota > avaliacao.valor) {
        novos[n.alunoId] = `Máximo de ${formatarPontos(avaliacao.valor)} pts.`
      } else if (nota === null && opcional(linha.observacao)) {
        novos[n.alunoId] = 'Lance a nota para guardar a observação.'
      }
    }
    setErros(novos)
    return Object.keys(novos).length === 0
  }

  async function salvar() {
    setErroEnvio(null)
    setSucesso(null)
    if (!validar()) {
      requestAnimationFrame(() => tabelaRef.current?.querySelector<HTMLElement>('[aria-invalid="true"]')?.focus())
      return
    }

    const lote: LancamentoNota[] = alterados.map((n) => ({
      alunoId: n.alunoId,
      valor: lerPontos(linhas[n.alunoId].nota),
      observacao: opcional(linhas[n.alunoId].observacao),
    }))
    setSalvando(true)
    try {
      const salvas = await lancarNotas(avaliacao.id, lote)
      // Reescreve os campos como o backend gravou ("8," vira "8").
      setLinhas(Object.fromEntries(salvas.map((n) => [n.alunoId, linhaDe(n)])))
      onSalvo(salvas)
      setSucesso(lote.length === 1 ? '1 nota salva.' : `${lote.length} notas salvas.`)
    } catch (e) {
      setErroEnvio(mensagemErroAvaliacao(e))
    } finally {
      setSalvando(false)
    }
  }

  function descartar() {
    setLinhas(Object.fromEntries(notas.map((n) => [n.alunoId, linhaDe(n)])))
    setErros({})
    setErroEnvio(null)
  }

  // Enter no campo da nota desce para a nota do próximo aluno, como numa planilha.
  function proximaNota(evento: KeyboardEvent<HTMLInputElement>) {
    if (evento.key !== 'Enter') return
    evento.preventDefault()
    const campos = [...(tabelaRef.current?.querySelectorAll<HTMLInputElement>('input[inputmode="decimal"]') ?? [])]
    campos[campos.indexOf(evento.currentTarget) + 1]?.focus()
  }

  return (
    <>
      <section className={styles.cabecalho} aria-label="Avaliação">
        <div className={styles.avaliacao}>
          <span className={styles.tipo}>{ROTULOS_TIPO_AVALIACAO[avaliacao.tipo]}</span>
          <h2 className={styles.titulo}>{avaliacao.titulo}</h2>
          <span className={styles.subtexto}>
            {formatarData(avaliacao.data)} · vale {formatarPontos(avaliacao.valor)} pts
          </span>
        </div>
        <StatCards rotulo="Resumo das notas">
          <StatCard
            label="Notas lançadas"
            value={`${lancadas.length} de ${ativos.length}`}
            descricao={ativos.length > 0 && lancadas.length === ativos.length ? 'Turma completa' : 'Alunos ativos'}
            destaque
          />
          <StatCard
            label="Média da turma"
            value={media === null ? '—' : formatarPontos(Math.round(media * 100) / 100)}
            descricao={`de ${formatarPontos(avaliacao.valor)} pts`}
          />
        </StatCards>
      </section>

      {notas.length === 0 ? (
        <div className={styles.vazio}>
          <strong>Nenhum aluno matriculado nesta turma.</strong>
          <span>Quando a secretaria matricular os alunos, eles aparecem aqui para receber nota.</span>
        </div>
      ) : (
        <section className={styles.card} aria-label="Notas dos alunos">
          <div className={styles.rolagem}>
            <table ref={tabelaRef} className={styles.tabela}>
              <thead>
                <tr>
                  <th>Aluno</th>
                  <th className={styles.colunaNota}>Nota</th>
                  <th className={styles.colunaSituacao}>Situação</th>
                  <th>Observação</th>
                </tr>
              </thead>
              <tbody>
                {notas.map((n) => {
                  const linha = linhas[n.alunoId] ?? linhaDe(n)
                  const erro = erros[n.alunoId]
                  const erroId = `nota-erro-${n.alunoId}`

                  if (!editavel(n)) {
                    return (
                      <tr key={n.alunoId} className={styles.inativo}>
                        <td className={styles.celAluno}>
                          <span className={styles.aluno}>{n.alunoNome}</span>{' '}
                          <Badge tone="neutral">
                            {n.situacaoMatricula ? ROTULOS_STATUS_MATRICULA[n.situacaoMatricula] : 'Saiu da turma'}
                          </Badge>
                          <span className={styles.numero}>Nº {n.alunoNumeroMatricula}</span>
                        </td>
                        {/* data-rotulo: no card estreito, as colunas viram linhas e o rótulo vai junto. */}
                        <td className={`${styles.colunaNota} ${styles.celNota}`} data-rotulo="Nota">
                          {n.valor === null ? '—' : formatarPontos(n.valor)}
                        </td>
                        <td className={`${styles.colunaSituacao} ${styles.celSituacao}`}>
                          <Situacao nota={n.valor} valor={avaliacao.valor} />
                        </td>
                        <td className={`${styles.secundario} ${styles.celObservacao}`} data-rotulo="Observação">
                          {n.observacao ?? '—'}
                        </td>
                      </tr>
                    )
                  }

                  return (
                    <tr key={n.alunoId} className={alterados.includes(n) ? styles.alterado : undefined}>
                      <td className={styles.celAluno}>
                        <span className={styles.aluno}>{n.alunoNome}</span>
                        <span className={styles.numero}>Nº {n.alunoNumeroMatricula}</span>
                      </td>
                      <td className={`${styles.colunaNota} ${styles.celNota}`} data-rotulo="Nota">
                        <div className={styles.nota}>
                          <input
                            className={`${styles.campo} ${styles.campoNota}`}
                            inputMode="decimal"
                            placeholder="—"
                            aria-label={`Nota de ${n.alunoNome}`}
                            aria-invalid={Boolean(erro)}
                            aria-describedby={erro ? erroId : undefined}
                            value={linha.nota}
                            disabled={salvando}
                            onChange={(e) => alterar(n.alunoId, 'nota', mascararPontos(e.target.value))}
                            onKeyDown={proximaNota}
                          />
                          <span>/ {formatarPontos(avaliacao.valor)}</span>
                        </div>
                        {erro && (
                          <span id={erroId} className={styles.erro}>
                            {erro}
                          </span>
                        )}
                      </td>
                      <td className={`${styles.colunaSituacao} ${styles.celSituacao}`}>
                        <Situacao nota={lerPontos(linha.nota)} valor={avaliacao.valor} />
                      </td>
                      <td className={styles.celObservacao} data-rotulo="Observação">
                        <input
                          className={styles.campo}
                          maxLength={MAX_OBSERVACAO}
                          placeholder="Opcional"
                          aria-label={`Observação de ${n.alunoNome}`}
                          value={linha.observacao}
                          disabled={salvando}
                          onChange={(e) => alterar(n.alunoId, 'observacao', e.target.value)}
                        />
                      </td>
                    </tr>
                  )
                })}
              </tbody>
            </table>
          </div>

          <footer className={styles.rodape}>
            <div className={styles.status} aria-live="polite">
              {erroEnvio && (
                <span className={styles.erroEnvio} role="alert">
                  {erroEnvio}
                </span>
              )}
              {!erroEnvio && sucesso && <span className={styles.sucesso}>{sucesso}</span>}
              {!erroEnvio && !sucesso && (
                <span>
                  {pendente
                    ? `${alterados.length} ${alterados.length === 1 ? 'alteração não salva' : 'alterações não salvas'}`
                    : 'Deixe a nota vazia para apagá-la.'}
                </span>
              )}
            </div>
            <div className={styles.botoes}>
              <Button disabled={!pendente || salvando} onClick={descartar}>
                Descartar
              </Button>
              <Button variant="accent" icon="check" disabled={!pendente || salvando} onClick={salvar}>
                {salvando ? 'Salvando…' : 'Salvar notas'}
              </Button>
            </div>
          </footer>
        </section>
      )}
    </>
  )
}
