package br.pucminas.eduka.backend.dto;

import java.util.UUID;

import br.pucminas.eduka.backend.model.PerfilUsuario;

public record CadastroUsuarioRequest(
        UUID escolaId,
        UUID pessoaId,
        String login,
        String senha,
        PerfilUsuario perfil) {

    /** Impede que a senha apareca em log ou mensagem de erro. */
    @Override
    public String toString() {
        return "CadastroUsuarioRequest[escolaId=%s, pessoaId=%s, login=%s, perfil=%s]"
                .formatted(escolaId, pessoaId, login, perfil);
    }
}
