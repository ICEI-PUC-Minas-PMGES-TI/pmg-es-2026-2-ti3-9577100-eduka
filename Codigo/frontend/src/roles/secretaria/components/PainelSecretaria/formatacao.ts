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

// O mesmo fuso que o backend usa para "hoje" (FUSO_ESCOLA).
const DATA_DA_ESCOLA = new Intl.DateTimeFormat('en-CA', { timeZone: 'America/Sao_Paulo' })

/** Hoje no fuso da escola, em ISO, para comparar com as datas do backend. */
export function hojeIso(): string {
  // en-CA formata como "AAAA-MM-DD".
  return DATA_DA_ESCOLA.format(new Date())
}
