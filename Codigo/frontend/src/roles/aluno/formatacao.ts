/** Pontos com até duas casas e vírgula: 8.5 -> "8,5", 10 -> "10". */
export function formatarPontos(valor: number): string {
  return valor.toLocaleString('pt-BR', { maximumFractionDigits: 2 })
}

/** Aproveitamento em %, ou null quando ainda não há nada avaliado. */
export function aproveitamento(obtidos: number, avaliados: number): number | null {
  return avaliados > 0 ? Math.round((obtidos / avaliados) * 100) : null
}
