import { useCallback, useEffect, useState } from 'react'
import { useBlocker, useLocation, useNavigate, useSearchParams } from 'react-router'
import { Button } from '../../../../components/Button/Button'
import { ConfirmarExclusao } from '../../../../components/ConfirmarExclusao/ConfirmarExclusao'
import { Modal } from '../../../../components/Modal/Modal'
import { useCarregamento } from '../../../../hooks/useCarregamento'
import type { EstadoAbrirCadastro } from '../../../../routes/paths'
import { obterToken } from '../../../../services/authService'
import {
  atualizarAvaliacao,
  cadastrarAvaliacao,
  excluirAvaliacao,
  listarAvaliacoes,
  listarNotas,
} from '../../../../services/portalProfessorService'
import type { AvaliacaoDoProfessor, NotaDoAluno } from '../../../../types/avaliacaoProfessor'
import { ROTULOS_TIPO_AVALIACAO } from '../../../../types/portalAluno'
import { formatarPontos, hojeIso, plural } from '../../../../utils/formatters'
import { formatarData } from '../../../secretaria/components/PainelSecretaria/formatacao'
import { DiarioNotas } from '../../components/DiarioNotas/DiarioNotas'
import { EstadoTurmas } from '../../components/EstadoTurmas/EstadoTurmas'
import { FormularioAvaliacao } from '../../components/FormularioAvaliacao/FormularioAvaliacao'
import { SeletorTurmaDisciplina } from '../../components/SeletorTurmaDisciplina/SeletorTurmaDisciplina'
import { useTurmaDisciplina } from '../../hooks/useTurmaDisciplina'
import { ProfessorLayout } from '../../layouts/ProfessorLayout'
import { mensagemErroAvaliacao } from '../../notas'
import { Dica } from '../../../../components/Dica/Dica'
import styles from './Notas.module.css'

const NENHUMA_AVALIACAO: AvaliacaoDoProfessor[] = []
const NENHUMA_NOTA: NotaDoAluno[] = []

const TITULO = 'Avaliações e notas'
const SUBTITULO = 'Crie as avaliações da turma e lance as notas dos alunos.'

/** Mesma ordem do backend: por data. */
function porData(a: AvaliacaoDoProfessor, b: AvaliacaoDoProfessor): number {
  return a.data.localeCompare(b.data)
}

/** Sem avaliação na URL: a mais recente que já aconteceu, ou a primeira que vem. */
function avaliacaoPadrao(avaliacoes: AvaliacaoDoProfessor[]): AvaliacaoDoProfessor | null {
  const hoje = hojeIso()
  return avaliacoes.findLast((a) => a.data <= hoje) ?? avaliacoes[0] ?? null
}

/** Formulário aberto: cadastro (sem avaliação) ou edição. */
type Formulario = { aberto: false } | { aberto: true; avaliacao?: AvaliacaoDoProfessor }

/**
 * Avaliações e notas de uma turma/disciplina numa tela só: as avaliações à
 * esquerda (criar, editar, excluir) e o diário de notas da escolhida à direita.
 * Turma, disciplina e avaliação ficam na URL.
 */
export function Notas() {
  const navigate = useNavigate()
  const location = useLocation()
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

  // O atalho "Criar uma avaliação" do Início chega com o formulário já aberto.
  const [formulario, setFormulario] = useState<Formulario>(
    (location.state as EstadoAbrirCadastro | null)?.abrirCadastro ? { aberto: true } : { aberto: false },
  )
  const [excluindo, setExcluindo] = useState<AvaliacaoDoProfessor | null>(null)
  const [sucesso, setSucesso] = useState<string | null>(null)

  // Descarta o estado já lido, para o formulário não reabrir ao recarregar a página.
  useEffect(() => {
    if (location.state) navigate({ pathname: location.pathname, search: location.search }, { replace: true, state: null })
  }, [location.state, location.pathname, location.search, navigate])

  // Alterações não salvas no diário: pede confirmação antes de perdê-las.
  const [pendente, setPendente] = useState(false)
  // O "Sair" da conta já apagou a sessão antes de navegar: esse não é
  // bloqueado, ou "Continuar editando" deixaria a tela sem sessão.
  const blocker = useBlocker(
    ({ currentLocation, nextLocation }) =>
      pendente && obterToken() !== null && currentLocation.pathname !== nextLocation.pathname,
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
      <ProfessorLayout titulo={TITULO} subtitulo={SUBTITULO}>
        <EstadoTurmas carregando={carregandoTurmas} erro={erroTurmas} onTentarDeNovo={recarregarTurmas} />
      </ProfessorLayout>
    )
  }

  const turmaDisciplina = { turma: String(selecionada.turma.id), disciplina: String(selecionada.disciplina.id) }
  const contexto = `${selecionada.turma.nome} · ${selecionada.disciplina.nome}`
  const editando = formulario.aberto ? formulario.avaliacao : undefined

  function escolher(id: string) {
    trocar(() => setParams({ ...turmaDisciplina, avaliacao: id }, { replace: true }))
  }

  function abrirFormulario(avaliacaoDoForm?: AvaliacaoDoProfessor) {
    setSucesso(null)
    setFormulario({ aberto: true, avaliacao: avaliacaoDoForm })
  }

  return (
    <ProfessorLayout titulo={TITULO} subtitulo={SUBTITULO}>
      <div className={styles.toolbar}>
        <SeletorTurmaDisciplina
          opcoes={opcoes}
          selecionada={selecionada}
          onChange={(chave) =>
            trocar(() => {
              setSucesso(null)
              selecionar(chave)
            })
          }
        />
        <Button variant="accent" icon="plus" className={styles.nova} onClick={() => abrirFormulario()}>
          Nova avaliação
        </Button>
      </div>

      {sucesso && (
        <p className={styles.sucesso} role="status">
          {sucesso}
        </p>
      )}

      {avaliacoes.carregando && <div className={styles.mensagem} role="status">Carregando as avaliações…</div>}

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
          <strong>Nenhuma avaliação em {contexto}.</strong>
          <span>Cadastre a primeira prova, trabalho ou tarefa para lançar as notas da turma.</span>
          <Button size="sm" icon="plus" onClick={() => abrirFormulario()}>
            Nova avaliação
          </Button>
        </div>
      )}

      {!avaliacoes.carregando && !avaliacoes.erro && avaliacao && (
        <div className={styles.colunas}>
          <section className={styles.avaliacoes} aria-label={`Avaliações de ${contexto}`}>
            <h2 className={styles.avaliacoesTitulo}>Avaliações</h2>
            <ul className={styles.lista}>
              {avaliacoes.dados.map((a) => (
                <ItemAvaliacao
                  key={a.id}
                  avaliacao={a}
                  selecionada={a.id === avaliacao.id}
                  onEscolher={() => escolher(a.id)}
                  onEditar={() => abrirFormulario(a)}
                  onExcluir={() => {
                    setSucesso(null)
                    setExcluindo(a)
                  }}
                />
              ))}
            </ul>
            <p className={styles.nota}>O total de pontos é a soma do valor das avaliações.</p>
          </section>

          <div className={styles.diario}>
            {notas.carregando && <div className={styles.mensagem} role="status">Carregando os alunos…</div>}

            {!notas.carregando && notas.erro && (
              <div className={styles.mensagem} role="alert">
                <span>{notas.erro}</span>
                <Button size="sm" onClick={notas.recarregar}>
                  Tentar novamente
                </Button>
              </div>
            )}

            {!notas.carregando && !notas.erro && (
              <DiarioNotas
                key={avaliacao.id}
                avaliacao={avaliacao}
                notas={notas.dados}
                onPendente={setPendente}
                onSalvo={(salvas, publicadas) => {
                  notas.atualizar(() => salvas)
                  // Mantém o "N notas lançadas" da lista de avaliações em dia sem buscar de novo.
                  const lancadas = salvas.filter((n) => n.valor !== null).length
                  avaliacoes.atualizar((lista) =>
                    lista.map((a) =>
                      a.id === avaliacao.id ? { ...a, notasLancadas: lancadas, notasPublicadas: publicadas } : a,
                    ),
                  )
                }}
              />
            )}
          </div>
        </div>
      )}

      {formulario.aberto && (
        <FormularioAvaliacao
          key={editando?.id ?? 'nova'}
          contexto={contexto}
          avaliacao={editando}
          onFechar={() => setFormulario({ aberto: false })}
          onSalvar={async (dados) => {
            const salva = editando
              ? await atualizarAvaliacao(editando.id, dados)
              : await cadastrarAvaliacao({ ...dados, turmaId: selecionada.turma.id, disciplinaId: selecionada.disciplina.id })
            avaliacoes.atualizar((lista) => [...lista.filter((a) => a.id !== salva.id), salva].sort(porData))
            setFormulario({ aberto: false })
            setSucesso(editando ? `Avaliação "${salva.titulo}" atualizada.` : `Avaliação "${salva.titulo}" cadastrada.`)
            // A nova já abre no diário, pronta para as notas.
            if (!editando) escolher(salva.id)
          }}
        />
      )}

      {excluindo && (
        <ConfirmarExclusao
          titulo="Excluir avaliação"
          nome={excluindo.titulo}
          traduzirErro={mensagemErroAvaliacao}
          onFechar={() => setExcluindo(null)}
          onConfirmar={async () => {
            await excluirAvaliacao(excluindo.id)
            avaliacoes.atualizar((lista) => lista.filter((a) => a.id !== excluindo.id))
            setExcluindo(null)
            setSucesso(`Avaliação "${excluindo.titulo}" excluída.`)
            // Sem ela na lista, a escolha volta para a avaliação padrão.
            if (excluindo.id === avaliacaoId) setParams(turmaDisciplina, { replace: true })
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

type ItemAvaliacaoProps = {
  avaliacao: AvaliacaoDoProfessor
  selecionada: boolean
  onEscolher: () => void
  onEditar: () => void
  onExcluir: () => void
}

/** Card de uma avaliação na lista: escolhe o diário; editar e excluir ao lado. */
function ItemAvaliacao({ avaliacao, selecionada, onEscolher, onEditar, onExcluir }: ItemAvaliacaoProps) {
  const temNotas = avaliacao.notasLancadas > 0

  return (
    <li className={`${styles.item} ${selecionada ? styles.itemSelecionado : ''}`}>
      <button type="button" className={styles.escolher} aria-current={selecionada || undefined} onClick={onEscolher}>
        <span className={styles.itemTopo}>
          <strong className={styles.itemTitulo}>{avaliacao.titulo}</strong>
          <span className={styles.valor}>{formatarPontos(avaliacao.valor)} pts</span>
        </span>
        <span className={styles.itemDetalhe}>
          {formatarData(avaliacao.data)} · {ROTULOS_TIPO_AVALIACAO[avaliacao.tipo]} ·{' '}
          {temNotas
            ? `${avaliacao.notasLancadas} ${plural(avaliacao.notasLancadas, 'nota lançada', 'notas lançadas')}`
            : 'Sem notas'}
          {!avaliacao.notasPublicadas && ' · rascunho'}
        </span>
      </button>
      <span className={styles.itemAcoes}>
        <Dica texto="Editar" descrever={false}>
          <Button size="sm" icon="edit" aria-label={`Editar ${avaliacao.titulo}`} onClick={onEditar} />
        </Dica>
        {/* Bloqueado, a dica explica o motivo: aí ela vale como descrição. */}
        <Dica
          texto={temNotas ? 'Tem notas lançadas: apague as notas antes de excluir' : 'Excluir'}
          descrever={temNotas}
        >
          <Button
            size="sm"
            icon="trash"
            variant="dangerOutline"
            aria-label={`Excluir ${avaliacao.titulo}`}
            disabled={temNotas}
            onClick={onExcluir}
          />
        </Dica>
      </span>
    </li>
  )
}
