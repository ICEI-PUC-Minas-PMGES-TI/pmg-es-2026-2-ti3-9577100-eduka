import type { Endereco } from '../../../../types/escola'
import { mascararCep, somenteDigitos } from '../../../../utils/formatters'

// Estado do formulário: tudo string, com o CEP mascarado.
export type ValoresEndereco = {
  logradouro: string
  numero: string
  bairro: string
  cidade: string
  estado: string
  cep: string
}

export type ErrosEndereco = Partial<Record<keyof ValoresEndereco, string>>

export const ENDERECO_VAZIO: ValoresEndereco = {
  logradouro: '',
  numero: '',
  bairro: '',
  cidade: '',
  estado: '',
  cep: '',
}

export const UFS = [
  'AC', 'AL', 'AP', 'AM', 'BA', 'CE', 'DF', 'ES', 'GO', 'MA', 'MT', 'MS', 'MG', 'PA',
  'PB', 'PR', 'PE', 'PI', 'RJ', 'RN', 'RS', 'RO', 'RR', 'SC', 'SP', 'SE', 'TO',
] as const

// Limites das colunas de ESCOLA no banco (V7).
export const MAX_LOGRADOURO = 200
export const MAX_NUMERO = 10
export const MAX_BAIRRO = 100
export const MAX_CIDADE = 100

export function valoresDoEndereco(endereco: Endereco | null): ValoresEndereco {
  return {
    logradouro: endereco?.logradouro ?? '',
    numero: endereco?.numero ?? '',
    bairro: endereco?.bairro ?? '',
    cidade: endereco?.cidade ?? '',
    estado: endereco?.estado ?? '',
    cep: mascararCep(endereco?.cep ?? ''),
  }
}

/** Monta o endereço da API. Campos vazios vão como "" para o backend limpá-los. */
export function enderecoDosValores(valores: ValoresEndereco): Endereco {
  return {
    logradouro: valores.logradouro.trim(),
    numero: valores.numero.trim(),
    bairro: valores.bairro.trim(),
    cidade: valores.cidade.trim(),
    estado: valores.estado,
    cep: somenteDigitos(valores.cep),
  }
}

export function validarEndereco(valores: ValoresEndereco): ErrosEndereco {
  const erros: ErrosEndereco = {}
  const cep = somenteDigitos(valores.cep)
  if (cep && cep.length !== 8) erros.cep = 'CEP deve ter 8 dígitos.'
  return erros
}
