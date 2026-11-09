import { useEffect, useState } from 'react'

/** Espera entre a última tecla da busca e a consulta ao backend. */
export const ESPERA_BUSCA_MS = 300

/**
 * O texto da busca, sem espaços nas pontas, só depois que a digitação para
 * por `espera` ms: cada tecla não vira uma consulta ao backend.
 */
export function useTermoAdiado(texto: string, espera = ESPERA_BUSCA_MS): string {
  const termo = texto.trim()
  const [adiado, setAdiado] = useState(termo)

  useEffect(() => {
    if (termo === adiado) return
    const temporizador = window.setTimeout(() => setAdiado(termo), espera)
    return () => window.clearTimeout(temporizador)
  }, [termo, adiado, espera])

  return adiado
}
