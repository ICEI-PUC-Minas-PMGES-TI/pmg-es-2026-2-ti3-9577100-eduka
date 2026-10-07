import { useState } from 'react'
import { Button } from '../../../../components/Button/Button'
import { Icon } from '../../../../components/Icon/Icon'
import { StatCard, StatCards } from '../../../../components/StatCard/StatCard'
import { ApiError } from '../../../../services/api'
import type { Professor } from '../../../../types/professor'
import { CadastroProfessorModal } from '../../components/CadastroProfessorModal/CadastroProfessorModal'
import { DetalhesProfessorModal } from '../../components/DetalhesProfessorModal/DetalhesProfessorModal'
import { ExcluirProfessorModal } from '../../components/ExcluirProfessorModal/ExcluirProfessorModal'
import { ProfessoresTable } from '../../components/ProfessoresTable/ProfessoresTable'
import { useProfessores } from '../../hooks/useProfessores'
import { SecretariaLayout } from '../../layouts/SecretariaLayout'
import styles from './Professores.module.css'

// Por enquanto só pelo nome: as disciplinas ainda não vêm na listagem.
function correspondeBusca(professor: Professor, busca: string): boolean {
  const termo = busca.trim().toLowerCase()
  if (!termo) return true
  return professor.nome.toLowerCase().includes(termo)
}

export function Professores() {
  const { professores, carregando, erro, recarregar, cadastrar, atualizar, alternarSituacao, excluir } =
    useProfessores()
  const [busca, setBusca] = useState('')
  const [cadastroAberto, setCadastroAberto] = useState(false)
  const [editando, setEditando] = useState<Professor | null>(null)
  const [sucesso, setSucesso] = useState<string | null>(null)
  const [erroAcao, setErroAcao] = useState<string | null>(null)
  const [alternandoId, setAlternandoId] = useState<string | null>(null)
  const [excluindo, setExcluindo] = useState<Professor | null>(null)
  const [vendo, setVendo] = useState<Professor | null>(null)

  const visiveis = professores.filter((p) => correspondeBusca(p, busca))
  const ativos = professores.filter((p) => p.ativo).length
  const semVinculo = professores.filter((p) => p.vinculo === null).length
  const mensagemVazia =
    professores.length === 0
      ? 'Nenhum professor cadastrado ainda.'
      : 'Nenhum professor encontrado para a busca.'

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
      {!carregando && !erro && (
        <StatCards rotulo="Resumo dos professores">
          <StatCard
            label="Professores ativos"
            value={ativos}
            descricao={`De ${professores.length} cadastrados`}
            destaque
          />
          <StatCard label="Inativos" value={professores.length - ativos} descricao="Afastados ou de licença" />
          <StatCard
            label="Sem vínculo informado"
            value={semVinculo}
            descricao={semVinculo > 0 ? 'Edite o cadastro para completar' : 'Cadastros completos'}
            atencao={semVinculo > 0}
          />
        </StatCards>
      )}

      <div className={styles.toolbar}>
        <label className={styles.busca}>
          <Icon name="search" size={17} />
          <input
            type="search"
            placeholder="Procurar pelo nome ou disciplina"
            aria-label="Buscar professor pelo nome ou disciplina"
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
        professores={visiveis}
        carregando={carregando}
        erro={erro}
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
