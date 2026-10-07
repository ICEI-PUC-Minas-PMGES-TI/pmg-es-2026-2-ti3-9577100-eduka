import { useCallback, useEffect, useState } from 'react'
import { useBlocker, useNavigate, useSearchParams } from 'react-router'
import { Button } from '../../../../components/Button/Button'
import { Modal } from '../../../../components/Modal/Modal'
import { Select } from '../../../../components/Select/Select'
import { paths } from '../../../../routes/paths'
import { listarAvaliacoes, listarNotas } from '../../../../services/portalProfessorService'
import type { AvaliacaoDoProfessor, NotaDoAluno } from '../../../../types/avaliacaoProfessor'
import { ROTULOS_TIPO_AVALIACAO } from '../../../../types/portalAluno'
import { formatarData, hojeIso } from '../../../secretaria/components/PainelSecretaria/formatacao'
import { DiarioNotas } from '../../components/DiarioNotas/DiarioNotas'
import { EstadoTurmas } from '../../components/EstadoTurmas/EstadoTurmas'
import { SeletorTurmaDisciplina } from '../../components/SeletorTurmaDisciplina/SeletorTurmaDisciplina'
import { useCarregamento } from '../../hooks/useCarregamento'
import { useTurmaDisciplina } from '../../hooks/useTurmaDisciplina'
import { ProfessorLayout } from '../../layouts/ProfessorLayout'
import styles from './Notas.module.css'

const NENHUMA_AVALIACAO: AvaliacaoDoProfessor[] = []
const NENHUMA_NOTA: NotaDoAluno[] = []

/** Sem avaliação na URL: a mais recente que já aconteceu, ou a primeira que vem. */
function avaliacaoPadrao(avaliacoes: AvaliacaoDoProfessor[]): AvaliacaoDoProfessor | null {
  const hoje = hojeIso()
  return avaliacoes.findLast((a) => a.data <= hoje) ?? avaliacoes[0] ?? null
}

export function Notas() {
  const navigate = useNavigate()
  const [params, setParams] = useSearchParams()
  const { opcoes, selecionada, selecionar, carregando: carregandoTurmas, erro: erroTurmas, recarregar: recarregarTurmas } =
    useTurmaDisciplina()
  const turmaId = selecionada?.turma.id
  const disciplinaId = selecionada?.disciplina.id

  const buscarAvaliacoes = useCallback(
    (signal: AbortSignal) => listarAvaliacoes(turmaId as number, disciplinaId as number, signal),
    [turmaId, disciplinaId],
  )
  const avaliacoes = useCarregamento(
    selecionada ? buscarAvaliacoes : null,
    NENHUMA_AVALIACAO,
    'Você não está mais alocado nesta turma/disciplina. Recarregue a página.',
  )

  const avaliacao =
    avaliacoes.dados.find((a) => a.id === params.get('avaliacao')) ?? avaliacaoPadrao(avaliacoes.dados)
  const avaliacaoId = avaliacao?.id

  const buscarNotas = useCallback((signal: AbortSignal) => listarNotas(avaliacaoId as string, signal), [avaliacaoId])
  const notas = useCarregamento(
    avaliacaoId ? buscarNotas : null,
    NENHUMA_NOTA,
    'Esta avaliação não existe mais. Recarregue a página.',
  )

  // Alterações não salvas no diário: pede confirmação antes de perdê-las.
  const [pendente, setPendente] = useState(false)
  const blocker = useBlocker(
    ({ currentLocation, nextLocation }) => pendente && currentLocation.pathname !== nextLocation.pathname,
  )
  // Troca de turma ou de avaliação esperando o "Sair sem salvar".
  const [aoDescartar, setAoDescartar] = useState<(() => void) | null>(null)
  const confirmandoSaida = blocker.state === 'blocked' || aoDescartar !== null
  useEffect(() => {
    if (!pendente) return
    const avisar = (e: BeforeUnloadEvent) => e.preventDefault()
    window.addEventListener('beforeunload', avisar)
    return () => window.removeEventListener('beforeunload', avisar)
  }, [pendente])

  function trocar(acao: () => void) {
    if (!pendente) return acao()
    setAoDescartar(() => () => {
      setPendente(false)
      acao()
    })
  }

  function descartar() {
    if (blocker.state === 'blocked') blocker.proceed()
    else aoDescartar?.()
    setAoDescartar(null)
  }

  function continuarEditando() {
    if (blocker.state === 'blocked') blocker.reset()
    setAoDescartar(null)
  }

  if (!selecionada) {
    return (
      <ProfessorLayout titulo="Notas" subtitulo="Lance as notas de cada avaliação, aluno por aluno.">
        <EstadoTurmas carregando={carregandoTurmas} erro={erroTurmas} onTentarDeNovo={recarregarTurmas} />
      </ProfessorLayout>
    )
  }

  const turmaDisciplina = { turma: String(selecionada.turma.id), disciplina: String(selecionada.disciplina.id) }
  const irParaAvaliacoes = () => navigate(`${paths.professor.avaliacoes}?${new URLSearchParams(turmaDisciplina)}`)

  return (
    <ProfessorLayout titulo="Notas" subtitulo="Lance as notas de cada avaliação, aluno por aluno.">
      <div className={styles.toolbar}>
        <SeletorTurmaDisciplina
          opcoes={opcoes}
          selecionada={selecionada}
          onChange={(chave) => trocar(() => selecionar(chave))}
        />
        {avaliacoes.dados.length > 0 && (
          <Select
            opcoes={avaliacoes.dados.map((a) => ({
              valor: a.id,
              rotulo: `${formatarData(a.data)} · ${a.titulo} (${ROTULOS_TIPO_AVALIACAO[a.tipo]})`,
            }))}
            valor={avaliacaoId ?? ''}
            onChange={(id) => trocar(() => setParams({ ...turmaDisciplina, avaliacao: id }, { replace: true }))}
            aria-label="Avaliação"
            className={styles.filtro}
          />
        )}
        <Button icon="calendar" className={styles.gerenciar} onClick={irParaAvaliacoes}>
          Gerenciar avaliações
        </Button>
      </div>

      {avaliacoes.carregando && <div className={styles.mensagem}>Carregando as avaliações…</div>}

      {!avaliacoes.carregando && avaliacoes.erro && (
        <div className={styles.mensagem} role="alert">
          <span>{avaliacoes.erro}</span>
          <Button size="sm" onClick={avaliacoes.recarregar}>
            Tentar novamente
          </Button>
        </div>
      )}

      {!avaliacoes.carregando && !avaliacoes.erro && !avaliacao && (
        <div className={styles.mensagem}>
          <strong>
            Nenhuma avaliação em {selecionada.turma.nome} · {selecionada.disciplina.nome}.
          </strong>
          <span>Cadastre uma prova, trabalho ou tarefa para lançar as notas da turma.</span>
          <Button size="sm" icon="plus" onClick={irParaAvaliacoes}>
            Cadastrar avaliação
          </Button>
        </div>
      )}

      {avaliacao && notas.carregando && <div className={styles.mensagem}>Carregando os alunos…</div>}

      {avaliacao && !notas.carregando && notas.erro && (
        <div className={styles.mensagem} role="alert">
          <span>{notas.erro}</span>
          <Button size="sm" onClick={notas.recarregar}>
            Tentar novamente
          </Button>
        </div>
      )}

      {avaliacao && !notas.carregando && !notas.erro && (
        <DiarioNotas
          key={avaliacao.id}
          avaliacao={avaliacao}
          notas={notas.dados}
          onPendente={setPendente}
          onSalvo={(salvas) => {
            notas.atualizar(() => salvas)
            // Mantém o "N notas lançadas" da lista de avaliações em dia sem buscar de novo.
            const lancadas = salvas.filter((n) => n.valor !== null).length
            avaliacoes.atualizar((lista) =>
              lista.map((a) => (a.id === avaliacao.id ? { ...a, notasLancadas: lancadas } : a)),
            )
          }}
        />
      )}

      {confirmandoSaida && (
        <Modal
          titulo="Sair sem salvar?"
          tamanho="pequeno"
          onFechar={continuarEditando}
          rodape={
            <>
              <Button onClick={continuarEditando}>Continuar editando</Button>
              <Button variant="danger" onClick={descartar}>
                Sair sem salvar
              </Button>
            </>
          }
        >
          <p className={styles.avisoSaida}>Há notas alteradas que ainda não foram salvas e elas serão perdidas.</p>
        </Modal>
      )}
    </ProfessorLayout>
  )
}
