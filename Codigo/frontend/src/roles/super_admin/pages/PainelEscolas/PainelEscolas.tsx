import { useState } from 'react'
import { Button } from '../../../../components/Button/Button'
import { ApiError } from '../../../../services/api'
import type { Escola } from '../../../../types/escola'
import { somenteDigitos } from '../../../../utils/formatters'
import { CadastroEscolaModal } from '../../components/CadastroEscolaModal/CadastroEscolaModal'
import { EscolasStats } from '../../components/EscolasStats/EscolasStats'
import { EscolasTable } from '../../components/EscolasTable/EscolasTable'
import { useEscolas } from '../../hooks/useEscolas'
import { SuperAdminLayout } from '../../layouts/SuperAdminLayout'
import styles from './PainelEscolas.module.css'

function correspondeBusca(escola: Escola, busca: string): boolean {
  const termo = busca.trim().toLowerCase()
  if (!termo) return true

  const digitos = somenteDigitos(termo)
  return (
    escola.nome.toLowerCase().includes(termo) ||
    escola.email.toLowerCase().includes(termo) ||
    (digitos.length > 0 && escola.cnpjCpf.includes(digitos))
  )
}

export function PainelEscolas() {
  const { escolas, carregando, erro, recarregar, alternarSituacao, cadastrar } = useEscolas()
  const [busca, setBusca] = useState('')
  const [alternandoId, setAlternandoId] = useState<string | null>(null)
  const [erroAcao, setErroAcao] = useState<string | null>(null)
  const [cadastroAberto, setCadastroAberto] = useState(false)
  const [sucesso, setSucesso] = useState<string | null>(null)

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

  // TODO: abrir o formulário de edição (PUT /escolas/{id}).
  function editar(_escola: Escola) {}

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
