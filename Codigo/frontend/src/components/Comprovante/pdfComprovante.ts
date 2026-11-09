import { formatarData } from '../../roles/secretaria/components/PainelSecretaria/formatacao'
import type { Comprovante } from '../../types/faturaPlataforma'
import { ROTULOS_FORMA_PAGAMENTO } from '../../types/pagamentoAluno'
import { salvarArquivo } from '../../utils/arquivos'
import { formatarMoeda } from '../../utils/formatters'

// PDF do comprovante (IU17-D), montado pelo próprio Edúka com os dados que o
// backend confirmou: nada vem da página do provedor. É um PDF 1.4 só de
// texto, com a Helvetica padrão em WinAnsi (cobre os acentos do português),
// então não precisa de biblioteca.

const LARGURA = 595 // A4 em pontos
const ALTURA = 842
const MARGEM = 56

/** Linha do comprovante: rótulo à esquerda, valor à direita. */
export type LinhaComprovante = { rotulo: string; valor: string }

/** Os campos na ordem da tela (IU17-D). */
export function linhasDoComprovante(comprovante: Comprovante): LinhaComprovante[] {
  const linhas: LinhaComprovante[] = [
    { rotulo: 'Valor pago', valor: formatarMoeda(comprovante.valorPago).replace(/\s/g, ' ') },
    ...(comprovante.valorEstornado > 0
      ? [{ rotulo: 'Valor devolvido', valor: formatarMoeda(comprovante.valorEstornado).replace(/\s/g, ' ') }]
      : []),
    { rotulo: 'Pago em', valor: formatarData(comprovante.dataPagamento) },
    { rotulo: 'Forma de pagamento', valor: ROTULOS_FORMA_PAGAMENTO[comprovante.forma] },
    {
      rotulo: 'Pago por',
      valor: [comprovante.pagadorNome, comprovante.pagadorDocumentoMascarado].filter(Boolean).join(' · '),
    },
    {
      rotulo: 'Recebido por',
      valor: [comprovante.beneficiarioNome, comprovante.beneficiarioDocumento].filter(Boolean).join(' · '),
    },
    { rotulo: 'Referência', valor: comprovante.descricao },
    { rotulo: 'Vencimento', valor: formatarData(comprovante.vencimento) },
  ]
  if (comprovante.codigoTransacao) linhas.push({ rotulo: 'Código da transação', valor: comprovante.codigoTransacao })
  linhas.push({ rotulo: 'Número do comprovante', valor: comprovante.numero })
  return linhas
}

/** Texto em WinAnsi dentro de ( ): escapa os delimitadores e troca o que a fonte não tem por "?". */
function textoPdf(texto: string): string {
  let saida = ''
  for (const caractere of texto) {
    const codigo = caractere.charCodeAt(0)
    if (caractere === '(' || caractere === ')' || caractere === '\\') saida += `\\${caractere}`
    else if (codigo >= 32 && codigo < 256) saida += caractere
    else saida += '?'
  }
  return `(${saida})`
}

/** Quebra o texto em linhas de até `maximo` caracteres, pelos espaços. */
function quebrar(texto: string, maximo: number): string[] {
  const linhas: string[] = []
  let atual = ''
  for (const palavra of texto.split(' ')) {
    if (atual && (atual + ' ' + palavra).length > maximo) {
      linhas.push(atual)
      atual = palavra
    } else {
      atual = atual ? `${atual} ${palavra}` : palavra
    }
  }
  if (atual) linhas.push(atual)
  return linhas
}

/** Conteúdo da página: cabeçalho, título e uma linha por campo. */
function conteudoDaPagina(linhas: LinhaComprovante[]): string {
  const comandos: string[] = []
  const texto = (fonte: 'F1' | 'F2', tamanho: number, x: number, y: number, valor: string) =>
    comandos.push(`BT /${fonte} ${tamanho} Tf ${x} ${y} Td ${textoPdf(valor)} Tj ET`)

  let y = ALTURA - MARGEM
  texto('F2', 20, MARGEM, y, 'Edúka')
  y -= 40
  texto('F2', 18, MARGEM, y, 'Pagamento confirmado')
  y -= 18
  texto('F1', 10, MARGEM, y, 'Comprovante emitido pelo Edúka com os dados confirmados pelo banco.')
  y -= 16
  comandos.push(`0.85 G ${MARGEM} ${y} m ${LARGURA - MARGEM} ${y} l S 0 G`)
  y -= 24

  for (const linha of linhas) {
    texto('F1', 11, MARGEM, y, linha.rotulo)
    const partes = quebrar(linha.valor, 48)
    partes.forEach((parte, indice) => texto('F2', 11, MARGEM + 170, y - indice * 14, parte))
    y -= 14 * partes.length + 10
  }
  return comandos.join('\n')
}

/** Monta o arquivo PDF: catálogo, página, duas fontes, conteúdo e a tabela xref. */
export function gerarPdfComprovante(comprovante: Comprovante): Uint8Array<ArrayBuffer> {
  const conteudo = conteudoDaPagina(linhasDoComprovante(comprovante))
  const objetos = [
    '<< /Type /Catalog /Pages 2 0 R >>',
    '<< /Type /Pages /Kids [3 0 R] /Count 1 >>',
    `<< /Type /Page /Parent 2 0 R /MediaBox [0 0 ${LARGURA} ${ALTURA}] ` +
      '/Resources << /Font << /F1 4 0 R /F2 5 0 R >> >> /Contents 6 0 R >>',
    '<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica /Encoding /WinAnsiEncoding >>',
    '<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica-Bold /Encoding /WinAnsiEncoding >>',
    `<< /Length ${conteudo.length} >>\nstream\n${conteudo}\nendstream`,
  ]

  // Tudo em um byte por caractere (WinAnsi): o tamanho da string é o deslocamento em bytes.
  let arquivo = '%PDF-1.4\n'
  const deslocamentos: number[] = []
  objetos.forEach((objeto, indice) => {
    deslocamentos.push(arquivo.length)
    arquivo += `${indice + 1} 0 obj\n${objeto}\nendobj\n`
  })
  const inicioXref = arquivo.length
  arquivo += `xref\n0 ${objetos.length + 1}\n0000000000 65535 f \n`
  arquivo += deslocamentos.map((d) => `${String(d).padStart(10, '0')} 00000 n \n`).join('')
  arquivo += `trailer\n<< /Size ${objetos.length + 1} /Root 1 0 R >>\nstartxref\n${inicioXref}\n%%EOF\n`

  const bytes = new Uint8Array(arquivo.length)
  for (let i = 0; i < arquivo.length; i++) bytes[i] = arquivo.charCodeAt(i) & 0xff
  return bytes
}

/** Gera o PDF e dispara o download no navegador. */
export function baixarPdfComprovante(comprovante: Comprovante): void {
  const blob = new Blob([gerarPdfComprovante(comprovante)], { type: 'application/pdf' })
  salvarArquivo(blob, `comprovante-${comprovante.numero}.pdf`)
}
