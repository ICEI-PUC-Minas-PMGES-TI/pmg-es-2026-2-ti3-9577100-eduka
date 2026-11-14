package br.pucminas.eduka.backend.exception.financeiro;

/**
 * A conta de recebimento nao pode ser ativada de novo: a criacao ja esta em
 * andamento ({@code ATIVACAO_EM_ANDAMENTO}) ou a conta ja existe no provedor
 * ({@code CONTA_JA_CRIADA}).
 */
public class ContaRecebimentoNaoAtivavelException extends RuntimeException {

    public static final String EM_ANDAMENTO = "ATIVACAO_EM_ANDAMENTO";
    public static final String JA_CRIADA = "CONTA_JA_CRIADA";

    private final String codigo;

    private ContaRecebimentoNaoAtivavelException(String codigo, String mensagem) {
        super(mensagem);
        this.codigo = codigo;
    }

    public static ContaRecebimentoNaoAtivavelException emAndamento() {
        return new ContaRecebimentoNaoAtivavelException(EM_ANDAMENTO,
                "A conta de recebimento esta sendo criada. Tente de novo em alguns segundos");
    }

    public static ContaRecebimentoNaoAtivavelException jaCriada() {
        return new ContaRecebimentoNaoAtivavelException(JA_CRIADA,
                "A conta de recebimento da escola ja foi criada. Use Sincronizar para ver a situacao");
    }

    public String getCodigo() {
        return codigo;
    }
}
