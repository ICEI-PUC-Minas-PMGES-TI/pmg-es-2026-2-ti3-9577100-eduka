package br.pucminas.eduka.backend.pagamento;

/**
 * O provedor recusou o pedido de forma definitiva (dados invalidos, operacao
 * nao permitida no estado atual). A mensagem e a do provedor, ja sem dados
 * pessoais e com no maximo 500 caracteres.
 */
public class ProvedorRecusouException extends RuntimeException {

    public ProvedorRecusouException(String mensagem) {
        super(mensagem);
    }
}
