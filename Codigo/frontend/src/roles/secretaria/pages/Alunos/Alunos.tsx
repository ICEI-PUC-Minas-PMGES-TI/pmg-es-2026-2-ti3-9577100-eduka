import { useCallback, useEffect, useMemo, useState, type ReactNode } from 'react'
import { useLocation, useNavigate, useSearchParams } from 'react-router'
import { Button } from '../../../../components/ui/Button/Button'
import { Icon } from '../../../../components/ui/Icon/Icon'
import type { OpcaoSelect } from '../../../../components/formulario/Select/Select'
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
import { useModulos } from '../../../../services/acesso/modulosSessao'
import { matricularAluno, mensagemErroMatricula } from '../../../../services/academico/matriculaService'
import type { TurmaAcademica } from '../../../../types/academico/academico'
import {
  ROTULOS_FAIXA_ATRASO,
  ROTULOS_SITUACAO_FINANCEIRA,
  type Aluno,
  type AlunoDaLista,
  type FaixaAtraso,
  type ResumoAlunos,
  type SituacaoNaEscola,
} from '../../../../types/pessoas/aluno'
import { ROTULOS_TURNO } from '../../../../types/academico/alocacao'
import { plural } from '../../../../utils/formatters'
import { AlunoModal } from '../../components/alunos/AlunoModal/AlunoModal'
import { AlunosTable } from '../../components/alunos/AlunosTable/AlunosTable'
import { DetalhesAlunoModal } from '../../components/alunos/DetalhesAlunoModal/DetalhesAlunoModal'
import { ResumoAlunos as CardsDoResumo } from '../../components/alunos/ResumoAlunos/ResumoAlunos'
import { SecretariaLayout } from '../../layouts/SecretariaLayout'
import {
  FAIXAS_ATRASO,
  SITUACOES_FINANCEIRAS,
  consultaDosFiltros,
  gravarFiltros,
  lerFiltros,
  proximaOrdem,
  temFiltro,
  type FiltrosDaLista,
} from './filtrosDaLista'
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

/** O financeiro dos alunos é do MASTER e do FINANCEIRO, com a gestão financeira contratada. */
function perfilVeFinanceiro(): boolean {
  const perfil = obterUsuarioLogado()?.perfil
  return perfil === 'MASTER' || perfil === 'FINANCEIRO'
}

/**
 * Alunos da secretaria (IU07): a lista vem do backend uma página por vez
 * (GET /alunos/pagina), com a busca pelo nome ou matrícula e os filtros
 * feitos lá. Cards e pílulas contam a escola inteira. O FINANCEIRO abre a
 * mesma tela só para consulta (/financeiro/alunos), para chegar à aba
 * Financeiro de cada aluno (IU18).
 *
 * MASTER e FINANCEIRO veem também o financeiro de cada aluno: a coluna "Em
 * aberto" (que ordena a lista), as pílulas da situação financeira, a faixa de
 * atraso e os filtros de vencimento e mensalidade. Os filtros ficam na URL,
 * para os atalhos do Início chegarem com eles aplicados.
 */
export function Alunos() {
  const navigate = useNavigate()
  const location = useLocation()
  const [searchParams, setSearchParams] = useSearchParams()
  const temModulo = useModulos()
  const podeCadastrar = podeCadastrarAlunos()
  const veFinanceiro = perfilVeFinanceiro() && temModulo('GESTAO_FINANCEIRA')
  // O atalho "Cadastrar um aluno" do Início chega com o modal já aberto.
  const abrirCadastroAoEntrar =
    podeCadastrar && (location.state as EstadoAbrirCadastro | null)?.abrirCadastro === true
  const [busca, setBusca] = useState('')
  const termo = useTermoAdiado(busca)
  const filtros = useMemo(() => lerFiltros(searchParams, veFinanceiro), [searchParams, veFinanceiro])
  const filtro: Filtro = filtros.situacao ?? 'TODOS'
  const [modal, setModal] = useState<EstadoModal>(abrirCadastroAoEntrar ? { aberto: true } : { aberto: false })
  const [sucesso, setSucesso] = useState<string | null>(null)
  const [erroAcao, setErroAcao] = useState<string | null>(null)
  const [reenviandoId, setReenviandoId] = useState<string | null>(null)
  const [alternandoId, setAlternandoId] = useState<string | null>(null)
  const [vendo, setVendo] = useState<AlunoDaLista | null>(null)

  // Busca ou filtro novo troca a função, e a lista volta à primeira página.
  const buscarAlunos = useCallback(
    (pagina: number, tamanho: number, signal: AbortSignal) =>
      listarPaginaDeAlunos({ pagina, tamanho, busca: termo || undefined, ...consultaDosFiltros(filtros) }, signal),
    [termo, filtros],
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

  // Descarta o estado já lido, para o modal não reabrir ao recarregar a
  // página. Os filtros da URL ficam.
  useEffect(() => {
    if (location.state) {
      navigate({ pathname: location.pathname, search: location.search }, { replace: true, state: null })
    }
  }, [location.state, location.pathname, location.search, navigate])

  /** Troca os filtros na URL, sem empilhar uma entrada no histórico a cada clique. */
  function filtrar(mudanca: Partial<FiltrosDaLista>) {
    setSearchParams((atuais) => gravarFiltros(atuais, { ...lerFiltros(atuais, veFinanceiro), ...mudanca }), {
      replace: true,
    })
  }

  function limparFiltros() {
    filtrar({ situacao: null, financeiro: null, faixaAtraso: null, venceEm7Dias: false, semMensalidade: false })
  }

  // As pílulas de inativo e anonimizado só aparecem se houver algum aluno nelas.
  const pilulasAcademicas = FILTROS.filter(
    (f) =>
      !SO_COM_ALUNOS.includes(f.valor) ||
      (resumo?.porSituacao[f.valor as SituacaoNaEscola] ?? 0) > 0 ||
      filtro === f.valor,
  )
  const resumoFinanceiro = resumo?.financeiro ?? null
  const mensagemVazia =
    resumo?.total === 0
      ? 'Nenhum aluno cadastrado ainda.'
      : termo
        ? 'Nenhum aluno encontrado para a busca.'
        : filtros.financeiro || filtros.venceEm7Dias || filtros.semMensalidade
          ? 'Nenhum aluno com esses filtros.'
          : 'Nenhum aluno nesta situação.'

  const pilulasDaSituacao = (
    <div className={styles.filtros} role="group" aria-label="Filtrar por situação">
      {pilulasAcademicas.map((f) => (
        <Pilula
          key={f.valor}
          ativa={filtro === f.valor}
          onClick={() => filtrar({ situacao: f.valor === 'TODOS' ? null : f.valor })}
          quantidade={resumo ? (f.valor === 'TODOS' ? resumo.total : resumo.porSituacao[f.valor]) : undefined}
        >
          {f.rotulo}
        </Pilula>
      ))}
    </div>
  )

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
        <CardsDoResumo
          resumo={resumo}
          // O FINANCEIRO vê os cards financeiros; o MASTER escolhe entre os da escola e os financeiros.
          visoes={!veFinanceiro ? 'ESCOLA' : podeCadastrar ? 'AMBOS' : 'FINANCEIRO'}
          podeMatricular={podeCadastrar}
          atalhos={{
            emAtraso: () => filtrar({ financeiro: 'EM_ATRASO', faixaAtraso: null }),
            venceEm7Dias: () => filtrar({ venceEm7Dias: true }),
            semCobranca: () => filtrar({ financeiro: 'SEM_COBRANCA', faixaAtraso: null }),
          }}
        />
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
        {/* Sem o financeiro, as pílulas cabem na mesma linha da busca, como sempre foram. */}
        {!veFinanceiro && pilulasDaSituacao}
        {podeCadastrar && (
          <Button variant="primary" icon="plus" onClick={abrirCadastro}>
            Novo aluno
          </Button>
        )}
      </div>

      {veFinanceiro && (
        <div className={styles.grupos}>
          <GrupoDeFiltros rotulo="Na escola">{pilulasDaSituacao}</GrupoDeFiltros>

          <GrupoDeFiltros rotulo="Financeiro">
            <div className={styles.filtros} role="group" aria-label="Filtrar pela situação financeira">
              <Pilula
                ativa={filtros.financeiro === null}
                onClick={() => filtrar({ financeiro: null, faixaAtraso: null })}
                quantidade={resumo?.total}
              >
                Todos
              </Pilula>
              {SITUACOES_FINANCEIRAS.map((s) => (
                <Pilula
                  key={s}
                  ativa={filtros.financeiro === s}
                  onClick={() => filtrar({ financeiro: s, faixaAtraso: null })}
                  quantidade={resumoFinanceiro?.porSituacao[s]}
                  tom={s === 'EM_ATRASO' ? 'alerta' : undefined}
                >
                  {ROTULOS_SITUACAO_FINANCEIRA[s]}
                </Pilula>
              ))}
            </div>
          </GrupoDeFiltros>

          {filtros.financeiro === 'EM_ATRASO' && (
            <GrupoDeFiltros rotulo="Atrasado há">
              <div className={styles.filtros} role="group" aria-label="Filtrar pelo tempo de atraso">
                <Pilula
                  ativa={filtros.faixaAtraso === null}
                  onClick={() => filtrar({ faixaAtraso: null })}
                  quantidade={resumoFinanceiro?.porSituacao.EM_ATRASO}
                >
                  Qualquer tempo
                </Pilula>
                {FAIXAS_ATRASO.map((faixa: FaixaAtraso) => (
                  <Pilula
                    key={faixa}
                    ativa={filtros.faixaAtraso === faixa}
                    onClick={() => filtrar({ faixaAtraso: faixa })}
                    quantidade={resumoFinanceiro?.porFaixaAtraso[faixa]}
                  >
                    {ROTULOS_FAIXA_ATRASO[faixa]}
                  </Pilula>
                ))}
              </div>
            </GrupoDeFiltros>
          )}

          <GrupoDeFiltros rotulo="Mais filtros">
            <div className={styles.filtros} role="group" aria-label="Mais filtros financeiros">
              <Pilula
                ativa={filtros.venceEm7Dias}
                marcavel
                onClick={() => filtrar({ venceEm7Dias: !filtros.venceEm7Dias })}
                quantidade={resumoFinanceiro?.venceEm7Dias}
              >
                Vence nos próximos 7 dias
              </Pilula>
              <Pilula
                ativa={filtros.semMensalidade}
                marcavel
                onClick={() => filtrar({ semMensalidade: !filtros.semMensalidade })}
                quantidade={resumoFinanceiro?.semMensalidadeNoMes}
              >
                Sem mensalidade neste mês
              </Pilula>
              {temFiltro(filtros) && (
                <button type="button" className={styles.limpar} onClick={limparFiltros}>
                  Limpar filtros
                </button>
              )}
            </div>
          </GrupoDeFiltros>
        </div>
      )}

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
        colunaEmAberto={
          veFinanceiro
            ? {
                ordem: filtros.ordem.campo === 'EM_ABERTO' ? filtros.ordem.direcao : null,
                onOrdenar: () => filtrar({ ordem: proximaOrdem(filtros.ordem) }),
              }
            : undefined
        }
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

type PilulaProps = {
  ativa: boolean
  onClick: () => void
  /** Quantos alunos a pílula traz; sem ela (resumo ainda não chegou), não mostra o número. */
  quantidade?: number
  /** Número em vermelho quando há algum aluno, para o que pede atenção. */
  tom?: 'alerta'
  /** Liga e desliga sozinha (combina com as outras), em vez de escolher uma entre várias. */
  marcavel?: boolean
  children: string
}

function Pilula({ ativa, onClick, quantidade, tom, marcavel = false, children }: PilulaProps) {
  const alerta = tom === 'alerta' && (quantidade ?? 0) > 0
  return (
    <button
      type="button"
      aria-pressed={ativa}
      className={`${styles.filtro} ${ativa ? styles.filtroAtivo : ''}`}
      onClick={onClick}
    >
      {marcavel && (
        <span className={`${styles.marca} ${ativa ? styles.marcaLigada : ''}`} aria-hidden="true">
          {ativa && <Icon name="check" size={12} strokeWidth={3} />}
        </span>
      )}
      {children}
      {quantidade !== undefined && (
        <span className={`${styles.filtroQuantidade} ${alerta && !ativa ? styles.quantidadeAlerta : ''}`}>
          {quantidade}
        </span>
      )}
    </button>
  )
}

/** Uma linha de pílulas com o rótulo do grupo à esquerda (empilha no celular). */
function GrupoDeFiltros({ rotulo, children }: { rotulo: string; children: ReactNode }) {
  return (
    <div className={styles.grupo}>
      <span className={styles.grupoRotulo}>{rotulo}</span>
      {children}
    </div>
  )
}
