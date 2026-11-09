/** Dispara o download de um arquivo no navegador, sem abrir nova aba. */
export function salvarArquivo(conteudo: Blob, nome: string): void {
  const url = URL.createObjectURL(conteudo)
  const link = document.createElement('a')
  link.href = url
  link.download = nome
  document.body.appendChild(link)
  link.click()
  link.remove()
  // Dá tempo de o navegador começar o download antes de liberar a memória.
  window.setTimeout(() => URL.revokeObjectURL(url), 1000)
}

/** "dados-do-aluno-ana-souza-2026-11-08.pdf": sem acento nem espaço, como o backend nomeia. */
export function nomeDoPdfDeDadosPessoais(nome: string, hoje = new Date()): string {
  const slug = nome
    .normalize('NFD')
    .replace(/\p{M}/gu, '')
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/(^-|-$)/g, '')
  const dia = `${hoje.getFullYear()}-${String(hoje.getMonth() + 1).padStart(2, '0')}-${String(hoje.getDate()).padStart(2, '0')}`
  return `dados-do-aluno-${slug || 'aluno'}-${dia}.pdf`
}
