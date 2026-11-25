package br.pucminas.eduka.backend.dto.escola;

import br.pucminas.eduka.backend.domain.comum.Endereco;
import br.pucminas.eduka.backend.domain.escola.Escola;

/**
 * Canais de atendimento da escola, mostrados na Ajuda de quem trabalha ou
 * estuda nela. So o que a escola publica para contato: nada de CNPJ, plano ou
 * situacao da assinatura. Campos nulos quando a escola nao cadastrou.
 */
public record ContatoEscolaResponse(
        String nome,
        String email,
        String telefone,
        String cidade,
        String estado) {

    public static ContatoEscolaResponse from(Escola escola) {
        Endereco endereco = escola.getEndereco();
        return new ContatoEscolaResponse(
                escola.getNome(),
                escola.getEmail(),
                escola.getTelefone(),
                endereco == null ? null : endereco.cidade(),
                endereco == null ? null : endereco.estado());
    }
}
