package br.pucminas.eduka.backend.dto;

import java.util.UUID;

import br.pucminas.eduka.backend.domain.Pessoa;
import br.pucminas.eduka.backend.domain.Usuario;

public record SecretariaResponse(
        UUID id,
        UUID escolaId,
        String nome,
        String cpf,
        String email,
        String telefone,
        String login,
        boolean ativo) {

    /** Listagem: sem CPF, que so aparece no detalhe de uma secretaria especifica. */
    public static SecretariaResponse deResumo(Usuario usuario, Pessoa pessoa) {
        return new SecretariaResponse(usuario.getId(), usuario.getEscolaId(), pessoa.getNome(), null,
                pessoa.getEmail(), pessoa.getTelefone(), usuario.getLogin(), usuario.isAtivo());
    }

    public static SecretariaResponse de(Usuario usuario, Pessoa pessoa) {
        return new SecretariaResponse(usuario.getId(), usuario.getEscolaId(), pessoa.getNome(), pessoa.getCpf(),
                pessoa.getEmail(), pessoa.getTelefone(), usuario.getLogin(), usuario.isAtivo());
    }
}
