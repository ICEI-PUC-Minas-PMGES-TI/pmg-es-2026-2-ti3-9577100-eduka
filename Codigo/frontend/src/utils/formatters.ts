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

/** Pontos com até duas casas e vírgula: 8.5 -> "8,5", 10 -> "10". */
export function formatarPontos(valor: number): string {
  return valor.toLocaleString('pt-BR', { maximumFractionDigits: 2 })
}

/** Formata valor monetário em reais: 890 -> "R$ 890,00". */
export function formatarMoeda(valor: number): string {
  return valor.toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' })
}

const MOEDA_COMPACTA = new Intl.NumberFormat('pt-BR', {
  style: 'currency',
  currency: 'BRL',
  notation: 'compact',
  minimumFractionDigits: 0,
  maximumFractionDigits: 1,
})

/** Reais em pouco espaço, para eixo de gráfico: 1500 -> "R$ 1,5 mil", 850 -> "R$ 850". */
export function formatarMoedaCompacta(valor: number): string {
  return MOEDA_COMPACTA.format(valor)
}

const SALDO_COMPACTO = new Intl.NumberFormat('pt-BR', {
  notation: 'compact',
  maximumFractionDigits: 1,
  signDisplay: 'exceptZero',
})

/** Saldo em pouco espaço, com o sinal: 51000 -> "+51 mil", -850 -> "-850". */
export function formatarSaldoCompacto(valor: number): string {
  return SALDO_COMPACTO.format(valor)
}

/** Percentual com até uma casa: 6.8 -> "6,8%"; sem base para o cálculo (null) -> "—". */
export function formatarPercentual(valor: number | null): string {
  return valor === null ? '—' : `${valor.toLocaleString('pt-BR', { maximumFractionDigits: 1 })}%`
}

// O mesmo fuso que o backend usa para "hoje" (FUSO_ESCOLA).
const DATA_DA_ESCOLA = new Intl.DateTimeFormat('en-CA', { timeZone: 'America/Sao_Paulo' })

/** Hoje no fuso da escola, em ISO, para comparar com as datas do backend. */
export function hojeIso(): string {
  // en-CA formata como "AAAA-MM-DD".
  return DATA_DA_ESCOLA.format(new Date())
}

/** Dia e mês de uma data ISO: "2026-10-27" -> "27/10". */
export function formatarDiaMes(data: string): string {
  const [, mes, dia] = data.split('-')
  return `${dia}/${mes}`
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
