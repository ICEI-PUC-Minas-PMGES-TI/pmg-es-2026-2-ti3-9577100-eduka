import { ApiError } from '../../../../services/api'

export const MAX_DESCRICAO = 255

const MESES = [
  'janeiro',
  'fevereiro',
  'março',
  'abril',
  'maio',
  'junho',
  'julho',
  'agosto',
  'setembro',
  'outubro',
  'novembro',
  'dezembro',
]

/** "2026-11" -> "novembro". */
export function nomeDoMes(isoMes: string): string {
  return MESES[Number(isoMes.slice(5, 7)) - 1] ?? ''
}

/** "1.234,56" ou "1234.56" -> 1234.56; NaN se não for número. */
export function lerValor(texto: string): number {
  const limpo = texto.trim().replace(/\s/g, '')
  const normalizado = limpo.includes(',') ? limpo.replace(/\./g, '').replace(',', '.') : limpo
  return normalizado === '' ? Number.NaN : Number(normalizado)
}

/** Acha o mês na referência ("Plano Avançado · novembro/2026" ou "11/2026") -> "2026-11"; null se não houver. */
export function competenciaDaReferencia(referencia: string): string | null {
  const achado = /(?:^|[^\p{L}\d])(\p{L}+|\d{1,2})\s*\/\s*(\d{4})(?!\d)/iu.exec(referencia)
  if (!achado) return null
  const [, mesTexto, ano] = achado
  const mes = /^\d/.test(mesTexto) ? Number(mesTexto) : MESES.indexOf(mesTexto.toLowerCase()) + 1
  return mes >= 1 && mes <= 12 ? `${ano}-${String(mes).padStart(2, '0')}` : null
}

export function traduzirErroLancar(erro: unknown): string {
  if (erro instanceof ApiError && erro.codigo === 'FATURA_DUPLICADA') {
    return 'Esta escola já tem uma fatura nesta competência. Cancele a anterior para lançar outra.'
  }
  if (erro instanceof ApiError) return erro.detalhe ?? erro.message
  return 'Erro inesperado ao lançar a fatura.'
}
