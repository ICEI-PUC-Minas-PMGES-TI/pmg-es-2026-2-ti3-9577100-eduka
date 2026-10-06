/** Campo opcional vazio vai como null, para o backend não gravar string vazia. */
export function opcional(valor: string): string | null {
  const limpo = valor.trim()
  return limpo ? limpo : null
}

/** "AAAA-MM-DD" -> "dd/mm/aaaa", sem passar por Date (que mudaria o dia pelo fuso). */
export function formatarData(iso: string): string {
  const [ano, mes, dia] = iso.split('-')
  return ano && mes && dia ? `${dia}/${mes}/${ano}` : iso
}

/** Hoje no fuso local, em ISO, para comparar com as datas do backend. */
export function hojeIso(): string {
  const hoje = new Date()
  const mes = String(hoje.getMonth() + 1).padStart(2, '0')
  const dia = String(hoje.getDate()).padStart(2, '0')
  return `${hoje.getFullYear()}-${mes}-${dia}`
}
