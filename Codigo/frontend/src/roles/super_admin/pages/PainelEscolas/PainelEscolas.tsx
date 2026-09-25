import { useEffect, useState } from 'react'
import { generatePath, useLocation, useNavigate } from 'react-router'
import { Button } from '../../../../components/Button/Button'
import { paths } from '../../../../routes/paths'
import { ApiError } from '../../../../services/api'
import type { Escola } from '../../../../types/escola'
import { somenteDigitos } from '../../../../utils/formatters'
import { CadastroEscolaModal } from '../../components/CadastroEscolaModal/CadastroEscolaModal'
import { EscolasStats } from '../../components/EscolasStats/EscolasStats'
import { EscolasTable } from '../../components/EscolasTable/EscolasTable'
import { useEscolas } from '../../hooks/useEscolas'
import { SuperAdminLayout } from '../../layouts/SuperAdminLayout'
import type { EstadoPainelEscolas } from '../EditarEscola/EditarEscola'
import styles from './PainelEscolas.module.css'

function correspondeBusca(escola: Escola, busca: string): boolean {
  const termo = busca.trim().toLowerCase()
  if (!termo) return true

  const digitos = somenteDigitos(termo)
  return (
    escola.nome.toLowerCase().includes(termo) ||
    (escola.email?.toLowerCase().includes(termo) ?? false) ||
    (digitos.length > 0 && (escola.cnpjCpf?.includes(digitos) ?? false))
  )
}

export function PainelEscolas() {
  const { escolas, carregando, erro, recarregar, alternarSituacao, cadastrar } = useEscolas()
  const navigate = useNavigate()
  const location = useLocation()
  // Mensagem de sucesso ou busca vindas da tela de edição.
  const estadoRecebido = location.state as EstadoPainelEscolas | null
  const [busca, setBusca] = useState(estadoRecebido?.busca ?? '')
  const [alternandoId, setAlternandoId] = useState<string | null>(null)
  const [erroAcao, setErroAcao] = useState<string | null>(null)
  const [cadastroAberto, setCadastroAberto] = useState(false)
  const [sucesso, setSucesso] = useState<string | null>(estadoRecebido?.sucesso ?? null)

  // Descarta o estado já lido, para a mensagem não voltar ao recarregar a página.
  useEffect(() => {
    if (location.state) navigate(location.pathname, { replace: true, state: null })
  }, [location.state, location.pathname, navigate])

  const escolasFiltradas = escolas.filter((e) => correspondeBusca(e, busca))

  async function handleAlternarSituacao(escola: Escola) {
    setAlternandoId(escola.id)
    setErroAcao(null)
    setSucesso(null)
    try {
      await alternarSituacao(escola)
    } catch (e) {
      const acao = escola.ativo ? 'suspender' : 'reativar'
      const detalhe = e instanceof ApiError ? ` ${e.message}` : ''
      setErroAcao(`Não foi possível ${acao} "${escola.nome}".${detalhe}`)
    } finally {
      setAlternandoId(null)
    }
  }

  function editar(escola: Escola) {
    navigate(generatePath(paths.superAdmin.editarEscola, { id: escola.id }))
  }

  function abrirCadastro() {
    setSucesso(null)
    setCadastroAberto(true)
  }

  function handleCadastrada(escola: Escola) {
    setCadastroAberto(false)
    setSucesso(`"${escola.nome}" foi cadastrada com sucesso.`)
  }

  return (
    <SuperAdminLayout busca={busca} onBuscaChange={setBusca}>
      <div className={styles.heading}>
        <div>
          <h1 className={styles.title}>Painel de Escolas</h1>
          <p className={styles.subtitle}>Todas as escolas que usam o Eduka.</p>
        </div>
        <Button variant="primary" icon="plus" onClick={abrirCadastro}>
          Cadastrar nova escola
        </Button>
      </div>

      <EscolasStats escolas={escolas} />

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

      <EscolasTable
        escolas={escolasFiltradas}
        carregando={carregando}
        erro={erro}
        mensagemVazia={
          busca.trim() ? 'Nenhuma escola encontrada para a busca.' : 'Nenhuma escola cadastrada ainda.'
        }
        alternandoId={alternandoId}
        onTentarNovamente={recarregar}
        onEditar={editar}
        onAlternarSituacao={handleAlternarSituacao}
      />

      {cadastroAberto && (
        <CadastroEscolaModal
          onFechar={() => setCadastroAberto(false)}
          onSalvar={cadastrar}
          onCadastrada={handleCadastrada}
        />
      )}
    </SuperAdminLayout>
  )
}
