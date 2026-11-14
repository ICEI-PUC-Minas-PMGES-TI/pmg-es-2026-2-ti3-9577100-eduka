package br.pucminas.eduka.backend.service.privacidade;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Os dados do aluno que a escola entrega a pedido (LGPD, art. 18 e 19), ja
 * decifrados. So leitura: quem monta e o {@link DadosPessoaisAlunoService},
 * quem desenha e o {@link DadosPessoaisPdf}.
 *
 * <p>
 * O boletim ainda nao entra: ele sera o fechado pela secretaria, que nao
 * existe ainda. Ate la a secao sai vazia, com aviso.
 * </p>
 */
public record DadosPessoaisAluno(
        Escola escola,
        Instant geradoEm,
        String solicitadoPor,
        Pessoal pessoal,
        List<Responsavel> responsaveis,
        List<FrequenciaDisciplina> frequencias,
        List<Mensalidade> mensalidades) {

    /** Quem trata o dado: a escola. */
    public record Escola(String nome, String documento, String email, String telefone, String endereco) {
    }

    public record Pessoal(String nome, String cpf, LocalDate dataNascimento, String email, String telefone,
            String genero, String endereco, String matricula, Instant cadastradoEm) {
    }

    public record Responsavel(String nome, String parentesco, String cpf, String email, String telefone,
            boolean financeiro) {
    }

    public record FrequenciaDisciplina(String turma, String disciplina, long aulas, long faltas) {
    }

    public record Mensalidade(String descricao, LocalDate vencimento, BigDecimal valor, String situacao,
            LocalDate pagaEm) {
    }
}
