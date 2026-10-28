package br.pucminas.eduka.backend.pagamento.tipos;

import java.math.BigDecimal;

/**
 * Cadastro da conta de recebimento da escola no provedor.
 *
 * <p>
 * {@code dadosBancarios} faz parte do contrato, mas o Asaas nao recebe conta
 * bancaria externa na criacao da subconta (achado A16 do spike); o adaptador
 * nao envia. O grupo decide se o campo sai do contrato.
 * </p>
 */
public record DadosNovaConta(
        String razaoSocial,
        String email,
        String cnpj,
        String telefoneCelular,
        TipoEmpresa tipoEmpresa,
        BigDecimal faturamentoMensal,
        String logradouro,
        String numero,
        String complemento,
        String bairro,
        String cep,
        DadosBancarios dadosBancarios) {
}
