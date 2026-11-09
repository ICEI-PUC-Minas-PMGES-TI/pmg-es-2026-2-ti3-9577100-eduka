import { useEffect, useState } from 'react'
import { useLocation, useNavigate } from 'react-router'
import { Button } from '../../../../components/Button/Button'
import { Icon } from '../../../../components/Icon/Icon'
import type { OpcaoSelect } from '../../../../components/Select/Select'
import { StatCard, StatCards } from '../../../../components/StatCard/StatCard'
import { useCarregamento } from '../../../../hooks/useCarregamento'
import type { EstadoAbrirCadastro } from '../../../../routes/paths'
import { listarTurmasAcademicas } from '../../../../services/academicoService'
import { reenviarAcessoAluno } from '../../../../services/alunoService'
import { ApiError } from '../../../../services/api'
import { matricularAluno, mensagemErroMatricula } from '../../../../services/matriculaService'
import type { TurmaAcademica } from '../../../../types/academico'
import type { Aluno, AlunoDaLista, AlunoResumo, SituacaoNaEscola } from '../../../../types/aluno'
import { ROTULOS_TURNO } from '../../../../types/alocacao'
import { plural } from '../../../../utils/formatters'
import { AlunoModal } from '../../components/AlunoModal/AlunoModal'
import { AlunosTable, type SituacaoAluno } from '../../components/AlunosTable/AlunosTable'
import { DetalhesAlunoModal } from '../../components/DetalhesAlunoModal/DetalhesAlunoModal'
import { useAlunos } from '../../hooks/useAlunos'
import { SecretariaLayout } from '../../layouts/SecretariaLayout'
import styles from './Alunos.module.css'

// Estável: o useCarregamento pede o valor inicial sempre igual.
const SEM_TURMAS: TurmaAcademica[] = []

type Filtro = 'todos' | SituacaoAluno['tipo']

const FILTROS: { valor: Filtro; rotulo: string }[] = [
  { valor: 'todos', rotulo: 'Todos' },
  { valor: 'matriculado', rotulo: 'Matriculados' },
  { valor: 'semTurma', rotulo: 'Sem turma' },
  { valor: 'trancado', rotulo: 'Trancados' },
  { valor: 'anonimizado', rotulo: 'Anonimizados' },
]

function correspondeBusca(aluno: AlunoResumo, busca: string): boolean {
  const termo = busca.trim().toLowerCase()
  if (!termo) return true
  return aluno.nome.toLowerCase().includes(termo) || aluno.matricula.toLowerCase().includes(termo)
}

const TIPO_DA_SITUACAO: Record<SituacaoNaEscola, SituacaoAluno['tipo']> = {
  MATRICULADO: 'matriculado',
  TRANCADO: 'trancado',
  SEM_TURMA: 'semTurma',
  ANONIMIZADO: 'anonimizado',
}

/** A situação já vem do backend com cada aluno: a matrícula ativa vale mais que a trancada. */
function situacoesDe(alunos: AlunoDaLista[]): Map<string, SituacaoAluno> {
  return new Map(alunos.map((a) => [a.id, { tipo: TIPO_DA_SITUACAO[a.situacao], turma: a.turmaNome }]))
}

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

/** Modal fechado, cadastro (sem id) ou edição de um aluno. */
type EstadoModal = { aberto: false } | { aberto: true; aluno?: AlunoResumo }

export function Alunos() {
  const navigate = useNavigate()
  const location = useLocation()
  // O atalho "Cadastrar um aluno" do Início chega com o modal já aberto.
  const abrirCadastroAoEntrar = (location.state as EstadoAbrirCadastro | null)?.abrirCadastro === true
  const { alunos, carregando, erro, recarregar, cadastrar, atualizar, marcarMatriculado } = useAlunos()
  const [busca, setBusca] = useState('')
  const [filtro, setFiltro] = useState<Filtro>('todos')
  const [modal, setModal] = useState<EstadoModal>(
    abrirCadastroAoEntrar ? { aberto: true } : { aberto: false },
  )
  const [sucesso, setSucesso] = useState<string | null>(null)
  const [erroAcao, setErroAcao] = useState<string | null>(null)
  const [reenviandoId, setReenviandoId] = useState<string | null>(null)
  const [vendo, setVendo] = useState<AlunoResumo | null>(null)

  // Só para o cadastro já matricular: cada turma vem com as vagas ocupadas.
  const turmas = useCarregamento(listarTurmasAcademicas, SEM_TURMAS, 'Não foi possível carregar as turmas.')
  const situacoes = situacoesDe(alunos)
  const situacaoDe = (aluno: AlunoDaLista): SituacaoAluno['tipo'] => TIPO_DA_SITUACAO[aluno.situacao]

  // Descarta o estado já lido, para o modal não reabrir ao recarregar a página.
  useEffect(() => {
    if (location.state) navigate(location.pathname, { replace: true, state: null })
  }, [location.state, location.pathname, navigate])

  const encontrados = alunos.filter((a) => correspondeBusca(a, busca))
  const visiveis = filtro === 'todos' ? encontrados : encontrados.filter((a) => situacaoDe(a) === filtro)
  // O anonimizado já saiu da escola: não conta como aluno cadastrado.
  const naEscola = alunos.filter((a) => situacaoDe(a) !== 'anonimizado').length
  const temAnonimizados = naEscola < alunos.length
  const matriculados = alunos.filter((a) => situacaoDe(a) === 'matriculado').length
  const semTurma = alunos.filter((a) => situacaoDe(a) === 'semTurma').length
  const mensagemVazia =
    alunos.length === 0
      ? 'Nenhum aluno cadastrado ainda.'
      : encontrados.length === 0
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

  function abrirEdicao(aluno: AlunoResumo) {
    limparAvisos()
    setModal({ aberto: true, aluno })
  }

  async function handleSalvo(aluno: Aluno, turmaId: number | null) {
    const edicao = modal.aberto && modal.aluno !== undefined
    setModal({ aberto: false })
    if (edicao) {
      setSucesso(`Os dados de "${aluno.nome}" foram atualizados.`)
      return
    }

    const cadastrado = `"${aluno.nome}" foi cadastrado. O acesso foi enviado para ${aluno.email}.`
    if (turmaId === null) {
      setSucesso(cadastrado)
      return
    }
    // O aluno já existe: se a matrícula falhar, ele fica sem turma e a
    // secretaria matricula depois, em Matrículas.
    try {
      const matricula = await matricularAluno({ alunoId: aluno.id, turmaId, dataMatricula: null, observacao: null })
      marcarMatriculado(matricula)
      turmas.atualizar((atuais) =>
        atuais.map((t) => (t.id === matricula.turmaId ? { ...t, matriculasAtivas: t.matriculasAtivas + 1 } : t)),
      )
      setSucesso(`${cadastrado} Matriculado na turma ${matricula.turmaNome}.`)
    } catch (e) {
      setSucesso(cadastrado)
      setErroAcao(`Não foi possível matricular "${aluno.nome}" na turma: ${mensagemErroMatricula(e)}`)
    }
  }

  function handleAnonimizado(aluno: AlunoResumo) {
    setVendo(null)
    limparAvisos()
    setSucesso(`Os dados pessoais de "${aluno.nome}" foram apagados. O histórico financeiro continua guardado.`)
    recarregar()
  }

  async function handleReenviarAcesso(aluno: AlunoResumo) {
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

  const alunoEmEdicao = modal.aberto ? modal.aluno : undefined
  const resumoPronto = !carregando && !erro

  return (
    <SecretariaLayout titulo="Alunos" subtitulo="Cadastre os alunos da escola e mantenha os dados em dia.">
      {resumoPronto && (
        <StatCards rotulo="Resumo dos alunos">
          <StatCard label="Alunos cadastrados" value={naEscola} descricao="Na escola" destaque />
          <StatCard label="Com turma" value={matriculados} descricao="Com matrícula ativa" />
          <StatCard
            label="Sem turma"
            value={semTurma}
            descricao={semTurma ? 'Matricule em Matrículas' : 'Todos matriculados'}
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
          {FILTROS.filter((f) => f.valor !== 'anonimizado' || temAnonimizados).map((f) => {
            const quantidade =
              f.valor === 'todos' ? encontrados.length : encontrados.filter((a) => situacaoDe(a) === f.valor).length
            return (
              <button
                key={f.valor}
                type="button"
                aria-pressed={filtro === f.valor}
                className={`${styles.filtro} ${filtro === f.valor ? styles.filtroAtivo : ''}`}
                onClick={() => setFiltro(f.valor)}
              >
                {f.rotulo}
                <span className={styles.filtroQuantidade}>{quantidade}</span>
              </button>
            )
          })}
        </div>
        <Button variant="primary" icon="plus" onClick={abrirCadastro}>
          Novo aluno
        </Button>
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
        alunos={visiveis}
        reiniciarCom={`${busca}|${filtro}`}
        situacoes={situacoes}
        carregando={carregando}
        erro={erro}
        mensagemVazia={mensagemVazia}
        reenviandoId={reenviandoId}
        onTentarNovamente={recarregar}
        onVer={setVendo}
        onEditar={abrirEdicao}
        onReenviarAcesso={handleReenviarAcesso}
      />

      {vendo && (
        <DetalhesAlunoModal
          aluno={vendo}
          anonimizado={situacoes.get(vendo.id)?.tipo === 'anonimizado'}
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
          onSalvar={(dados) => (alunoEmEdicao ? atualizar(alunoEmEdicao.id, dados) : cadastrar(dados))}
          onSalvo={handleSalvo}
        />
      )}
    </SecretariaLayout>
  )
}
