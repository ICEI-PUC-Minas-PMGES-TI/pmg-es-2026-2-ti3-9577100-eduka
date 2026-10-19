package br.pucminas.eduka.backend.domain;

/**
 * Situacao da conta de recebimento da escola no provedor (coluna
 * {@code STATUS} de {@code CONTA_RECEBIMENTO_ESCOLA}, V25).
 *
 * <ul>
 * <li>CRIANDO: pedido de criacao enviado ao provedor, sem resposta ainda.</li>
 * <li>VERIFICACAO_PENDENTE: resposta inconclusiva; a proxima ativacao procura
 * a conta pelo CNPJ antes de criar.</li>
 * <li>ERRO_CRIACAO: o provedor recusou; o MASTER pode corrigir e enviar de novo.</li>
 * <li>AGUARDANDO_APROVACAO, APROVADA, REJEITADA: conta criada, seguindo a
 * situacao geral do cadastro no provedor.</li>
 * <li>CREDENCIAL_INDISPONIVEL: conta achada pelo CNPJ, mas sem chave; exige
 * acao manual do grupo.</li>
 * </ul>
 */
public enum StatusContaRecebimento {
    CRIANDO,
    VERIFICACAO_PENDENTE,
    ERRO_CRIACAO,
    AGUARDANDO_APROVACAO,
    APROVADA,
    REJEITADA,
    CREDENCIAL_INDISPONIVEL
}
