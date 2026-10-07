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

/** Escolhe a forma pela quantidade: plural(1, 'turma', 'turmas') -> "turma". */
export function plural(quantidade: number, singular: string, varios: string): string {
  return quantidade === 1 ? singular : varios
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

/** Máscara de inteiro com separador de milhar: "1500" -> "1.500". */
export function mascararInteiro(valor: string, maxDigitos: number): string {
  const digitos = somenteDigitos(valor).replace(/^0+/, '').slice(0, maxDigitos)
  return digitos.replace(/\B(?=(\d{3})+(?!\d))/g, '.')
}

/** Máscara de CPF: "12345678909" -> "123.456.789-09". */
export function mascararCpf(valor: string): string {
  return somenteDigitos(valor)
    .slice(0, 11)
    .replace(/^(\d{3})(\d)/, '$1.$2')
    .replace(/^(\d{3})\.(\d{3})(\d)/, '$1.$2.$3')
    .replace(/\.(\d{3})(\d)/, '.$1-$2')
}

/** Confere tamanho e dígitos verificadores de um CPF (com ou sem máscara). */
export function cpfValido(valor: string): boolean {
  const digitos = somenteDigitos(valor)
  if (digitos.length !== 11 || /^(\d)\1+$/.test(digitos)) return false

  const digitoVerificador = (quantidade: number) => {
    let soma = 0
    for (let i = 0; i < quantidade; i++) soma += Number(digitos[i]) * (quantidade + 1 - i)
    const resto = soma % 11
    return resto < 2 ? 0 : 11 - resto
  }

  return digitoVerificador(9) === Number(digitos[9]) && digitoVerificador(10) === Number(digitos[10])
}

type EnderecoFormatavel = {
  logradouro: string | null
  numero: string | null
  bairro: string | null
  cidade: string | null
  estado: string | null
  cep: string | null
}

/** "Rua X, 268 · Centro · Belo Horizonte/MG · CEP 30140-000", ou null se vazio. */
export function formatarEndereco(endereco: EnderecoFormatavel | null): string | null {
  if (!endereco) return null
  const rua = [endereco.logradouro, endereco.numero].filter(Boolean).join(', ')
  const cidade = [endereco.cidade, endereco.estado].filter(Boolean).join('/')
  const cep = endereco.cep ? `CEP ${mascararCep(endereco.cep)}` : ''
  const partes = [rua, endereco.bairro, cidade, cep].filter(Boolean)
  return partes.length > 0 ? partes.join(' · ') : null
}
