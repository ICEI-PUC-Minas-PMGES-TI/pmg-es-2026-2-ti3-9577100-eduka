package br.pucminas.eduka.backend.dto.pessoas;

import java.time.LocalDate;
import java.util.UUID;

import br.pucminas.eduka.backend.domain.pessoas.Pessoa;
import br.pucminas.eduka.backend.domain.pessoas.Professor;
import br.pucminas.eduka.backend.domain.pessoas.VinculoProfessor;

/**
 * @param aguardandoPrimeiroAcesso ainda nao trocou a senha provisoria (ou
 *                                 nao tem login): a secretaria pode reenviar
 *                                 o e-mail de acesso
 */
public record ProfessorResponse(
        UUID id,
        UUID escolaId,
        String nome,
        String cpf,
        String email,
        String telefone,
        String formacao,
        LocalDate dataAdmissao,
        VinculoProfessor vinculo,
        boolean ativo,
        boolean aguardandoPrimeiroAcesso) {

    /** Listagem: sem CPF, que so aparece no detalhe de um professor especifico. */
    public static ProfessorResponse deResumo(Professor professor, Pessoa pessoa, boolean aguardandoPrimeiroAcesso) {
        return new ProfessorResponse(professor.getId(), professor.getEscolaId(), pessoa.getNome(), null,
                pessoa.getEmail(), pessoa.getTelefone(), professor.getFormacao(), professor.getDataAdmissao(),
                professor.getVinculo(), professor.isAtivo(), aguardandoPrimeiroAcesso);
    }

    public static ProfessorResponse de(Professor professor, Pessoa pessoa, boolean aguardandoPrimeiroAcesso) {
        return new ProfessorResponse(professor.getId(), professor.getEscolaId(), pessoa.getNome(), pessoa.getCpf(),
                pessoa.getEmail(), pessoa.getTelefone(), professor.getFormacao(), professor.getDataAdmissao(),
                professor.getVinculo(), professor.isAtivo(), aguardandoPrimeiroAcesso);
    }
}
