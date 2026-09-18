package br.pucminas.eduka.backend.dto;

import java.util.UUID;

import br.pucminas.eduka.backend.model.PerfilUsuario;

public record CadastroUsuarioRequest(
        UUID empresaId,
        UUID pessoaId,
        String login,
        String senha,
        PerfilUsuario perfil) {

    /** Impede que a senha apareca em log ou mensagem de erro. */
    @Override
    public String toString() {
        return "CadastroUsuarioRequest[empresaId=%s, pessoaId=%s, login=%s, perfil=%s]"
                .formatted(empresaId, pessoaId, login, perfil);
    }
}
