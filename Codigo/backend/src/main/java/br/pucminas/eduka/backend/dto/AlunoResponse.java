package br.pucminas.eduka.backend.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import br.pucminas.eduka.backend.domain.Aluno;
import br.pucminas.eduka.backend.domain.Pessoa;

public record AlunoResponse(
        UUID id,
        UUID escolaId,
        String nome,
        String cpf,
        String email,
        LocalDate dataNascimento,
        EnderecoResponse endereco,
        String matricula,
        String genero,
        List<ResponsavelResponse> responsaveis,
        Situacao situacao,
        Long turmaId,
        String turmaNome) {

    /**
     * Onde o aluno esta hoje, pelas matriculas: com uma ATIVA, so com uma
     * TRANCADA, ou sem nenhuma das duas. Vem so na listagem.
     */
    public enum Situacao {
        MATRICULADO, TRANCADO, SEM_TURMA
    }

    /**
     * Listagem (enturmacao): so o necessario para achar/identificar o aluno,
     * com a situacao e a turma da matricula ATIVA (ou da TRANCADA).
     */
    public static AlunoResponse deResumo(Aluno aluno, Pessoa pessoa, Situacao situacao, Long turmaId,
            String turmaNome) {
        return new AlunoResponse(aluno.getId(), aluno.getEscolaId(), pessoa.getNome(), null, null, null, null,
                aluno.getMatricula(), aluno.getGenero(), List.of(), situacao, turmaId, turmaNome);
    }

    /** Detalhe de um aluno especifico: todo dado pessoal, ja decifrado. Sem a situacao. */
    public static AlunoResponse de(Aluno aluno, Pessoa pessoa, List<ResponsavelResponse> responsaveis) {
        return new AlunoResponse(aluno.getId(), aluno.getEscolaId(), pessoa.getNome(), pessoa.getCpf(),
                pessoa.getEmail(),
                pessoa.getDataNascimento() == null ? null : LocalDate.parse(pessoa.getDataNascimento()),
                enderecoDe(pessoa), aluno.getMatricula(), aluno.getGenero(), responsaveis, null, null, null);
    }

    private static EnderecoResponse enderecoDe(Pessoa pessoa) {
        if (pessoa.getLogradouro() == null && pessoa.getNumero() == null && pessoa.getBairro() == null
                && pessoa.getCidade() == null && pessoa.getEstado() == null && pessoa.getCep() == null) {
            return null;
        }
        return new EnderecoResponse(pessoa.getLogradouro(), pessoa.getNumero(), pessoa.getBairro(),
                pessoa.getCidade(), pessoa.getEstado(), pessoa.getCep());
    }
}
