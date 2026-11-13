package br.pucminas.eduka.backend.service;

import java.util.Objects;
import java.util.Optional;

import br.pucminas.eduka.backend.domain.Pessoa;
import br.pucminas.eduka.backend.domain.Usuario;
import br.pucminas.eduka.backend.repository.PessoaRepository;

/**
 * Para onde vao os codigos enviados ao proprio usuario (verificacao do login
 * e recuperacao de senha).
 */
record DestinatarioEmail(String nome, String email) {

    /**
     * O e-mail do cadastro da pessoa. {@code Pessoa} nao tem filtro automatico
     * de escola, entao a escola e conferida aqui contra a do proprio usuario.
     * Sem e-mail no cadastro, serve o login quando ele e um e-mail.
     */
    static Optional<DestinatarioEmail> doUsuario(Usuario usuario, PessoaRepository pessoaRepository) {
        Pessoa pessoa = pessoaRepository.findById(usuario.getPessoaId())
                .filter(p -> Objects.equals(p.getEscolaId(), usuario.getEscolaId()))
                .orElse(null);

        String nome = pessoa != null ? pessoa.getNome() : null;
        String email = pessoa != null ? pessoa.getEmail() : null;
        if (email == null || email.isBlank()) {
            email = usuario.getLogin().contains("@") ? usuario.getLogin() : null;
        }
        if (email == null) {
            return Optional.empty();
        }
        return Optional.of(new DestinatarioEmail(nome != null ? nome : email, email.trim()));
    }

    /** Nome e e-mail sao dado pessoal: fora do log. */
    @Override
    public String toString() {
        return "DestinatarioEmail[]";
    }
}
