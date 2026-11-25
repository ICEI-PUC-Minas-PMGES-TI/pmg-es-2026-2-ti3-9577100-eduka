/**
 * Canal de suporte da plataforma Eduka: o mesmo da página inicial, da tela de
 * acesso suspenso e da Central de ajuda. Trocar aqui troca em todo o sistema.
 */
export const EMAIL_SUPORTE = 'contato@eduka.com.br'

/** Link de e-mail para o suporte com o assunto (e, se houver, o corpo) já preenchidos. */
export function mailtoSuporte(assunto: string, corpo?: string): string {
  const params = new URLSearchParams({ subject: assunto })
  if (corpo) params.set('body', corpo)
  // URLSearchParams troca espaço por "+", que o cliente de e-mail mostraria literalmente.
  return `mailto:${EMAIL_SUPORTE}?${params.toString().replace(/\+/g, '%20')}`
}
