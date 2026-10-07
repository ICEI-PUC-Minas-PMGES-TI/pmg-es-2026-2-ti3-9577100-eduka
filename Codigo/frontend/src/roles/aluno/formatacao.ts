/** Aproveitamento em %, ou null quando ainda não há nada avaliado. */
export function aproveitamento(obtidos: number, avaliados: number): number | null {
  return avaliados > 0 ? Math.round((obtidos / avaliados) * 100) : null
}
