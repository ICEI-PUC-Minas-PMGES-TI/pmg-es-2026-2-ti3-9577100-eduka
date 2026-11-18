// Valor em reais nos campos dos modais de cobrança, como o financeiro digita.

/** 890.5 -> "890,50". */
export function valorParaTexto(valor: number): string {
  return valor.toFixed(2).replace('.', ',')
}

/** "1.890,50" -> 1890.5; NaN se não for número. */
export function textoParaValor(texto: string): number {
  return parseFloat(texto.replace(/^R\$\s*/, '').replace(/\./g, '').replace(',', '.'))
}
