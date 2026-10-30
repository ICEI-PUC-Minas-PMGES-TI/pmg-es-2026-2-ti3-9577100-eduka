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
import { listarMatriculas, matricularAluno, mensagemErroMatricula } from '../../../../services/matriculaService'
import type { TurmaAcademica } from '../../../../types/academico'
import type { Aluno, AlunoResumo } from '../../../../types/aluno'
import { ROTULOS_TURNO } from '../../../../types/alocacao'
import type { Matricula } from '../../../../types/matricula'
import { plural } from '../../../../utils/formatters'
import { AlunoModal } from '../../components/AlunoModal/AlunoModal'
import { AlunosTable, type SituacaoAluno } from '../../components/AlunosTable/AlunosTable'
import { DetalhesAlunoModal } from '../../components/DetalhesAlunoModal/DetalhesAlunoModal'
import { useAlunos } from '../../hooks/useAlunos'
import { SecretariaLayout } from '../../layouts/SecretariaLayout'
import styles from './Alunos.module.css'

// Estáveis: o useCarregamento pede o valor inicial sempre igual.
const SEM_MATRICULAS: Matricula[] = []
const SEM_TURMAS: TurmaAcademica[] = []

type Filtro = 'todos' | SituacaoAluno['tipo']

const FILTROS: { valor: Filtro; rotulo: string }[] = [
  { valor: 'todos', rotulo: 'Todos' },
  { valor: 'matriculado', rotulo: 'Matriculados' },
  { valor: 'semTurma', rotulo: 'Sem turma' },
  { valor: 'trancado', rotulo: 'Trancados' },
]

function correspondeBusca(aluno: AlunoResumo, busca: string): boolean {
  const termo = busca.trim().toLowerCase()
  if (!termo) return true
  return aluno.nome.toLowerCase().includes(termo) || aluno.matricula.toLowerCase().includes(termo)
}

/** Situação de cada aluno: a matrícula ativa vale mais que a trancada. */
function situacoesDe(matriculas: Matricula[]): Map<string, SituacaoAluno> {
  const situacoes = new Map<string, SituacaoAluno>()
  for (const m of matriculas) {
    if (m.status === 'ATIVA') {
      situacoes.set(m.alunoId, { tipo: 'matriculado', turma: m.turmaNome })
    } else if (m.status === 'TRANCADA' && situacoes.get(m.alunoId)?.tipo !== 'matriculado') {
      situacoes.set(m.alunoId, { tipo: 'trancado', turma: m.turmaNome })
    }
  }
  return situacoes
}

/** Turmas ativas com vaga livre, para o cadastro já matricular. */
function opcoesDeTurma(turmas: TurmaAcademica[], matriculas: Matricula[]): OpcaoSelect<string>[] {
  const ativasPorTurma = new Map<number, number>()
  for (const m of matriculas) {
    if (m.status === 'ATIVA') ativasPorTurma.set(m.turmaId, (ativasPorTurma.get(m.turmaId) ?? 0) + 1)
  }
  return turmas
    .filter((t) => t.ativa && t.vagas > (ativasPorTurma.get(t.id) ?? 0))
    .sort((a, b) => a.nome.localeCompare(b.nome, 'pt-BR', { numeric: true }))
    .map((t) => {
      const livres = t.vagas - (ativasPorTurma.get(t.id) ?? 0)
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
  const { alunos, carregando, erro, recarregar, cadastrar, atualizar } = useAlunos()
  const [busca, setBusca] = useState('')
  const [filtro, setFiltro] = useState<Filtro>('todos')
  const [modal, setModal] = useState<EstadoModal>(
    abrirCadastroAoEntrar ? { aberto: true } : { aberto: false },
  )
  const [sucesso, setSucesso] = useState<string | null>(null)
  const [erroAcao, setErroAcao] = useState<string | null>(null)
  const [reenviandoId, setReenviandoId] = useState<string | null>(null)
  const [vendo, setVendo] = useState<AlunoResumo | null>(null)

  // A listagem de alunos não traz as matrículas: turma e situação saem daqui.
  const matriculas = useCarregamento(listarMatriculas, SEM_MATRICULAS, 'Não foi possível carregar as matrículas.')
  const turmas = useCarregamento(listarTurmasAcademicas, SEM_TURMAS, 'Não foi possível carregar as turmas.')
  const situacoes = situacoesDe(matriculas.dados)
  const situacaoDe = (aluno: AlunoResumo): SituacaoAluno['tipo'] => situacoes.get(aluno.id)?.tipo ?? 'semTurma'

  // Descarta o estado já lido, para o modal não reabrir ao recarregar a página.
  useEffect(() => {
    if (location.state) navigate(location.pathname, { replace: true, state: null })
  }, [location.state, location.pathname, navigate])

  const encontrados = alunos.filter((a) => correspondeBusca(a, busca))
  const visiveis = filtro === 'todos' ? encontrados : encontrados.filter((a) => situacaoDe(a) === filtro)
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
      matriculas.atualizar((atuais) => [...atuais, matricula])
      setSucesso(`${cadastrado} Matriculado na turma ${matricula.turmaNome}.`)
    } catch (e) {
      setSucesso(cadastrado)
      setErroAcao(`Não foi possível matricular "${aluno.nome}" na turma: ${mensagemErroMatricula(e)}`)
    }
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
  const resumoPronto = !carregando && !erro && !matriculas.carregando && !matriculas.erro

  return (
    <SecretariaLayout titulo="Alunos" subtitulo="Cadastre os alunos da escola e mantenha os dados em dia.">
      {resumoPronto && (
        <StatCards rotulo="Resumo dos alunos">
          <StatCard label="Alunos cadastrados" value={alunos.length} descricao="Na escola" destaque />
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
          {FILTROS.map((f) => {
            const quantidade =
              f.valor === 'todos' ? encontrados.length : encontrados.filter((a) => situacaoDe(a) === f.valor).length
            return (
              <button
                key={f.valor}
                type="button"
                aria-pressed={filtro === f.valor}
                className={`${styles.filtro} ${filtro === f.valor ? styles.filtroAtivo : ''}`}
                disabled={matriculas.carregando || matriculas.erro !== null}
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

      {matriculas.erro && (
        <p className={styles.alerta} role="alert">
          {matriculas.erro} A turma e a situação dos alunos não aparecem.{' '}
          <Button size="sm" onClick={matriculas.recarregar}>
            Tentar novamente
          </Button>
        </p>
      )}

      <AlunosTable
        alunos={visiveis}
        reiniciarCom={`${busca}|${filtro}`}
        situacoes={matriculas.carregando || matriculas.erro ? new Map() : situacoes}
        carregando={carregando}
        erro={erro}
        mensagemVazia={mensagemVazia}
        reenviandoId={reenviandoId}
        onTentarNovamente={recarregar}
        onVer={setVendo}
        onEditar={abrirEdicao}
        onReenviarAcesso={handleReenviarAcesso}
      />

      {vendo && <DetalhesAlunoModal aluno={vendo} onFechar={() => setVendo(null)} />}

      {modal.aberto && (
        <AlunoModal
          // A key remonta o modal ao trocar de aluno, para não herdar o estado do anterior.
          key={alunoEmEdicao?.id ?? 'novo'}
          alunoId={alunoEmEdicao?.id}
          turmas={opcoesDeTurma(turmas.dados, matriculas.dados)}
          onFechar={() => setModal({ aberto: false })}
          onSalvar={(dados) => (alunoEmEdicao ? atualizar(alunoEmEdicao.id, dados) : cadastrar(dados))}
          onSalvo={handleSalvo}
        />
      )}
    </SecretariaLayout>
  )
}
