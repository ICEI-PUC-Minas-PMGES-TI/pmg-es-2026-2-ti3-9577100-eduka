package br.pucminas.eduka.backend.pagamento;

import java.util.function.Consumer;
import java.util.function.Function;

import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Unica porta para o {@link PaymentProvider}, de uso exclusivo do roteador
 * (package-private). Recusa a chamada se houver transacao de banco aberta: a
 * chamada ao provedor pode levar ate 10 s e nao pode segurar conexao nem lock.
 * O padrao e reservar numa transacao curta, chamar o provedor sem transacao e
 * gravar o resultado em outra transacao curta.
 */
@Component
class ChamadorProvedor {

    private final PaymentProvider provider;

    ChamadorProvedor(PaymentProvider provider) {
        this.provider = provider;
    }

    <T> T chamar(Function<PaymentProvider, T> chamada) {
        exigirSemTransacao();
        return chamada.apply(provider);
    }

    void executar(Consumer<PaymentProvider> chamada) {
        exigirSemTransacao();
        chamada.accept(provider);
    }

    private static void exigirSemTransacao() {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("Chamada ao provedor de pagamento com transacao de banco aberta");
        }
    }
}
