import type { Endereco } from '../../../../../types/escola/escola'
import { mascararCep, somenteDigitos } from '../../../../../utils/formatters'

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

/** Monta o endereço da API. validarEndereco() garante que nenhum campo vai vazio. */
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

/**
 * Todos os campos são obrigatórios, no cadastro e na edição — escolas antigas
 * sem endereço completo precisam completá-lo para salvar.
 */
export function validarEndereco(valores: ValoresEndereco): ErrosEndereco {
  // Na ordem da tela, para o foco ir ao primeiro campo inválido.
  const erros: ErrosEndereco = {}
  if (!valores.logradouro.trim()) erros.logradouro = 'Informe o logradouro.'
  if (!valores.numero.trim()) erros.numero = 'Informe o número.'
  if (!valores.bairro.trim()) erros.bairro = 'Informe o bairro.'
  if (!valores.cidade.trim()) erros.cidade = 'Informe a cidade.'
  if (!valores.estado) erros.estado = 'Selecione o estado.'
  const cep = somenteDigitos(valores.cep)
  if (!cep) erros.cep = 'Informe o CEP.'
  else if (cep.length !== 8) erros.cep = 'CEP deve ter 8 dígitos.'
  return erros
}
