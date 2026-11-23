// Espelha o AvisoPrivacidadeResponse do backend (GET /privacidade/aviso, IU29).
export type AvisoPrivacidade = {
  versao: string
  /** ISO (yyyy-MM-dd). */
  publicadoEm: string
  /** `icone` é o nome de um Icon do frontend. */
  resumo: { icone: string; titulo: string; texto: string }[]
  secoes: { titulo: string; paragrafos: string[] }[]
  /** Quando o usuário logado aceitou esta versão; nulo enquanto não aceitou. */
  aceitoEm: string | null
}
