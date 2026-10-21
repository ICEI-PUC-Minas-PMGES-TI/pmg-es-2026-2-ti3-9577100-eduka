package br.pucminas.eduka.backend.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import br.pucminas.eduka.backend.pagamento.ProvedorIndisponivelException;
import br.pucminas.eduka.backend.pagamento.ProvedorNaoConfiguradoException;
import br.pucminas.eduka.backend.pagamento.ProvedorRecusouException;

/**
 * Traduz excecoes em respostas HTTP no formato ProblemDetail (RFC 9457).
 *
 * <p>
 * Os erros de seguranca chegam aqui tanto de {@code @PreAuthorize} e servicos
 * (via DispatcherServlet) quanto dos filtros do Spring Security (via
 * {@code HandlerExceptionResolver}, configurado em {@code JwtConfig}). Assim
 * 401 e 403 tem sempre o mesmo corpo, venham de onde vierem.
 * </p>
 *
 * <p>
 * As mensagens sao deliberadamente genericas: nao dizem se o login existe,
 * se o recurso existe em outra escola, nem qual validacao interna falhou.
 * </p>
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ProblemDetail> naoAutenticado(AuthenticationException ex) {
        ProblemDetail problem = problema(HttpStatus.UNAUTHORIZED, "Autenticacao necessaria");
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .header(HttpHeaders.WWW_AUTHENTICATE, "Bearer")
                .body(problem);
    }

    @ExceptionHandler(CredenciaisInvalidasException.class)
    public ResponseEntity<ProblemDetail> credenciaisInvalidas(CredenciaisInvalidasException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(problema(HttpStatus.UNAUTHORIZED, ex.getMessage()));
    }

    @ExceptionHandler(SenhaProvisoriaExpiradaException.class)
    public ResponseEntity<ProblemDetail> senhaProvisoriaExpirada(SenhaProvisoriaExpiradaException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(problema(HttpStatus.UNAUTHORIZED, ex.getMessage()));
    }

    /**
     * Cada desfecho do codigo de verificacao tem um status proprio, porque o
     * front reage diferente a cada um: 401 deixa tentar de novo, 410 pede o
     * reenvio, 404 manda de volta ao login e 429 pede para esperar.
     */
    @ExceptionHandler(CodigoVerificacaoIncorretoException.class)
    public ResponseEntity<ProblemDetail> codigoIncorreto(CodigoVerificacaoIncorretoException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(problema(HttpStatus.UNAUTHORIZED, ex.getMessage()));
    }

    @ExceptionHandler(CodigoVerificacaoExpiradoException.class)
    public ResponseEntity<ProblemDetail> codigoExpirado(CodigoVerificacaoExpiradoException ex) {
        return ResponseEntity.status(HttpStatus.GONE)
                .body(problema(HttpStatus.GONE, ex.getMessage()));
    }

    @ExceptionHandler(VerificacaoLoginEncerradaException.class)
    public ResponseEntity<ProblemDetail> verificacaoEncerrada(VerificacaoLoginEncerradaException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(problema(HttpStatus.NOT_FOUND, ex.getMessage()));
    }

    @ExceptionHandler(ReenvioCodigoIndisponivelException.class)
    public ResponseEntity<ProblemDetail> reenvioIndisponivel(ReenvioCodigoIndisponivelException ex) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .body(problema(HttpStatus.TOO_MANY_REQUESTS, ex.getMessage()));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ProblemDetail> acessoNegado(AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(problema(HttpStatus.FORBIDDEN, "Acesso negado"));
    }

    @ExceptionHandler(RecursoNaoEncontradoException.class)
    public ResponseEntity<ProblemDetail> naoEncontrado(RecursoNaoEncontradoException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(problema(HttpStatus.NOT_FOUND, ex.getMessage()));
    }

    @ExceptionHandler({ LoginJaCadastradoException.class, CnpjJaCadastradoException.class,
            CpfJaCadastradoException.class, AcessoJaAtivadoException.class,
            ProfessorComHistoricoException.class, AlocacaoJaExisteException.class,
            MatriculaJaExisteException.class, TurmaSemVagasException.class,
            NumeroMatriculaJaCadastradoException.class, AvaliacaoComNotasException.class,
            RegistroEmUsoException.class })
    public ResponseEntity<ProblemDetail> recursoDuplicado(RuntimeException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(problema(HttpStatus.CONFLICT, ex.getMessage()));
    }

    @ExceptionHandler({ SenhaInvalidaException.class, IllegalArgumentException.class })
    public ResponseEntity<ProblemDetail> requisicaoInvalida(RuntimeException ex) {
        return ResponseEntity.badRequest()
                .body(problema(HttpStatus.BAD_REQUEST, ex.getMessage()));
    }

    /**
     * JSON malformado ou valor fora de um enum (ex.: plano inexistente). Sem
     * isto cairia no {@link #erroInterno} e viraria 500.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ProblemDetail> corpoIlegivel(HttpMessageNotReadableException ex) {
        return ResponseEntity.badRequest()
                .body(problema(HttpStatus.BAD_REQUEST, "corpo da requisição inválido"));
    }

    /**
     * Parametro de URL ou de query que nao converte para o tipo esperado
     * (UUID malformado, valor fora de um enum...). Sem isto viraria 500.
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ProblemDetail> parametroInvalido(MethodArgumentTypeMismatchException ex) {
        return ResponseEntity.badRequest()
                .body(problema(HttpStatus.BAD_REQUEST, "parâmetro inválido: " + ex.getName()));
    }

    /**
     * Erros do provedor de pagamento. Levam a propriedade {@code codigo}, que o
     * front usa para escolher a mensagem. A recusa repassa a mensagem do
     * provedor, ja saneada pelo adaptador.
     */
    @ExceptionHandler(ProvedorRecusouException.class)
    public ResponseEntity<ProblemDetail> provedorRecusou(ProvedorRecusouException ex) {
        return ResponseEntity.badRequest()
                .body(problema(HttpStatus.BAD_REQUEST, ex.getMessage(), "PROVEDOR_RECUSOU"));
    }

    @ExceptionHandler(ProvedorIndisponivelException.class)
    public ResponseEntity<ProblemDetail> provedorIndisponivel(ProvedorIndisponivelException ex) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(problema(HttpStatus.SERVICE_UNAVAILABLE,
                        "O servico de pagamento nao respondeu. Tente novamente em instantes",
                        "PROVEDOR_INDISPONIVEL"));
    }

    @ExceptionHandler(ProvedorNaoConfiguradoException.class)
    public ResponseEntity<ProblemDetail> provedorNaoConfigurado(ProvedorNaoConfiguradoException ex) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(problema(HttpStatus.SERVICE_UNAVAILABLE,
                        "Pagamentos indisponiveis no momento. Fale com o suporte do Eduka",
                        "PROVEDOR_NAO_CONFIGURADO"));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ProblemDetail> violacaoDeIntegridade(DataIntegrityViolationException ex) {
        return ResponseEntity.badRequest()
                .body(problema(HttpStatus.BAD_REQUEST, "dados inválidos"));
    }

    /**
     * Ultimo recurso. Excecoes do proprio Spring MVC que ja sabem seu status
     * (404 de rota inexistente, 405 de metodo errado, 415 de content-type...)
     * passam com o status delas. Qualquer outra vira 500: o detalhe fica no
     * log e o cliente recebe so o status, para nao expor classe, mensagem ou
     * stack de excecao interna.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> erroInterno(Exception ex) {
        if (ex instanceof ErrorResponse comStatus) {
            return ResponseEntity.status(comStatus.getStatusCode()).body(comStatus.getBody());
        }
        log.error("Erro nao tratado", ex);
        return ResponseEntity.internalServerError()
                .body(problema(HttpStatus.INTERNAL_SERVER_ERROR, "Erro interno"));
    }

    private static ProblemDetail problema(HttpStatus status, String detalhe) {
        ProblemDetail problem = ProblemDetail.forStatus(status);
        problem.setTitle(status.getReasonPhrase());
        problem.setDetail(detalhe);
        return problem;
    }

    private static ProblemDetail problema(HttpStatus status, String detalhe, String codigo) {
        ProblemDetail problem = problema(status, detalhe);
        problem.setProperty("codigo", codigo);
        return problem;
    }
}
