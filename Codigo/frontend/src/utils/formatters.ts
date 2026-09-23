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

/** Aplica a máscara de CNPJ conforme o usuário digita: "55123456" -> "55.123.456". */
export function mascararCnpj(valor: string): string {
  return somenteDigitos(valor)
    .slice(0, 14)
    .replace(/^(\d{2})(\d)/, '$1.$2')
    .replace(/^(\d{2})\.(\d{3})(\d)/, '$1.$2.$3')
    .replace(/\.(\d{3})(\d)/, '.$1/$2')
    .replace(/(\d{4})(\d)/, '$1-$2')
}

/** Máscara de telefone fixo (10 dígitos) ou celular (11 dígitos). */
export function mascararTelefone(valor: string): string {
  const digitos = somenteDigitos(valor).slice(0, 11)
  if (digitos.length <= 2) return digitos.replace(/^(\d{1,2})/, '($1')
  const meio = digitos.length === 11 ? 5 : 4
  return digitos.replace(new RegExp(`^(\\d{2})(\\d{1,${meio}})(\\d{0,4})`), (_, ddd, a, b) =>
    b ? `(${ddd}) ${a}-${b}` : `(${ddd}) ${a}`,
  )
}

/** Máscara de CEP: "30140071" -> "30140-071". */
export function mascararCep(valor: string): string {
  return somenteDigitos(valor)
    .slice(0, 8)
    .replace(/^(\d{5})(\d)/, '$1-$2')
}

/** Confere tamanho e dígitos verificadores de um CNPJ (com ou sem máscara). */
export function cnpjValido(valor: string): boolean {
  const digitos = somenteDigitos(valor)
  if (digitos.length !== 14 || /^(\d)\1+$/.test(digitos)) return false

  const digitoVerificador = (base: string) => {
    const pesos = base.length === 12 ? [5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2] : [6, 5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2]
    const soma = pesos.reduce((acc, peso, i) => acc + Number(base[i]) * peso, 0)
    const resto = soma % 11
    return resto < 2 ? 0 : 11 - resto
  }

  const primeiro = digitoVerificador(digitos.slice(0, 12))
  const segundo = digitoVerificador(digitos.slice(0, 12) + primeiro)
  return digitos.endsWith(`${primeiro}${segundo}`)
}
