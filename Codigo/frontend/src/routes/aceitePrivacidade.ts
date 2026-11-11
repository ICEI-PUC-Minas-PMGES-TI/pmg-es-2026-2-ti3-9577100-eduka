import { obterToken } from '../services/authService'

/** Destino depois do aceite do aviso de privacidade: a tela que o usuário tentou abrir. */
export type EstadoAvisoPrivacidade = { voltarPara: string }

// Token cujo aceite já foi conferido nesta aba. Outro login traz outro token
// e confere de novo; trocar de tela não repete a consulta.
let tokenComAceite: string | null = null

/** O usuário desta sessão já aceitou o aviso em vigor (conferido nesta aba). */
export function aceiteConferido(): boolean {
  const token = obterToken()
  return token !== null && token === tokenComAceite
}

/** Depois do aceite (ou de a consulta mostrar que já havia), o painel abre sem consultar de novo. */
export function registrarAceitePrivacidade(): void {
  tokenComAceite = obterToken()
}

/** Só para os testes começarem sem o aceite de um teste anterior. */
export function esquecerAceitePrivacidade(): void {
  tokenComAceite = null
}
