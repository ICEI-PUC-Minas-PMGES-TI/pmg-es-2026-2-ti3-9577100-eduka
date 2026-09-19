package br.pucminas.eduka.backend.exception;

/**
 * Recurso inexistente <em>para o usuario atual</em>. Um registro de outra
 * escola tambem cai aqui: responder 403 confirmaria ao cliente que o id
 * existe em outro tenant, e 404 nao.
 */
public class RecursoNaoEncontradoException extends RuntimeException {

    public RecursoNaoEncontradoException(String mensagem) {
        super(mensagem);
    }
}
