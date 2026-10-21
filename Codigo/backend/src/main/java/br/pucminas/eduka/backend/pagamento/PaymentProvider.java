package br.pucminas.eduka.backend.pagamento;

import java.time.LocalDate;
import java.util.Optional;

import br.pucminas.eduka.backend.pagamento.tipos.BoletoProvedor;
import br.pucminas.eduka.backend.pagamento.tipos.CobrancaProvedor;
import br.pucminas.eduka.backend.pagamento.tipos.ContaProvedor;
import br.pucminas.eduka.backend.pagamento.tipos.CredencialProvedor;
import br.pucminas.eduka.backend.pagamento.tipos.DadosNovaConta;
import br.pucminas.eduka.backend.pagamento.tipos.DadosPagador;
import br.pucminas.eduka.backend.pagamento.tipos.EventoProvedor;
import br.pucminas.eduka.backend.pagamento.tipos.FormaPagamento;
import br.pucminas.eduka.backend.pagamento.tipos.NovaCobrancaProvedor;
import br.pucminas.eduka.backend.pagamento.tipos.PixProvedor;
import br.pucminas.eduka.backend.pagamento.tipos.SituacaoContaProvedor;

/**
 * Contrato neutro do gateway de pagamento. Nenhum tipo, URL ou status do
 * Asaas aparece aqui: so o adaptador ({@code pagamento.asaas}) o conhece.
 *
 * <p>
 * So o {@link PaymentRoutingService} chama este contrato, e nunca com
 * transacao de banco aberta. Toda operacao pode lancar
 * {@link ProvedorRecusouException} (recusa definitiva),
 * {@link ProvedorIndisponivelException} (resultado inconclusivo) ou
 * {@link ProvedorNaoConfiguradoException}.
 * </p>
 */
public interface PaymentProvider {

    // Contas (a credencial da conta-pai e implicita em criarConta e buscarContaPorCnpj)

    ContaProvedor criarConta(DadosNovaConta dados);

    Optional<ContaProvedor> buscarContaPorCnpj(String cnpj);

    SituacaoContaProvedor consultarSituacaoConta(CredencialProvedor credencialDaConta);

    /**
     * Conta nova nao tem chave PIX e o primeiro QR Code falha (achado A1 do
     * spike). Cria uma chave aleatoria se a conta ainda nao tiver nenhuma;
     * chamar de novo nao cria outra.
     */
    void garantirChavePix(CredencialProvedor credencialDaConta);

    /**
     * Credencial da conta-pai do Eduka, para cobrar as faturas da plataforma
     * (Fluxo A). Sem chave configurada lanca
     * {@link ProvedorNaoConfiguradoException}.
     */
    CredencialProvedor credencialDaContaPai();

    // Clientes e cobrancas (agem na conta dona da credencial)

    String obterOuCriarCliente(CredencialProvedor credencial, DadosPagador pagador);

    CobrancaProvedor criarCobranca(CredencialProvedor credencial, NovaCobrancaProvedor cobranca);

    Optional<CobrancaProvedor> buscarCobrancaPorReferencia(CredencialProvedor credencial, String referenciaExterna);

    CobrancaProvedor consultarCobranca(CredencialProvedor credencial, String idCobrancaExterna);

    CobrancaProvedor alterarFormaEVencimento(CredencialProvedor credencial, String idCobrancaExterna,
            FormaPagamento forma, LocalDate vencimento);

    /** Cobranca inexistente ou ja removida conta como sucesso. */
    void removerCobranca(CredencialProvedor credencial, String idCobrancaExterna);

    PixProvedor obterPix(CredencialProvedor credencial, String idCobrancaExterna);

    BoletoProvedor obterLinhaDigitavel(CredencialProvedor credencial, String idCobrancaExterna);

    /**
     * PDF do boleto gerado pelo provedor, em bytes. O link do PDF nao sai do
     * adaptador. Cobranca que nao e boleto lanca {@link ProvedorRecusouException}.
     */
    byte[] baixarBoletoPdf(CredencialProvedor credencial, String idCobrancaExterna);

    // Webhook

    /**
     * Se o token recebido junto do aviso e o cadastrado no webhook. Sem token
     * configurado, nenhum aviso e autentico.
     */
    boolean webhookAutentico(String tokenRecebido);

    EventoProvedor interpretarEvento(String corpoJson);

    // Somente Sandbox: fora dele lancam IllegalStateException

    void sandboxAprovarConta(CredencialProvedor credencialDaConta);

    void sandboxConfirmarPagamento(CredencialProvedor credencial, String idCobrancaExterna);
}
