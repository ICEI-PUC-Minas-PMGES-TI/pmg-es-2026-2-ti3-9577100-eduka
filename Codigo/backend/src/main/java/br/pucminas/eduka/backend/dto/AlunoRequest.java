package br.pucminas.eduka.backend.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * Dados de cadastro de aluno (e, opcionalmente, seus responsaveis). Nao
 * existe campo {@code escolaId} de proposito: a escola vem sempre do token
 * do usuario autenticado.
 */
public record AlunoRequest(
        String nome,
        String cpf,
        LocalDate dataNascimento,
        EnderecoRequest endereco,
        String matricula,
        String genero,
        List<ResponsavelRequest> responsaveis) {
}
