import { useEffect, useState } from 'react'
import { useBlocker } from 'react-router'
import { obterToken } from '../../../services/authService'

/**
 * Pede confirmação antes de perder o que não foi salvo: ao sair da tela, ao
 * fechar a aba e nas trocas dentro da própria tela (passadas por `trocar`).
 */
export function useConfirmarSaida() {
  const [pendente, setPendente] = useState(false)
  // O "Sair" da conta já apagou a sessão antes de navegar: esse não é
  // bloqueado, ou "Continuar editando" deixaria a tela sem sessão.
  const blocker = useBlocker(
    ({ currentLocation, nextLocation }) =>
      pendente && obterToken() !== null && currentLocation.pathname !== nextLocation.pathname,
  )
  const [aoDescartar, setAoDescartar] = useState<(() => void) | null>(null)

  useEffect(() => {
    if (!pendente) return
    const avisar = (e: BeforeUnloadEvent) => e.preventDefault()
    window.addEventListener('beforeunload', avisar)
    return () => window.removeEventListener('beforeunload', avisar)
  }, [pendente])

  function trocar(acao: () => void) {
    if (!pendente) return acao()
    setAoDescartar(() => () => {
      setPendente(false)
      acao()
    })
  }

  function descartar() {
    if (blocker.state === 'blocked') blocker.proceed()
    else aoDescartar?.()
    setAoDescartar(null)
  }

  function continuarEditando() {
    if (blocker.state === 'blocked') blocker.reset()
    setAoDescartar(null)
  }

  return {
    setPendente,
    trocar,
    confirmando: blocker.state === 'blocked' || aoDescartar !== null,
    descartar,
    continuarEditando,
  }
}
