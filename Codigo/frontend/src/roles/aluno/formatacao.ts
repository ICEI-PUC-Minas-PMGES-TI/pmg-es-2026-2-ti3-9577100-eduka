/** Aproveitamento em %, ou null quando ainda não há nada avaliado. */
export function aproveitamento(obtidos: number, avaliados: number): number | null {
  return avaliados > 0 ? Math.round((obtidos / avaliados) * 100) : null
}

/**
 * Linha digitável de boleto (47 dígitos) nos blocos do banco:
 * 00000.00000 00000.000000 00000.000000 0 00000000000000. Fora disso, devolve como veio.
 */
export function formatarLinhaDigitavel(linha: string): string {
  const d = linha.replace(/\D/g, '')
  if (d.length !== 47) return linha
  return `${d.slice(0, 5)}.${d.slice(5, 10)} ${d.slice(10, 15)}.${d.slice(15, 21)} ${d.slice(21, 26)}.${d.slice(26, 32)} ${d.slice(32, 33)} ${d.slice(33)}`
}
