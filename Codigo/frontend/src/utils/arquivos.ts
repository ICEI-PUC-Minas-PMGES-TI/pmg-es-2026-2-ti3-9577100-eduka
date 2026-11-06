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
