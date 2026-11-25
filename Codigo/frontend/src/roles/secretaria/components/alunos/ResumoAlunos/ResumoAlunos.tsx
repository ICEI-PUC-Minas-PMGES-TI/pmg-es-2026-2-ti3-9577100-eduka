import { useState } from 'react'
import { StatCard, StatCards } from '../../../../../components/ui/StatCard/StatCard'
import type { ResumoAlunos as Resumo, ResumoFinanceiroAlunos } from '../../../../../types/pessoas/aluno'
import { formatarMoeda, plural } from '../../../../../utils/formatters'
import styles from './ResumoAlunos.module.css'

export type VisaoDoResumo = 'ESCOLA' | 'FINANCEIRO'

/** Onde fica a última visão que o MASTER escolheu; só neste navegador. */
const CHAVE_VISAO = 'eduka.alunos.resumo'

function visaoGuardada(): VisaoDoResumo {
  try {
    return localStorage.getItem(CHAVE_VISAO) === 'FINANCEIRO' ? 'FINANCEIRO' : 'ESCOLA'
  } catch {
    return 'ESCOLA'
  }
}

function guardarVisao(visao: VisaoDoResumo): void {
  try {
    localStorage.setItem(CHAVE_VISAO, visao)
  } catch {
    // Sem storage (ex.: navegação anônima), a escolha vale só até sair da tela.
  }
}

/** Atalhos dos cards financeiros: aplicam o filtro correspondente na lista. */
export type AtalhosDoResumo = {
  emAtraso: () => void
  venceEm7Dias: () => void
  semCobranca: () => void
}

type ResumoAlunosProps = {
  resumo: Resumo
  /**
   * Quais cards mostrar. ESCOLA: os da secretaria. FINANCEIRO: os do
   * financeiro. AMBOS: o MASTER escolhe entre os dois num seletor.
   */
  visoes: 'ESCOLA' | 'FINANCEIRO' | 'AMBOS'
  /** Texto do card "Sem turma" para quem matricula (secretaria e MASTER). */
  podeMatricular: boolean
  atalhos: AtalhosDoResumo
}

/**
 * Cards do topo da tela Alunos. Contam a escola inteira: não mudam com a busca
 * nem com os filtros. Sem o resumo financeiro (o backend não mandou), ficam os
 * da escola.
 */
export function ResumoAlunos({ resumo, visoes, podeMatricular, atalhos }: ResumoAlunosProps) {
  const [escolhida, setEscolhida] = useState<VisaoDoResumo>(visaoGuardada)
  const financeiro = resumo.financeiro ?? null
  const comSeletor = visoes === 'AMBOS' && financeiro !== null
  const visao: VisaoDoResumo = financeiro === null ? 'ESCOLA' : visoes === 'AMBOS' ? escolhida : visoes

  function escolher(nova: VisaoDoResumo) {
    setEscolhida(nova)
    guardarVisao(nova)
  }

  return (
    <div className={styles.resumo}>
      {comSeletor && (
        <div className={styles.seletor} role="group" aria-label="Resumo">
          <span className={styles.seletorRotulo} aria-hidden="true">
            Resumo
          </span>
          {(['ESCOLA', 'FINANCEIRO'] as const).map((v) => (
            <button
              key={v}
              type="button"
              aria-pressed={visao === v}
              className={`${styles.opcao} ${visao === v ? styles.opcaoAtiva : ''}`}
              onClick={() => escolher(v)}
            >
              {v === 'ESCOLA' ? 'Escola' : 'Financeiro'}
            </button>
          ))}
        </div>
      )}
      {visao === 'FINANCEIRO' && financeiro ? (
        <CardsFinanceiros resumo={resumo} financeiro={financeiro} atalhos={atalhos} />
      ) : (
        <CardsDaEscola resumo={resumo} podeMatricular={podeMatricular} />
      )}
    </div>
  )
}

/** Inativo e anonimizado não estão mais na escola: não contam como alunos cadastrados. */
function alunosNaEscola(resumo: Resumo): { naEscola: number; inativos: number } {
  const inativos = resumo.porSituacao.INATIVO
  return { naEscola: resumo.total - resumo.porSituacao.ANONIMIZADO - inativos, inativos }
}

function CardAlunosCadastrados({ resumo }: { resumo: Resumo }) {
  const { naEscola, inativos } = alunosNaEscola(resumo)
  return (
    <StatCard
      label="Alunos cadastrados"
      value={naEscola}
      descricao={inativos ? `Na escola · ${inativos} ${plural(inativos, 'inativo', 'inativos')}` : 'Na escola'}
      destaque
    />
  )
}

function CardsDaEscola({ resumo, podeMatricular }: { resumo: Resumo; podeMatricular: boolean }) {
  const semTurma = resumo.porSituacao.SEM_TURMA
  return (
    <StatCards rotulo="Resumo dos alunos">
      <CardAlunosCadastrados resumo={resumo} />
      <StatCard label="Com turma" value={resumo.porSituacao.MATRICULADO} descricao="Com matrícula ativa" />
      <StatCard
        label="Sem turma"
        value={semTurma}
        descricao={semTurma ? (podeMatricular ? 'Matricule em Matrículas' : 'Sem matrícula ativa') : 'Todos matriculados'}
        atencao={semTurma > 0}
      />
    </StatCards>
  )
}

function CardsFinanceiros({
  resumo,
  financeiro,
  atalhos,
}: {
  resumo: Resumo
  financeiro: ResumoFinanceiroAlunos
  atalhos: AtalhosDoResumo
}) {
  const emAtraso = financeiro.porSituacao.EM_ATRASO
  const semCobranca = financeiro.porSituacao.SEM_COBRANCA
  return (
    <StatCards rotulo="Resumo financeiro dos alunos">
      <CardAlunosCadastrados resumo={resumo} />
      <StatCard
        label="Em atraso"
        value={emAtraso}
        descricao={
          emAtraso ? (
            <Atalho
              texto={formatarMoeda(financeiro.valorEmAtraso)}
              rotulo="Ver alunos em atraso"
              onClick={atalhos.emAtraso}
            />
          ) : (
            'Ninguém em atraso'
          )
        }
        alerta={emAtraso > 0}
      />
      <StatCard
        label="Vencem em 7 dias"
        value={financeiro.venceEm7Dias}
        descricao={
          financeiro.venceEm7Dias ? (
            <Atalho
              texto={formatarMoeda(financeiro.valorVenceEm7Dias)}
              rotulo="Ver alunos com vencimento em 7 dias"
              onClick={atalhos.venceEm7Dias}
            />
          ) : (
            'Nada vence esta semana'
          )
        }
        atencao={financeiro.venceEm7Dias > 0}
      />
      <StatCard
        label="Sem cobrança"
        value={semCobranca}
        descricao={
          semCobranca ? (
            <Atalho texto="Nenhuma cobrança lançada" rotulo="Ver alunos sem cobrança" onClick={atalhos.semCobranca} />
          ) : (
            'Todos com cobrança'
          )
        }
      />
    </StatCards>
  )
}

/** Descrição do card seguida do link que aplica o filtro na lista. */
function Atalho({ texto, rotulo, onClick }: { texto: string; rotulo: string; onClick: () => void }) {
  return (
    <>
      {texto} ·{' '}
      <button type="button" className={styles.atalho} onClick={onClick} aria-label={rotulo}>
        Ver
      </button>
    </>
  )
}
