package br.pucminas.eduka.backend.dto;

import java.util.UUID;

import br.pucminas.eduka.backend.domain.PerfilUsuario;
import br.pucminas.eduka.backend.domain.Pessoa;
import br.pucminas.eduka.backend.domain.Usuario;

/**
 * Alguem da equipe da escola (SECRETARIA ou FINANCEIRO).
 *
 * @param aguardandoPrimeiroAcesso ainda nao trocou a senha provisoria: o
 *                                 MASTER pode reenviar o e-mail de acesso
 */
public record MembroEquipeResponse(
        UUID id,
        PerfilUsuario perfil,
        String nome,
        String cpf,
        String email,
        String telefone,
        String login,
        boolean ativo,
        boolean aguardandoPrimeiroAcesso) {

    /** Listagem: sem CPF, que so aparece no detalhe de uma pessoa especifica. */
    public static MembroEquipeResponse deResumo(Usuario usuario, Pessoa pessoa) {
        return new MembroEquipeResponse(usuario.getId(), usuario.getPerfil(), pessoa.getNome(), null,
                pessoa.getEmail(), pessoa.getTelefone(), usuario.getLogin(), usuario.isAtivo(),
                usuario.isSenhaProvisoria());
    }

    public static MembroEquipeResponse de(Usuario usuario, Pessoa pessoa) {
        return new MembroEquipeResponse(usuario.getId(), usuario.getPerfil(), pessoa.getNome(), pessoa.getCpf(),
                pessoa.getEmail(), pessoa.getTelefone(), usuario.getLogin(), usuario.isAtivo(),
                usuario.isSenhaProvisoria());
    }
}
