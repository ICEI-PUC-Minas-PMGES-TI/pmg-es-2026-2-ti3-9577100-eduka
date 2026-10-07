import { useState } from 'react'
import { Button } from '../../../../components/Button/Button'
import { Icon } from '../../../../components/Icon/Icon'
import { reenviarAcessoAluno } from '../../../../services/alunoService'
import { ApiError } from '../../../../services/api'
import type { Aluno, AlunoResumo } from '../../../../types/aluno'
import { AlunoModal } from '../../components/AlunoModal/AlunoModal'
import { AlunosTable } from '../../components/AlunosTable/AlunosTable'
import { DetalhesAlunoModal } from '../../components/DetalhesAlunoModal/DetalhesAlunoModal'
import { useAlunos } from '../../hooks/useAlunos'
import { SecretariaLayout } from '../../layouts/SecretariaLayout'
import styles from './Alunos.module.css'

function correspondeBusca(aluno: AlunoResumo, busca: string): boolean {
  const termo = busca.trim().toLowerCase()
  if (!termo) return true
  return aluno.nome.toLowerCase().includes(termo) || aluno.matricula.toLowerCase().includes(termo)
}

/** Modal fechado, cadastro (sem id) ou edição de um aluno. */
type EstadoModal = { aberto: false } | { aberto: true; aluno?: AlunoResumo }

export function Alunos() {
  const { alunos, carregando, erro, recarregar, cadastrar, atualizar } = useAlunos()
  const [busca, setBusca] = useState('')
  const [modal, setModal] = useState<EstadoModal>({ aberto: false })
  const [sucesso, setSucesso] = useState<string | null>(null)
  const [erroAcao, setErroAcao] = useState<string | null>(null)
  const [reenviandoId, setReenviandoId] = useState<string | null>(null)
  const [vendo, setVendo] = useState<AlunoResumo | null>(null)

  const visiveis = alunos.filter((a) => correspondeBusca(a, busca))
  const mensagemVazia =
    alunos.length === 0 ? 'Nenhum aluno cadastrado ainda.' : 'Nenhum aluno encontrado para a busca.'

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

  function handleSalvo(aluno: Aluno) {
    const edicao = modal.aberto && modal.aluno !== undefined
    setModal({ aberto: false })
    setSucesso(
      edicao
        ? `Os dados de "${aluno.nome}" foram atualizados.`
        : `"${aluno.nome}" foi cadastrado. O acesso foi enviado para ${aluno.email}.`,
    )
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

  return (
    <SecretariaLayout titulo="Alunos" subtitulo="Cadastre os alunos da escola e mantenha os dados em dia.">
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
          onFechar={() => setModal({ aberto: false })}
          onSalvar={(dados) => (alunoEmEdicao ? atualizar(alunoEmEdicao.id, dados) : cadastrar(dados))}
          onSalvo={handleSalvo}
        />
      )}
    </SecretariaLayout>
  )
}
