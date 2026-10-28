package br.pucminas.eduka.backend.pagamento.tipos;

/**
 * Chave de API de uma conta no provedor. So o roteador decifra e monta esta
 * credencial; ela nunca e gravada em texto, logada nem devolvida na API.
 */
public record CredencialProvedor(String valor) {

    public boolean preenchida() {
        return valor != null && !valor.isBlank();
    }

    /** Impede que a chave apareca em log. */
    @Override
    public String toString() {
        return "CredencialProvedor[***]";
    }
}
