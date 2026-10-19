package br.pucminas.eduka.backend.pagamento;

/** Chave do provedor ausente, invalida ou sem permissao (HTTP 401/403). */
public class ProvedorNaoConfiguradoException extends RuntimeException {

    public ProvedorNaoConfiguradoException(String mensagem) {
        super(mensagem);
    }
}
