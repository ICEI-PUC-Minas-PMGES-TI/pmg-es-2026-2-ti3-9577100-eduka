import { ApiError } from '../../services/api'

/**
 * Máscara de pontos (nota ou valor da avaliação): até 3 dígitos inteiros e 2
 * decimais, como o backend aceita. Ponto vira vírgula: "8.5" -> "8,5".
 */
export function mascararPontos(texto: string): string {
  const [inteiro = '', ...decimais] = texto.replace(/\./g, ',').replace(/[^\d,]/g, '').split(',')
  const parteInteira = inteiro.slice(0, 3)
  return decimais.length === 0 ? parteInteira : `${parteInteira},${decimais.join('').slice(0, 2)}`
}

/** "8,5" -> 8.5; vazio -> null; texto sem número (ex.: ",") -> NaN. */
export function lerPontos(texto: string): number | null {
  const limpo = texto.trim()
  return limpo ? Number(limpo.replace(',', '.')) : null
}

/** 8.5 -> "8,5", para preencher o campo com o que veio do backend. */
export function pontosParaCampo(valor: number | null): string {
  return valor === null ? '' : String(valor).replace('.', ',')
}

/**
 * Média de aprovação: 60% do que foi avaliado (6,0 numa escala de 10). As
 * avaliações valem pontos, então a regra é a proporção, não a nota absoluta.
 */
export const PERCENTUAL_APROVACAO = 0.6

/** A nota alcançou a média de aprovação do valor da avaliação? */
export function atingiuMedia(nota: number, valor: number): boolean {
  return valor > 0 && nota >= valor * PERCENTUAL_APROVACAO
}

/**
 * Texto de tela para o erro do backend nas avaliações e notas. O motivo vem
 * no `detail`, sem acento.
 */
export function mensagemErroAvaliacao(erro: unknown): string {
  if (!(erro instanceof ApiError)) return 'Erro inesperado. Tente de novo.'
  const detalhe = erro.detalhe ?? ''
  const minusculo = detalhe.toLowerCase()

  const acimaDoValor = /Nota maior que o valor da avaliacao \((.+)\)/.exec(detalhe)
  if (acimaDoValor) return `Há nota acima do valor da avaliação (${acimaDoValor[1].replace('.', ',')} pts).`
  if (minusculo.includes('maiores que o novo valor')) {
    return 'Já há notas lançadas acima deste valor. Corrija as notas antes de diminuir o valor.'
  }
  if (minusculo.includes('ja tem notas lancadas')) {
    return 'A avaliação já tem notas lançadas e não pode ser excluída. Apague as notas antes.'
  }
  if (minusculo.includes('sem matricula ativa')) {
    return 'Algum aluno não está mais ativo na turma. Recarregue a página e tente de novo.'
  }
  if (minusculo.includes('nao alocada')) return 'Você não está mais alocado nesta turma/disciplina.'

  if (erro.status === 404) return 'Esta avaliação não existe mais. Recarregue a página.'
  if (erro.status === 400) return 'Os dados foram recusados pelo servidor. Revise os campos e tente novamente.'
  return erro.message
}
