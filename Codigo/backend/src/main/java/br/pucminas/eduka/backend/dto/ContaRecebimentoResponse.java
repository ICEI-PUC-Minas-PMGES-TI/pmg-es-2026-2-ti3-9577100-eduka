package br.pucminas.eduka.backend.dto;

import java.math.BigDecimal;
import java.time.Instant;

import br.pucminas.eduka.backend.pagamento.tipos.TipoEmpresa;

/**
 * Conta de recebimento da escola, como o MASTER a ve. Sem credencial, id
 * externo, walletId nem dados bancarios completos.
 *
 * @param status              NAO_INICIADA ou um {@code StatusContaRecebimento}
 * @param motivoNaoHabilitado nulo quando os recebimentos estao habilitados
 * @param cnpj                CNPJ da escola, so leitura
 * @param situacao            nula antes da criacao no provedor
 * @param podeAtivar          true em NAO_INICIADA, ERRO_CRIACAO e VERIFICACAO_PENDENTE
 * @param podeSincronizar     true em AGUARDANDO_APROVACAO, APROVADA e REJEITADA
 */
public record ContaRecebimentoResponse(
        String status,
        boolean recebimentosHabilitados,
        String motivoNaoHabilitado,
        String razaoSocial,
        String emailFinanceiro,
        String telefoneCelular,
        TipoEmpresa tipoEmpresa,
        BigDecimal faturamentoMensal,
        String logradouro,
        String numero,
        String complemento,
        String bairro,
        String cep,
        String cnpj,
        String bancoCodigo,
        String contaFinal,
        SituacaoContaResponse situacao,
        String ultimoErro,
        Instant solicitadaEm,
        Instant aprovadaEm,
        Instant sincronizadaEm,
        boolean podeAtivar,
        boolean podeSincronizar) {
}
