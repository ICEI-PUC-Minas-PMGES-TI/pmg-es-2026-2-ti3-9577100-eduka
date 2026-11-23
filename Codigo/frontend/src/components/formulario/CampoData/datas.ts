// "AAAA-MM-DD" <-> Date no fuso local, para os calendários. new Date("AAAA-MM-DD")
// seria UTC e, no Brasil, cairia no dia anterior.

export function paraData(iso: string): Date | undefined {
  const [ano, mes, dia] = iso.split('-').map(Number)
  return ano && mes && dia ? new Date(ano, mes - 1, dia) : undefined
}

export function paraIso(data: Date): string {
  const mes = String(data.getMonth() + 1).padStart(2, '0')
  const dia = String(data.getDate()).padStart(2, '0')
  return `${data.getFullYear()}-${mes}-${dia}`
}

/** "2026-10-20" -> "20/10/2026". */
export function formatarIso(iso: string): string {
  return paraData(iso)?.toLocaleDateString('pt-BR') ?? ''
}

/** "15/08/2026 – 20/10/2026", ou só uma data quando as duas pontas coincidem. */
export function rotuloDoPeriodo({ de, ate }: { de: string; ate: string }): string {
  return de === ate ? formatarIso(de) : `${formatarIso(de)} – ${formatarIso(ate)}`
}
