import type { ResponsavelRequest } from '../../../../../types/pessoas/aluno'
import { cpfValido, somenteDigitos } from '../../../../../utils/formatters'

export type CamposResponsavel = {
  nome: string
  parentesco: string
  cpf: string
  email: string
  telefone: string
  financeiro: boolean
}

export type ErrosResponsavel = Partial<Record<keyof CamposResponsavel, string>>

export const RESPONSAVEL_VAZIO: CamposResponsavel = {
  nome: '',
  parentesco: '',
  cpf: '',
  email: '',
  telefone: '',
  financeiro: false,
}

const EMAIL_REGEX = /^[^\s@]+@[^\s@]+\.[^\s@]+$/

/**
 * Na ordem da tela, para o foco ir ao primeiro campo inválido. O financeiro é
 * o pagador no Asaas, que exige CPF; `exigirCpf` é a edição, em que o backend
 * pede o CPF de qualquer responsável.
 */
export function validarResponsavel(responsavel: CamposResponsavel, exigirCpf = false): ErrosResponsavel {
  const erros: ErrosResponsavel = {}
  if (!responsavel.nome.trim()) erros.nome = 'Informe o nome.'
  if (!responsavel.cpf) {
    if (responsavel.financeiro) erros.cpf = 'Informe o CPF: quem paga as cobranças precisa ter CPF.'
    else if (exigirCpf) erros.cpf = 'Informe o CPF.'
  } else if (!cpfValido(responsavel.cpf)) {
    erros.cpf = 'CPF inválido.'
  }
  if (responsavel.email.trim() && !EMAIL_REGEX.test(responsavel.email.trim())) erros.email = 'E-mail inválido.'
  const telefone = somenteDigitos(responsavel.telefone)
  if (telefone && telefone.length < 10) erros.telefone = 'Telefone incompleto.'
  return erros
}

/** Campo opcional vazio vai como null, para o backend não gravar string vazia. */
function opcional(valor: string): string | null {
  const limpo = valor.trim()
  return limpo ? limpo : null
}

export function responsavelParaRequest(responsavel: CamposResponsavel): ResponsavelRequest {
  return {
    nome: responsavel.nome.trim(),
    parentesco: opcional(responsavel.parentesco),
    cpf: opcional(somenteDigitos(responsavel.cpf)),
    email: opcional(responsavel.email),
    telefone: opcional(somenteDigitos(responsavel.telefone)),
    financeiro: responsavel.financeiro,
  }
}
