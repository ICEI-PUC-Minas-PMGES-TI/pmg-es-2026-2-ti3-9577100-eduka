import { useState } from 'react'
import { Button } from '../../../../../components/ui/Button/Button'
import { EstadoDashboard, Painel, PainelVazio } from '../../../../../components/layout/Dashboard/Dashboard'
import { useCarregamento } from '../../../../../hooks/useCarregamento'
import { ApiError } from '../../../../../services/api'
import { atenderCorrecao, listarCorrecoesAbertas } from '../../../../../services/pessoas/solicitacaoCorrecaoService'
import type { SolicitacaoCorrecao } from '../../../../../types/pessoas/solicitacaoCorrecao'
import { formatarData } from '../../../../../utils/formatacao'
import styles from './PedidosCorrecao.module.css'

const SEM_PEDIDOS: SolicitacaoCorrecao[] = []

/**
 * Pedidos de correção de dados que os alunos enviaram pelo portal (IU15). A
 * secretaria corrige o cadastro na tela Alunos e marca o pedido como
 * atendido, o que libera o aluno para pedir de novo.
 */
export function PedidosCorrecao({ onVerAlunos }: { onVerAlunos: () => void }) {
  const { dados: pedidos, carregando, erro, recarregar } = useCarregamento(
    listarCorrecoesAbertas,
    SEM_PEDIDOS,
    'Não foi possível encontrar os pedidos de correção.',
  )
  const [atendendo, setAtendendo] = useState<string | null>(null)
  const [erroAtender, setErroAtender] = useState<string | null>(null)

  async function atender(pedido: SolicitacaoCorrecao) {
    setErroAtender(null)
    setAtendendo(pedido.id)
    try {
      await atenderCorrecao(pedido.id)
      recarregar()
    } catch (e) {
      setErroAtender(e instanceof ApiError ? (e.detalhe ?? e.message) : 'Erro inesperado ao atender o pedido.')
    } finally {
      setAtendendo(null)
    }
  }

  return (
    <Painel
      titulo="Pedidos de correção de dados"
      descricao="Enviados pelos alunos no portal. Corrija o cadastro em Alunos e marque como atendido."
      acao={
        <Button size="sm" icon="arrowRight" onClick={onVerAlunos}>
          Ir para Alunos
        </Button>
      }
    >
      <EstadoDashboard carregando={carregando} erro={erro} onTentarDeNovo={recarregar} />
      {erroAtender && (
        <p className={styles.erro} role="alert">
          {erroAtender}
        </p>
      )}
      {!carregando && !erro && pedidos.length === 0 && <PainelVazio>Nenhum pedido aguardando.</PainelVazio>}
      {pedidos.length > 0 && (
        <ul className={styles.lista} aria-label="Pedidos de correção abertos">
          {pedidos.map((pedido) => (
            <li key={pedido.id} className={styles.pedido}>
              <div className={styles.texto}>
                <strong>
                  {pedido.alunoNome ?? 'Aluno'}
                  {pedido.alunoMatricula && ` · ${pedido.alunoMatricula}`}
                </strong>
                <span>{pedido.mensagem}</span>
                <span className={styles.data}>Enviado em {formatarData(pedido.criadoEm.slice(0, 10))}</span>
              </div>
              <Button
                size="sm"
                variant="outline"
                icon="check"
                disabled={atendendo === pedido.id}
                onClick={() => atender(pedido)}
              >
                {atendendo === pedido.id ? 'Salvando…' : 'Marcar como atendido'}
              </Button>
            </li>
          ))}
        </ul>
      )}
    </Painel>
  )
}
