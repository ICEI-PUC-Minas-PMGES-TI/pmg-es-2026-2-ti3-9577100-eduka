import { useEffect, useState } from 'react'
import { generatePath, useLocation, useNavigate } from 'react-router'
import { Button } from '../../../../components/Button/Button'
import { ConfirmarExclusao } from '../../../../components/ConfirmarExclusao/ConfirmarExclusao'
import { paths } from '../../../../routes/paths'
import { ApiError } from '../../../../services/api'
import type { Escola } from '../../../../types/escola'
import { CadastroEscolaModal } from '../../components/CadastroEscolaModal/CadastroEscolaModal'
import { DetalhesEscolaModal } from '../../components/DetalhesEscolaModal/DetalhesEscolaModal'
import { EscolasStats } from '../../components/EscolasStats/EscolasStats'
import { EscolasTable } from '../../components/EscolasTable/EscolasTable'
import { useEscolas } from '../../hooks/useEscolas'
import { SuperAdminLayout } from '../../layouts/SuperAdminLayout'
import type { EstadoPainelEscolas } from '../EditarEscola/EditarEscola'
import styles from './PainelEscolas.module.css'

export function PainelEscolas() {
  const { escolas, carregando, erro, recarregar, alternarSituacao, cadastrar } = useEscolas()
  const navigate = useNavigate()
  const location = useLocation()
  // Mensagem de sucesso vinda da tela de edição.
  const estadoRecebido = location.state as EstadoPainelEscolas | null
  const [alternandoId, setAlternandoId] = useState<string | null>(null)
  const [erroAcao, setErroAcao] = useState<string | null>(null)
  const [cadastroAberto, setCadastroAberto] = useState(false)
  const [vendo, setVendo] = useState<Escola | null>(null)
  const [suspendendo, setSuspendendo] = useState<Escola | null>(null)
  const [sucesso, setSucesso] = useState<string | null>(estadoRecebido?.sucesso ?? null)

  // Descarta o estado já lido, para a mensagem não voltar ao recarregar a página.
  useEffect(() => {
    if (location.state) navigate(location.pathname, { replace: true, state: null })
  }, [location.state, location.pathname, navigate])

  // Suspender corta o acesso da escola inteira: pede confirmação antes.
  // Reativar não tem risco e vai direto.
  function handleAlternarSituacao(escola: Escola) {
    setErroAcao(null)
    setSucesso(null)
    if (escola.ativo) setSuspendendo(escola)
    else void alternar(escola)
  }

  async function alternar(escola: Escola) {
    setAlternandoId(escola.id)
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

  function verFaturas(escola: Escola) {
    navigate(generatePath(paths.superAdmin.faturasEscola, { id: escola.id }))
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
    <SuperAdminLayout
      titulo="Painel de Escolas"
      subtitulo="Todas as escolas que usam o Eduka."
      acao={
        <Button variant="primary" icon="plus" onClick={abrirCadastro}>
          Cadastrar nova escola
        </Button>
      }
    >

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
        escolas={escolas}
        carregando={carregando}
        erro={erro}
        alternandoId={alternandoId}
        onTentarNovamente={recarregar}
        onVer={setVendo}
        onEditar={editar}
        onFaturas={verFaturas}
        onAlternarSituacao={handleAlternarSituacao}
      />

      {vendo && <DetalhesEscolaModal escola={vendo} onFechar={() => setVendo(null)} />}

      {suspendendo && (
        <ConfirmarExclusao
          titulo="Suspender escola"
          nome={suspendendo.nome}
          rotuloConfirmar="Suspender"
          iconeConfirmar="pause"
          onFechar={() => setSuspendendo(null)}
          onConfirmar={async () => {
            await alternarSituacao(suspendendo)
            setSuspendendo(null)
            setSucesso(`"${suspendendo.nome}" foi suspensa.`)
          }}
        >
          <p>
            Suspender <strong>{suspendendo.nome}</strong>? A assinatura fica suspensa até a escola ser
            reativada.
          </p>
        </ConfirmarExclusao>
      )}

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
