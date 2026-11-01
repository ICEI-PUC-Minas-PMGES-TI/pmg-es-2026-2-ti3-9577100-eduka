package br.pucminas.eduka.backend.logging;

import java.util.UUID;

import org.slf4j.MDC;

/**
 * Campos que acompanham toda linha de log (MDC) e saem no JSON do log
 * estruturado: o id da requisicao (ou da execucao de uma rotina) e o id da
 * escola. Sao so ids: nunca ponha aqui nome, CPF, e-mail, telefone ou login.
 */
public final class ContextoLog {

    /** Chave do MDC e nome do campo no JSON. */
    public static final String REQUEST_ID = "requestId";

    /** Chave do MDC e nome do campo no JSON; ausente quando nao ha escola. */
    public static final String ESCOLA_ID = "escolaId";

    private ContextoLog() {
    }

    /** Id novo para uma requisicao sem {@code X-Request-Id} aproveitavel ou para uma execucao de rotina. */
    public static String novoId() {
        return UUID.randomUUID().toString();
    }

    public static void definirEscola(UUID escolaId) {
        if (escolaId != null) {
            MDC.put(ESCOLA_ID, escolaId.toString());
        }
    }

    public static void limparEscola() {
        MDC.remove(ESCOLA_ID);
    }

    /** Tira do MDC tudo o que esta classe pos; chamado no fim da requisicao ou da rotina, mesmo com erro. */
    public static void limpar() {
        MDC.remove(REQUEST_ID);
        MDC.remove(ESCOLA_ID);
    }
}
