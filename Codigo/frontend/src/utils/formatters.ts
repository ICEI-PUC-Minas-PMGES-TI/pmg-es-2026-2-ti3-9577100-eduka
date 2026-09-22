/** Mantém só os dígitos, para buscar CNPJ/CPF com ou sem máscara. */
export function somenteDigitos(valor: string): string {
  return valor.replace(/\D/g, '')
}

/** Formata 14 dígitos como CNPJ e 11 como CPF; qualquer outro valor volta como veio. */
export function formatarCnpjCpf(valor: string): string {
  const digitos = somenteDigitos(valor)
  if (digitos.length === 14) {
    return digitos.replace(/(\d{2})(\d{3})(\d{3})(\d{4})(\d{2})/, '$1.$2.$3/$4-$5')
  }
  if (digitos.length === 11) {
    return digitos.replace(/(\d{3})(\d{3})(\d{3})(\d{2})/, '$1.$2.$3-$4')
  }
  return valor
}

/** Iniciais do primeiro e do último nome: "Lucas Silva" -> "LS". */
export function iniciais(nome: string): string {
  const partes = nome.trim().split(/\s+/)
  const primeira = partes[0]?.[0] ?? ''
  const ultima = partes.length > 1 ? partes[partes.length - 1][0] : ''
  return (primeira + ultima).toUpperCase()
}
