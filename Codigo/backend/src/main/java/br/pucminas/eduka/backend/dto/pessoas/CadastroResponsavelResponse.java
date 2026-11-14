package br.pucminas.eduka.backend.dto.pessoas;

import java.util.List;
import java.util.UUID;

import br.pucminas.eduka.backend.domain.pessoas.Pessoa;
import br.pucminas.eduka.backend.domain.pessoas.Responsavel;
import br.pucminas.eduka.backend.dto.comum.EnderecoResponse;

/**
 * Responsavel em {@code /responsaveis}, com os alunos a que esta ligado.
 *
 * @param pessoaId id da pessoa; e o {@code responsavelId} das cobrancas
 */
public record CadastroResponsavelResponse(
        UUID id,
        UUID pessoaId,
        UUID escolaId,
        String nome,
        String cpf,
        String email,
        String telefone,
        EnderecoResponse endereco,
        List<AlunoDoResponsavelResponse> alunos) {

    /** Aluno ligado ao responsavel, com o vinculo. */
    public record AlunoDoResponsavelResponse(
            UUID alunoId,
            String nome,
            String matricula,
            String parentesco,
            boolean financeiro) {
    }

    /** Listagem: sem CPF nem endereco, que so aparecem no detalhe. */
    public static CadastroResponsavelResponse deResumo(Responsavel responsavel, Pessoa pessoa,
            List<AlunoDoResponsavelResponse> alunos) {
        return new CadastroResponsavelResponse(responsavel.getId(), pessoa.getId(), responsavel.getEscolaId(),
                pessoa.getNome(), null, pessoa.getEmail(), pessoa.getTelefone(), null, alunos);
    }

    public static CadastroResponsavelResponse de(Responsavel responsavel, Pessoa pessoa,
            List<AlunoDoResponsavelResponse> alunos) {
        return new CadastroResponsavelResponse(responsavel.getId(), pessoa.getId(), responsavel.getEscolaId(),
                pessoa.getNome(), pessoa.getCpf(), pessoa.getEmail(), pessoa.getTelefone(), enderecoDe(pessoa),
                alunos);
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
