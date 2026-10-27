package br.pucminas.eduka.backend.integracao;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;

import br.pucminas.eduka.backend.domain.StatusCobranca;
import br.pucminas.eduka.backend.pagamento.tipos.FormaPagamento;
import br.pucminas.eduka.backend.security.PiiCryptoService;

/**
 * Grava no banco o minimo para cada cenario, direto por SQL: sem passar pelos
 * services (que exigem usuario autenticado e disparam e-mail) nem pelo
 * {@code @TenantId}. Cada chamada cria registros novos, com ids e numeros
 * unicos, entao testes que dividem o banco nao se atrapalham.
 *
 * <p>
 * Os dados pessoais sao cifrados com a mesma chave da aplicacao: a pessoa
 * criada aqui e lida normalmente pelos repositorios.
 * </p>
 */
public class DadosDeTeste {

    private final JdbcTemplate jdbc;
    private final PiiCryptoService pii;

    DadosDeTeste(JdbcTemplate jdbc, PiiCryptoService pii) {
        this.jdbc = jdbc;
        this.pii = pii;
    }

    public UUID escola() {
        return jdbc.queryForObject("INSERT INTO ESCOLA (NOME) VALUES (?) RETURNING ID_ESCOLA", UUID.class,
                "Escola " + unico());
    }

    public UUID aluno(UUID escolaId) {
        UUID pessoaId = jdbc.queryForObject(
                "INSERT INTO PESSOA (ESCOLA_ID, NOME) VALUES (?, ?) RETURNING ID_PESSOA", UUID.class,
                escolaId, pii.cifrar("Aluno " + unico()));
        return jdbc.queryForObject(
                "INSERT INTO alunos (escola_id, numero_matricula, pessoa_id) VALUES (?, ?, ?) RETURNING id_aluno",
                UUID.class, escolaId, unico(), pessoaId);
    }

    /** Cobranca de R$ 100,00 ainda nao emitida no provedor. */
    public UUID cobranca(UUID escolaId, UUID alunoId, StatusCobranca status, LocalDate vencimento) {
        return jdbc.queryForObject("INSERT INTO COBRANCA (ESCOLA_ID, ALUNO_ID, DESCRICAO, VALOR, DATA_VENCIMENTO, "
                + "STATUS) VALUES (?, ?, 'Mensalidade', 100.00, ?, ?) RETURNING ID_COBRANCA", UUID.class,
                escolaId, alunoId, vencimento, status.name());
    }

    /** Cobranca pendente ja emitida no provedor, como fica depois do Pagar. */
    public UUID cobrancaEmitida(UUID escolaId, UUID alunoId, String idCobrancaExterna, FormaPagamento forma) {
        UUID id = cobranca(escolaId, alunoId, StatusCobranca.PENDENTE, LocalDate.now().plusDays(10));
        jdbc.update("UPDATE COBRANCA SET SITUACAO_PROVEDOR = 'EMITIDA', ID_COBRANCA_EXTERNA = ?, "
                + "FORMA_PAGAMENTO = ?, EMITIDA_EM = CURRENT_TIMESTAMP WHERE ID_COBRANCA = ?",
                idCobrancaExterna, forma.name(), id);
        return id;
    }

    /** Cobranca pendente cujo Pagar ficou sem resposta do provedor, desde {@code iniciadaEm}. */
    public UUID cobrancaEmitindo(UUID escolaId, UUID alunoId, Instant iniciadaEm) {
        UUID id = cobranca(escolaId, alunoId, StatusCobranca.PENDENTE, LocalDate.now().plusDays(10));
        jdbc.update("UPDATE COBRANCA SET SITUACAO_PROVEDOR = 'EMITINDO', FORMA_PAGAMENTO = 'PIX', "
                + "EMISSAO_INICIADA_EM = ? WHERE ID_COBRANCA = ?", Timestamp.from(iniciadaEm), id);
        return id;
    }

    /** Conta de recebimento ja criada no provedor, com a credencial {@code credencial} cifrada. */
    public void contaAprovada(UUID escolaId, String credencial) {
        jdbc.update("INSERT INTO CONTA_RECEBIMENTO_ESCOLA (ESCOLA_ID, STATUS, RAZAO_SOCIAL, EMAIL_FINANCEIRO, "
                + "TELEFONE_CELULAR, TIPO_EMPRESA, FATURAMENTO_MENSAL, LOGRADOURO, NUMERO, BAIRRO, CEP, "
                + "BANCO_CODIGO, CONTA_FINAL, ID_CONTA_EXTERNA, CREDENCIAL_CIFRADA, SOLICITADA_EM) "
                + "VALUES (?, 'APROVADA', 'Escola Teste LTDA', 'financeiro@escola.com', '31999999999', 'LIMITED', "
                + "10000.00, 'Rua A', '1', 'Centro', '30000000', '001', '1234', ?, ?, CURRENT_TIMESTAMP)",
                escolaId, "acc_" + unico(), pii.cifrar(credencial));
    }

    /** Situacao gravada, lida direto da tabela (sem o EM_ATRASO calculado na leitura). */
    public StatusCobranca statusGravado(UUID cobrancaId) {
        return StatusCobranca.valueOf(jdbc.queryForObject(
                "SELECT STATUS FROM COBRANCA WHERE ID_COBRANCA = ?", String.class, cobrancaId));
    }

    public BigDecimal valor(UUID cobrancaId) {
        return jdbc.queryForObject("SELECT VALOR FROM COBRANCA WHERE ID_COBRANCA = ?", BigDecimal.class,
                cobrancaId);
    }

    /** Texto curto e unico, para campos com UNIQUE (numero de matricula, ids externos). */
    public static String unico() {
        return UUID.randomUUID().toString().substring(0, 13);
    }
}
