import { describe, expect, it } from 'vitest'
import type { Comprovante } from '../../types/faturaPlataforma'
import { gerarPdfComprovante, linhasDoComprovante } from './pdfComprovante'

const COMPROVANTE: Comprovante = {
  numero: 'b9f0c1d2-0000-4000-8000-000000004821',
  beneficiarioNome: 'Colégio Santo Agostinho',
  beneficiarioDocumento: '61.093.123/0001-45',
  pagadorNome: 'Cláudia Andrade',
  pagadorDocumentoMascarado: '***.456.789-**',
  descricao: 'Mensalidade de setembro (2026)',
  vencimento: '2026-09-10',
  valorPago: 890,
  dataPagamento: '2026-09-09',
  forma: 'PIX',
  codigoTransacao: 'pay_8f2k1x9q7m3d',
}

/** Bytes do PDF lidos como Latin-1, um caractere por byte. */
function comoTexto(bytes: Uint8Array): string {
  return Array.from(bytes, (b) => String.fromCharCode(b)).join('')
}

describe('pdfComprovante', () => {
  it('leva os campos da IU17-D, na ordem da tela', () => {
    const linhas = linhasDoComprovante(COMPROVANTE)
    expect(linhas.map((l) => l.rotulo)).toEqual([
      'Valor pago',
      'Pago em',
      'Forma de pagamento',
      'Pago por',
      'Recebido por',
      'Referência',
      'Vencimento',
      'Código da transação',
      'Número do comprovante',
    ])
    expect(linhas[3].valor).toBe('Cláudia Andrade · ***.456.789-**')
    expect(linhas[4].valor).toBe('Colégio Santo Agostinho · 61.093.123/0001-45')
  })

  it('gera um PDF válido, com os acentos em WinAnsi e os parênteses escapados', () => {
    const pdf = comoTexto(gerarPdfComprovante(COMPROVANTE))

    expect(pdf.startsWith('%PDF-1.4')).toBe(true)
    expect(pdf.trimEnd().endsWith('%%EOF')).toBe(true)
    expect(pdf).toContain('(Colégio Santo Agostinho · 61.093.123/0001-45)')
    expect(pdf).toContain('(Mensalidade de setembro \\(2026\\))')
    expect(pdf).toContain('(pay_8f2k1x9q7m3d)')

    // Cada entrada da xref aponta para o início do objeto correspondente.
    const inicioXref = Number(pdf.match(/startxref\n(\d+)/)?.[1])
    expect(pdf.slice(inicioXref, inicioXref + 4)).toBe('xref')
    const deslocamentos = [...pdf.slice(inicioXref).matchAll(/^(\d{10}) 00000 n $/gm)].map((m) => Number(m[1]))
    expect(deslocamentos).toHaveLength(6)
    deslocamentos.forEach((d, i) => expect(pdf.slice(d, d + `${i + 1} 0 obj`.length)).toBe(`${i + 1} 0 obj`))
  })

  it('o Length do conteúdo bate com o tamanho do stream', () => {
    const pdf = comoTexto(gerarPdfComprovante(COMPROVANTE))
    const tamanho = Number(pdf.match(/<< \/Length (\d+) >>\nstream\n/)?.[1])
    const inicio = pdf.indexOf('stream\n') + 'stream\n'.length
    expect(pdf.slice(inicio + tamanho, inicio + tamanho + '\nendstream'.length)).toBe('\nendstream')
  })
})
