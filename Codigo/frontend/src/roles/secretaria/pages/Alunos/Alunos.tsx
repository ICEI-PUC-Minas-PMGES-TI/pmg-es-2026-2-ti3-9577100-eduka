import { useCallback, useEffect, useState } from 'react'
import { useLocation, useNavigate } from 'react-router'
import { Button } from '../../../../components/ui/Button/Button'
import { Icon } from '../../../../components/ui/Icon/Icon'
import type { OpcaoSelect } from '../../../../components/formulario/Select/Select'
import { StatCard, StatCards } from '../../../../components/ui/StatCard/StatCard'
import { useCarregamento } from '../../../../hooks/useCarregamento'
import { useListaPaginada, type PaginaComResumo } from '../../../../hooks/usePaginacao'
import { useTermoAdiado } from '../../../../hooks/useTermoAdiado'
import type { EstadoAbrirCadastro } from '../../../../routes/paths'
import { listarTurmasAcademicas } from '../../../../services/academico/academicoService'
import {
  ativarAluno,
  atualizarAluno,
  cadastrarAluno,
  inativarAluno,
  listarPaginaDeAlunos,
  reenviarAcessoAluno,
} from '../../../../services/pessoas/alunoService'
import { ApiError } from '../../../../services/api'
import { obterUsuarioLogado } from '../../../../services/acesso/authService'
import { matricularAluno, mensagemErroMatricula } from '../../../../services/academico/matriculaService'
import type { TurmaAcademica } from '../../../../types/academico/academico'
import type { Aluno, AlunoDaLista, ResumoAlunos, SituacaoNaEscola } from '../../../../types/pessoas/aluno'
import { ROTULOS_TURNO } from '../../../../types/academico/alocacao'
import { plural } from '../../../../utils/formatters'
import { AlunoModal } from '../../components/alunos/AlunoModal/AlunoModal'
import { AlunosTable } from '../../components/alunos/AlunosTable/AlunosTable'
import { DetalhesAlunoModal } from '../../components/alunos/DetalhesAlunoModal/DetalhesAlunoModal'
import { SecretariaLayout } from '../../layouts/SecretariaLayout'
import styles from './Alunos.module.css'

// Estável: o useCarregamento pede o valor inicial sempre igual.
const SEM_TURMAS: TurmaAcademica[] = []

type Filtro = 'TODOS' | SituacaoNaEscola

const FILTROS: { valor: Filtro; rotulo: string }[] = [
  { valor: 'TODOS', rotulo: 'Todos' },
  { valor: 'MATRICULADO', rotulo: 'Matriculados' },
  { valor: 'SEM_TURMA', rotulo: 'Sem turma' },
  { valor: 'TRANCADO', rotulo: 'Trancados' },
  { valor: 'INATIVO', rotulo: 'Inativos' },
  { valor: 'ANONIMIZADO', rotulo: 'Anonimizados' },
]

/** Turmas ativas com vaga livre, para o cadastro já matricular. */
function opcoesDeTurma(turmas: TurmaAcademica[]): OpcaoSelect<string>[] {
  return turmas
    .filter((t) => t.ativa && t.vagas > t.matriculasAtivas)
    .sort((a, b) => a.nome.localeCompare(b.nome, 'pt-BR', { numeric: true }))
    .map((t) => {
      const livres = t.vagas - t.matriculasAtivas
      return {
        valor: String(t.id),
        rotulo: `${t.nome} · ${t.cursoNome} · ${ROTULOS_TURNO[t.turno]} (${livres} ${plural(livres, 'vaga', 'vagas')})`,
      }
    })
}

/** Situações cuja pílula só aparece quando há algum aluno nela (ou quando já está escolhida). */
const SO_COM_ALUNOS: readonly Filtro[] = ['INATIVO', 'ANONIMIZADO']

/** Mensagem para quando inativar ou reativar falha. */
function motivoParaNaoAlternar(aluno: AlunoDaLista, e: unknown): string {
  const inativo = aluno.situacao === 'INATIVO'
  if (e instanceof ApiError && e.status === 409) {
    if (/matricula ativa/i.test(e.detalhe ?? '')) {
      return `"${aluno.nome}" tem matrícula ativa. Tranque, cancele ou conclua a matrícula em Matrículas antes de inativar.`
    }
    if (/anonimizado/i.test(e.detalhe ?? '')) {
      return `"${aluno.nome}" foi anonimizado e não pode ser ${inativo ? 'reativado' : 'inativado'}.`
    }
  }
  const detalhe = e instanceof ApiError ? ` ${e.message}` : ''
  return `Não foi possível ${inativo ? 'reativar' : 'inativar'} "${aluno.nome}".${detalhe}`
}

/** Modal fechado, cadastro (sem aluno) ou edição de um aluno. */
type EstadoModal = { aberto: false } | { aberto: true; aluno?: AlunoDaLista }

/**
 * Cadastrar, editar e reenviar o acesso é da secretaria e do MASTER. O
 * FINANCEIRO só consulta a lista e o financeiro de cada aluno: o backend recusa
 * a ele as escritas em /alunos.
 */
function podeCadastrarAlunos(): boolean {
  const perfil = obterUsuarioLogado()?.perfil
  return perfil === 'MASTER' || perfil === 'SECRETARIA'
}

/**
 * Alunos da secretaria (IU07): a lista vem do backend uma página por vez
 * (GET /alunos/pagina), com a busca pelo nome ou matrícula e o filtro de
 * situação feitos lá. Cards e pílulas contam a escola inteira. O FINANCEIRO
 * abre a mesma tela só para consulta (/financeiro/alunos), para chegar à aba
 * Financeiro de cada aluno (IU18).
 */
export function Alunos() {
  const navigate = useNavigate()
  const location = useLocation()
  const podeCadastrar = podeCadastrarAlunos()
  // O atalho "Cadastrar um aluno" do Início chega com o modal já aberto.
  const abrirCadastroAoEntrar =
    podeCadastrar && (location.state as EstadoAbrirCadastro | null)?.abrirCadastro === true
  const [busca, setBusca] = useState('')
  const termo = useTermoAdiado(busca)
  const [filtro, setFiltro] = useState<Filtro>('TODOS')
  const [modal, setModal] = useState<EstadoModal>(abrirCadastroAoEntrar ? { aberto: true } : { aberto: false })
  const [sucesso, setSucesso] = useState<string | null>(null)
  const [erroAcao, setErroAcao] = useState<string | null>(null)
  const [reenviandoId, setReenviandoId] = useState<string | null>(null)
  const [alternandoId, setAlternandoId] = useState<string | null>(null)
  const [vendo, setVendo] = useState<AlunoDaLista | null>(null)

  const buscarAlunos = useCallback(
    (pagina: number, tamanho: number, signal: AbortSignal) =>
      listarPaginaDeAlunos(
        { pagina, tamanho, busca: termo || undefined, situacao: filtro === 'TODOS' ? undefined : filtro },
        signal,
      ),
    [termo, filtro],
  )
  const lista = useListaPaginada<PaginaComResumo<AlunoDaLista, ResumoAlunos>, HTMLElement>(
    buscarAlunos,
    'Não foi possível carregar os alunos.',
  )
  const resumo = lista.dados?.resumo ?? null

  // Só para o cadastro já matricular: cada turma vem com as vagas ocupadas.
  const turmas = useCarregamento(
    podeCadastrar ? listarTurmasAcademicas : null,
    SEM_TURMAS,
    'Não foi possível carregar as turmas.',
  )

  // Descarta o estado já lido, para o modal não reabrir ao recarregar a página.
  useEffect(() => {
    if (location.state) navigate(location.pathname, { replace: true, state: null })
  }, [location.state, location.pathname, navigate])

  // Inativo e anonimizado não estão mais na escola: não contam como alunos
  // cadastrados, e as pílulas deles só aparecem se houver algum.
  const anonimizados = resumo?.porSituacao.ANONIMIZADO ?? 0
  const inativos = resumo?.porSituacao.INATIVO ?? 0
  const naEscola = (resumo?.total ?? 0) - anonimizados - inativos
  const semTurma = resumo?.porSituacao.SEM_TURMA ?? 0
  const filtros = FILTROS.filter(
    (f) => !SO_COM_ALUNOS.includes(f.valor) || (resumo?.porSituacao[f.valor as SituacaoNaEscola] ?? 0) > 0 || filtro === f.valor,
  )
  const mensagemVazia =
    resumo?.total === 0
      ? 'Nenhum aluno cadastrado ainda.'
      : termo
        ? 'Nenhum aluno encontrado para a busca.'
        : 'Nenhum aluno nesta situação.'

  function limparAvisos() {
    setSucesso(null)
    setErroAcao(null)
  }

  function abrirCadastro() {
    limparAvisos()
    setModal({ aberto: true })
  }

  function abrirEdicao(aluno: AlunoDaLista) {
    limparAvisos()
    setModal({ aberto: true, aluno })
  }

  async function handleSalvo(aluno: Aluno, turmaId: number | null) {
    const edicao = modal.aberto && modal.aluno !== undefined
    setModal({ aberto: false })
    if (edicao) {
      setSucesso(`Os dados de "${aluno.nome}" foram atualizados.`)
      lista.recarregar()
      return
    }

    const cadastrado = `"${aluno.nome}" foi cadastrado. O acesso foi enviado para ${aluno.email}.`
    if (turmaId === null) {
      setSucesso(cadastrado)
      lista.recarregar()
      return
    }
    // O aluno já existe: se a matrícula falhar, ele fica sem turma e a
    // secretaria matricula depois, em Matrículas.
    try {
      const matricula = await matricularAluno({ alunoId: aluno.id, turmaId, dataMatricula: null, observacao: null })
      turmas.atualizar((atuais) =>
        atuais.map((t) => (t.id === matricula.turmaId ? { ...t, matriculasAtivas: t.matriculasAtivas + 1 } : t)),
      )
      setSucesso(`${cadastrado} Matriculado na turma ${matricula.turmaNome}.`)
    } catch (e) {
      setSucesso(cadastrado)
      setErroAcao(`Não foi possível matricular "${aluno.nome}" na turma: ${mensagemErroMatricula(e)}`)
    } finally {
      lista.recarregar()
    }
  }

  function handleAnonimizado(aluno: AlunoDaLista) {
    setVendo(null)
    limparAvisos()
    setSucesso(`Os dados pessoais de "${aluno.nome}" foram apagados. O histórico financeiro continua guardado.`)
    lista.recarregar()
  }

  async function handleReenviarAcesso(aluno: AlunoDaLista) {
    limparAvisos()
    setReenviandoId(aluno.id)
    try {
      await reenviarAcessoAluno(aluno.id)
      setSucesso(`Um novo acesso foi enviado para o e-mail de "${aluno.nome}".`)
    } catch (e) {
      if (e instanceof ApiError && e.status === 409) {
        setErroAcao(`"${aluno.nome}" já definiu a própria senha; não há acesso para reenviar.`)
      } else {
        const detalhe = e instanceof ApiError ? ` ${e.message}` : ''
        setErroAcao(`Não foi possível reenviar o acesso de "${aluno.nome}".${detalhe}`)
      }
    } finally {
      setReenviandoId(null)
    }
  }

  async function handleAlternarSituacao(aluno: AlunoDaLista) {
    limparAvisos()
    setAlternandoId(aluno.id)
    const inativo = aluno.situacao === 'INATIVO'
    try {
      await (inativo ? ativarAluno(aluno.id) : inativarAluno(aluno.id))
      setSucesso(
        inativo
          ? `"${aluno.nome}" foi reativado e já pode entrar no portal e ser matriculado.`
          : `"${aluno.nome}" foi inativado. Os dados continuam guardados e ele não entra mais no portal.`,
      )
      lista.recarregar()
    } catch (e) {
      setErroAcao(motivoParaNaoAlternar(aluno, e))
    } finally {
      setAlternandoId(null)
    }
  }

  const alunoEmEdicao = modal.aberto ? modal.aluno : undefined

  return (
    <SecretariaLayout
      titulo="Alunos"
      subtitulo={
        podeCadastrar
          ? 'Cadastre os alunos da escola e mantenha os dados em dia.'
          : 'Consulte os alunos da escola e o financeiro de cada um em Ver.'
      }
    >
      {resumo && (
        <StatCards rotulo="Resumo dos alunos">
          <StatCard
            label="Alunos cadastrados"
            value={naEscola}
            descricao={inativos ? `Na escola · ${inativos} ${plural(inativos, 'inativo', 'inativos')}` : 'Na escola'}
            destaque
          />
          <StatCard label="Com turma" value={resumo.porSituacao.MATRICULADO} descricao="Com matrícula ativa" />
          <StatCard
            label="Sem turma"
            value={semTurma}
            descricao={semTurma ? (podeCadastrar ? 'Matricule em Matrículas' : 'Sem matrícula ativa') : 'Todos matriculados'}
            atencao={semTurma > 0}
          />
        </StatCards>
      )}

      <div className={styles.toolbar}>
        <label className={styles.busca}>
          <Icon name="search" size={17} />
          <input
            type="search"
            placeholder="Procurar pelo nome ou matrícula"
            aria-label="Buscar aluno pelo nome ou matrícula"
            value={busca}
            onChange={(e) => setBusca(e.target.value)}
          />
        </label>
        <div className={styles.filtros} role="group" aria-label="Filtrar por situação">
          {filtros.map((f) => (
            <button
              key={f.valor}
              type="button"
              aria-pressed={filtro === f.valor}
              className={`${styles.filtro} ${filtro === f.valor ? styles.filtroAtivo : ''}`}
              onClick={() => setFiltro(f.valor)}
            >
              {f.rotulo}
              {resumo && (
                <span className={styles.filtroQuantidade}>
                  {f.valor === 'TODOS' ? resumo.total : resumo.porSituacao[f.valor]}
                </span>
              )}
            </button>
          ))}
        </div>
        {podeCadastrar && (
          <Button variant="primary" icon="plus" onClick={abrirCadastro}>
            Novo aluno
          </Button>
        )}
      </div>

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

      <AlunosTable
        paginacao={lista.paginacao}
        topo={lista.topo}
        carregando={lista.carregando}
        atualizando={lista.atualizando}
        erro={lista.erro}
        mensagemVazia={mensagemVazia}
        reenviandoId={reenviandoId}
        alternandoId={alternandoId}
        onTentarNovamente={lista.recarregar}
        onVer={setVendo}
        onEditar={podeCadastrar ? abrirEdicao : undefined}
        onReenviarAcesso={podeCadastrar ? handleReenviarAcesso : undefined}
        onAlternarSituacao={podeCadastrar ? handleAlternarSituacao : undefined}
      />

      {vendo && (
        <DetalhesAlunoModal
          aluno={vendo}
          anonimizado={vendo.situacao === 'ANONIMIZADO'}
          onFechar={() => setVendo(null)}
          onAnonimizado={() => handleAnonimizado(vendo)}
        />
      )}

      {modal.aberto && (
        <AlunoModal
          // A key remonta o modal ao trocar de aluno, para não herdar o estado do anterior.
          key={alunoEmEdicao?.id ?? 'novo'}
          alunoId={alunoEmEdicao?.id}
          turmas={opcoesDeTurma(turmas.dados)}
          onFechar={() => setModal({ aberto: false })}
          onSalvar={(dados) => (alunoEmEdicao ? atualizarAluno(alunoEmEdicao.id, dados) : cadastrarAluno(dados))}
          onSalvo={handleSalvo}
        />
      )}
    </SecretariaLayout>
  )
}
