import { useCallback, type ReactNode } from 'react'
import { useCarregamento } from '../../hooks/useCarregamento'
import { listarRiscoAtraso } from '../../services/cobrancaService'
import {
  ROTULOS_NIVEL_RISCO,
  ROTULOS_REGRA_RISCO,
  type NivelRiscoAtraso,
  type RiscoAtraso,
} from '../../types/riscoAtraso'
import { formatarMoeda } from '../../utils/formatters'
import { Badge } from '../Badge/Badge'
import { Painel, PainelVazio } from '../Dashboard/Dashboard'
import styles from './RiscoAtraso.module.css'

// Risco de atraso dos alunos (GET /cobrancas/risco-atraso e
// GET /alunos/{id}/financeiro/risco): o nível vem com o motivo, montado no
// backend a partir de regras explicáveis.

const TOM_NIVEL: Record<NivelRiscoAtraso, 'success' | 'warning' | 'danger'> = {
  BAIXO: 'success',
  MEDIO: 'warning',
  ALTO: 'danger',
}

/** Itens do painel do Início; o resto vira "e mais N". */
const LIMITE_LISTA = 8

// Estável: o useCarregamento pede o valor inicial sempre igual.
const NENHUM: RiscoAtraso[] = []

export function NivelRisco({ nivel }: { nivel: NivelRiscoAtraso }) {
  return (
    <Badge tone={TOM_NIVEL[nivel]} icon={nivel === 'BAIXO' ? undefined : 'alert'}>
      {ROTULOS_NIVEL_RISCO[nivel]}
    </Badge>
  )
}

/**
 * Painel do Início com os alunos de risco médio e alto, do maior para o
 * menor, cada um com o motivo. Busca sozinho, para o resto do Início não
 * esperar por ele.
 */
export function AlunosEmRisco({ acao }: { acao?: ReactNode }) {
  const buscar = useCallback((signal: AbortSignal) => listarRiscoAtraso('MEDIO', signal), [])
  const { dados, carregando, erro } = useCarregamento(buscar, NENHUM, 'Não foi possível calcular o risco.')

  function conteudo() {
    if (carregando) return <PainelVazio>Calculando o risco dos alunos…</PainelVazio>
    if (erro) return <p className={styles.erro} role="alert">{erro}</p>
    if (dados.length === 0) {
      return <PainelVazio>Nenhum aluno com risco médio ou alto de atraso.</PainelVazio>
    }
    const restantes = dados.length - LIMITE_LISTA
    return (
      <>
        <ul className={styles.lista}>
          {dados.slice(0, LIMITE_LISTA).map((risco) => (
            <li key={risco.alunoId} className={styles.item}>
              <div className={styles.corpo}>
                <strong className={styles.titulo}>{risco.alunoNome ?? 'Aluno sem nome'}</strong>
                <span className={styles.contexto}>{risco.motivo}</span>
              </div>
              <div className={styles.lado}>
                {risco.indicadores.valorEmAtraso > 0 && (
                  <strong className={styles.valor}>{formatarMoeda(risco.indicadores.valorEmAtraso)}</strong>
                )}
                <NivelRisco nivel={risco.nivel} />
              </div>
            </li>
          ))}
        </ul>
        {restantes > 0 && (
          <p className={styles.mais}>
            E mais {restantes} {restantes === 1 ? 'aluno' : 'alunos'} com risco médio ou alto.
          </p>
        )}
      </>
    )
  }

  return (
    <Painel
      titulo="Risco de atraso"
      descricao="Pelos atrasos dos últimos 12 meses, os dias de atraso e o valor vencido em aberto."
      acao={acao}
    >
      {conteudo()}
    </Painel>
  )
}

/** Nível, motivo e o que cada regra mediu, para o cadastro do aluno. */
export function RiscoDoAluno({ risco }: { risco: RiscoAtraso }) {
  return (
    <section className={styles.doAluno} aria-label="Risco de atraso">
      <div className={styles.cabecalho}>
        <strong className={styles.titulo}>Risco de atraso</strong>
        <NivelRisco nivel={risco.nivel} />
      </div>
      <p className={styles.motivo}>{risco.motivo}</p>
      <ul className={styles.fatores}>
        {risco.fatores.map((fator) => (
          <li key={fator.regra}>
            <strong>{ROTULOS_REGRA_RISCO[fator.regra]}:</strong> {fator.descricao}{' '}
            {fator.pontos > 0 && (
              <span className={styles.pontuou}>
                (+{fator.pontos} {fator.pontos === 1 ? 'ponto' : 'pontos'})
              </span>
            )}
          </li>
        ))}
      </ul>
    </section>
  )
}
