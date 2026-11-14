// Risco de atraso dos alunos. Espelha o RiscoAtrasoResponse do backend
// (GET /alunos/{id}/financeiro/risco e GET /cobrancas/risco-atraso).

export type NivelRiscoAtraso = 'BAIXO' | 'MEDIO' | 'ALTO'

export const ROTULOS_NIVEL_RISCO: Record<NivelRiscoAtraso, string> = {
  BAIXO: 'Risco baixo',
  MEDIO: 'Risco médio',
  ALTO: 'Risco alto',
}

export type RegraRiscoAtraso = 'ATRASOS_ANTERIORES' | 'DIAS_MEDIOS_ATRASO' | 'VALOR_EM_ABERTO'

export const ROTULOS_REGRA_RISCO: Record<RegraRiscoAtraso, string> = {
  ATRASOS_ANTERIORES: 'Atrasos anteriores',
  DIAS_MEDIOS_ATRASO: 'Dias médios de atraso',
  VALOR_EM_ABERTO: 'Valor em aberto',
}

/** O que uma regra mediu e quantos pontos (0 a 2) deu. */
export type FatorRisco = {
  regra: RegraRiscoAtraso
  pontos: number
  /** Já em português, para exibir. */
  descricao: string
}

export type IndicadoresRisco = {
  /** Início da janela (ISO yyyy-MM-dd): os últimos 12 meses pelo vencimento. */
  desde: string
  cobrancasVencidas: number
  /** Pagas depois do vencimento mais as vencidas em aberto. */
  atrasos: number
  atrasosPagos: number
  atrasosEmAberto: number
  /** Nulo quando não houve atraso. */
  diasMediosAtraso: number | null
  /** Em aberto e já vencido. */
  valorEmAtraso: number
  valorEmAberto: number
}

export type RiscoAtraso = {
  alunoId: string
  alunoNome: string | null
  alunoNumeroMatricula: string
  nivel: NivelRiscoAtraso
  /** Soma dos pontos das regras, de 0 a 6. */
  pontos: number
  /** Frase curta com o que levou ao nível. */
  motivo: string
  indicadores: IndicadoresRisco
  fatores: FatorRisco[]
}
