import { useCallback, useEffect, useState } from 'react'
import { useLocation, useNavigate } from 'react-router'
import { Button } from '../../../../components/ui/Button/Button'
import { Icon } from '../../../../components/ui/Icon/Icon'
import { StatCard, StatCards } from '../../../../components/ui/StatCard/StatCard'
import { useListaPaginada, type PaginaComResumo } from '../../../../hooks/usePaginacao'
import { useTermoAdiado } from '../../../../hooks/useTermoAdiado'
import type { EstadoAbrirCadastro } from '../../../../routes/paths'
import { ApiError } from '../../../../services/api'
import {
  ativarProfessor,
  atualizarProfessor,
  cadastrarProfessor,
  excluirProfessor,
  inativarProfessor,
  listarPaginaDeProfessores,
} from '../../../../services/pessoas/professorService'
import type { Professor, ResumoProfessores } from '../../../../types/pessoas/professor'
import { CadastroProfessorModal } from '../../components/professores/CadastroProfessorModal/CadastroProfessorModal'
import { DetalhesProfessorModal } from '../../components/professores/DetalhesProfessorModal/DetalhesProfessorModal'
import { ExcluirProfessorModal } from '../../components/professores/ExcluirProfessorModal/ExcluirProfessorModal'
import { ProfessoresTable } from '../../components/professores/ProfessoresTable/ProfessoresTable'
import { SecretariaLayout } from '../../layouts/SecretariaLayout'
import styles from './Professores.module.css'

/**
 * Professores da secretaria (IU08): a lista vem do backend uma página por vez
 * (GET /professores/pagina), com a busca pelo nome feita lá. Os cards contam a
 * escola inteira. Toda ação lê a página de novo, para cards e lista baterem.
 */
export function Professores() {
  const navigate = useNavigate()
  const location = useLocation()
  const [busca, setBusca] = useState('')
  // As disciplinas ainda não vêm na listagem: a busca é só pelo nome.
  const termo = useTermoAdiado(busca)
  const buscarProfessores = useCallback(
    (pagina: number, tamanho: number, signal: AbortSignal) =>
      listarPaginaDeProfessores({ pagina, tamanho, nome: termo || undefined }, signal),
    [termo],
  )
  const lista = useListaPaginada<PaginaComResumo<Professor, ResumoProfessores>, HTMLElement>(
    buscarProfessores,
    'Não foi possível carregar os professores.',
  )
  const resumo = lista.dados?.resumo ?? null
  const { recarregar } = lista
  // O atalho "Cadastrar um professor" do Início chega com o modal já aberto.
  const [cadastroAberto, setCadastroAberto] = useState(
    (location.state as EstadoAbrirCadastro | null)?.abrirCadastro === true,
  )
  const [editando, setEditando] = useState<Professor | null>(null)
  const [sucesso, setSucesso] = useState<string | null>(null)
  const [erroAcao, setErroAcao] = useState<string | null>(null)
  const [alternandoId, setAlternandoId] = useState<string | null>(null)
  const [excluindo, setExcluindo] = useState<Professor | null>(null)
  const [vendo, setVendo] = useState<Professor | null>(null)

  // Descarta o estado já lido, para o modal não reabrir ao recarregar a página.
  useEffect(() => {
    if (location.state) navigate(location.pathname, { replace: true, state: null })
  }, [location.state, location.pathname, navigate])

  const mensagemVazia =
    resumo?.total === 0 ? 'Nenhum professor cadastrado ainda.' : 'Nenhum professor encontrado para a busca.'

  /** Cadastra ou edita; lança ApiError se o backend recusar (o modal mostra). */
  async function cadastrar(dados: Parameters<typeof cadastrarProfessor>[0]) {
    const criado = await cadastrarProfessor(dados)
    recarregar()
    return criado
  }

  async function atualizar(id: string, dados: Parameters<typeof atualizarProfessor>[1]) {
    const atualizado = await atualizarProfessor(id, dados)
    recarregar()
    return atualizado
  }

  /** Inativa um professor ativo ou reativa um inativo; lança ApiError se falhar. */
  async function alternarSituacao(professor: Professor) {
    await (professor.ativo ? inativarProfessor(professor.id) : ativarProfessor(professor.id))
    recarregar()
  }

  async function excluir(professor: Professor) {
    await excluirProfessor(professor.id)
    recarregar()
  }

  function limparAvisos() {
    setSucesso(null)
    setErroAcao(null)
  }

  function abrirCadastro() {
    limparAvisos()
    setCadastroAberto(true)
  }

  async function handleAlternarSituacao(professor: Professor) {
    limparAvisos()
    setAlternandoId(professor.id)
    try {
      await alternarSituacao(professor)
      setSucesso(`"${professor.nome}" foi ${professor.ativo ? 'inativado' : 'reativado'}.`)
    } catch (e) {
      const acao = professor.ativo ? 'inativar' : 'reativar'
      const detalhe = e instanceof ApiError ? ` ${e.message}` : ''
      setErroAcao(`Não foi possível ${acao} "${professor.nome}".${detalhe}`)
    } finally {
      setAlternandoId(null)
    }
  }

  function abrirExclusao(professor: Professor) {
    limparAvisos()
    setExcluindo(professor)
  }

  function handleExclusaoConcluida(mensagem: string) {
    setExcluindo(null)
    setSucesso(mensagem)
  }

  function handleCadastrado(professor: Professor) {
    setCadastroAberto(false)
    setSucesso(`"${professor.nome}" foi cadastrado com sucesso.`)
  }

  function abrirEdicao(professor: Professor) {
    limparAvisos()
    setEditando(professor)
  }

  function handleEditado(professor: Professor) {
    setEditando(null)
    setSucesso(`Os dados de "${professor.nome}" foram atualizados.`)
  }

  return (
    <SecretariaLayout
      titulo="Professores"
      subtitulo="Cadastre os professores e veja onde cada um dá aula."
    >
      {resumo && (
        <StatCards rotulo="Resumo dos professores">
          <StatCard
            label="Professores ativos"
            value={resumo.ativos}
            descricao={`De ${resumo.total} cadastrados`}
            destaque
          />
          <StatCard label="Inativos" value={resumo.total - resumo.ativos} descricao="Afastados ou de licença" />
          <StatCard
            label="Sem vínculo informado"
            value={resumo.semVinculo}
            descricao={resumo.semVinculo > 0 ? 'Edite o cadastro para completar' : 'Cadastros completos'}
            atencao={resumo.semVinculo > 0}
          />
        </StatCards>
      )}

      <div className={styles.toolbar}>
        <label className={styles.busca}>
          <Icon name="search" size={17} />
          <input
            type="search"
            placeholder="Procurar pelo nome"
            aria-label="Buscar professor pelo nome"
            value={busca}
            onChange={(e) => setBusca(e.target.value)}
          />
        </label>
        <Button variant="primary" icon="plus" onClick={abrirCadastro}>
          Novo professor
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

      <ProfessoresTable
        paginacao={lista.paginacao}
        topo={lista.topo}
        carregando={lista.carregando}
        atualizando={lista.atualizando}
        erro={lista.erro}
        mensagemVazia={mensagemVazia}
        alternandoId={alternandoId}
        onTentarNovamente={recarregar}
        onVer={setVendo}
        onEditar={abrirEdicao}
        onAlternarSituacao={handleAlternarSituacao}
        onExcluir={abrirExclusao}
      />

      {vendo && <DetalhesProfessorModal professor={vendo} onFechar={() => setVendo(null)} />}

      {editando && (
        <CadastroProfessorModal
          key={editando.id}
          professorId={editando.id}
          onFechar={() => setEditando(null)}
          onSalvar={(dados) => atualizar(editando.id, dados)}
          onCadastrado={handleEditado}
        />
      )}

      {cadastroAberto && (
        <CadastroProfessorModal
          onFechar={() => setCadastroAberto(false)}
          onSalvar={cadastrar}
          onCadastrado={handleCadastrado}
        />
      )}

      {excluindo && (
        <ExcluirProfessorModal
          professor={excluindo}
          onFechar={() => setExcluindo(null)}
          onExcluir={excluir}
          onInativar={alternarSituacao}
          onConcluido={handleExclusaoConcluida}
        />
      )}
    </SecretariaLayout>
  )
}
