/** Canais de atendimento da escola do usuário logado (GET /minha-escola/contato). */
export type ContatoEscola = {
  nome: string
  email: string | null
  telefone: string | null
  cidade: string | null
  estado: string | null
}
