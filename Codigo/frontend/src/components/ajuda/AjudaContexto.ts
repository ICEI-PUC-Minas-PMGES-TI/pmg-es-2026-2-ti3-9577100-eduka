import { createContext, useContext } from 'react'

/**
 * Abre a Central de ajuda da tela. O PainelLayout fornece; fora dele (telas
 * abertas) não há provedor e o valor é nulo.
 */
export const AjudaContexto = createContext<(() => void) | null>(null)

export function useAbrirAjuda(): (() => void) | null {
  return useContext(AjudaContexto)
}
