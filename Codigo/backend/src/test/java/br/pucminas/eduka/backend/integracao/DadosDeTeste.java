package br.pucminas.eduka.backend.integracao;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;

import br.pucminas.eduka.backend.domain.EntidadeAuditada;
import br.pucminas.eduka.backend.domain.OperacaoAuditoria;
import br.pucminas.eduka.backend.domain.SituacaoDespesa;
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
        return aluno(escolaId, "Aluno " + unico());
    }

    public UUID aluno(UUID escolaId, String nome) {
        return jdbc.queryForObject(
                "INSERT INTO alunos (escola_id, numero_matricula, pessoa_id) VALUES (?, ?, ?) RETURNING id_aluno",
                UUID.class, escolaId, unico(), pessoa(escolaId, nome));
    }

    /** Usuario ativo da escola, com a senha que nao importa nos testes. */
    public UUID usuario(UUID escolaId, String perfil, String nome) {
        return jdbc.queryForObject("INSERT INTO USUARIO (ESCOLA_ID, PESSOA_ID, LOGIN, SENHA_HASH, PERFIL) "
                + "VALUES (?, ?, ?, '{noop}x', ?) RETURNING ID_USUARIO", UUID.class,
                escolaId, pessoa(escolaId, nome), unico() + "@teste.com", perfil);
    }

    private UUID pessoa(UUID escolaId, String nome) {
        return jdbc.queryForObject("INSERT INTO PESSOA (ESCOLA_ID, NOME) VALUES (?, ?) RETURNING ID_PESSOA",
                UUID.class, escolaId, pii.cifrar(nome));
    }

    /** Cobranca de R$ 100,00 ainda nao emitida no provedor. */
    public UUID cobranca(UUID escolaId, UUID alunoId, StatusCobranca status, LocalDate vencimento) {
        return cobranca(escolaId, alunoId, status, vencimento, new BigDecimal("100.00"));
    }

    public UUID cobranca(UUID escolaId, UUID alunoId, StatusCobranca status, LocalDate vencimento,
            BigDecimal valor) {
        return jdbc.queryForObject("INSERT INTO COBRANCA (ESCOLA_ID, ALUNO_ID, DESCRICAO, VALOR, DATA_VENCIMENTO, "
                + "STATUS) VALUES (?, ?, 'Mensalidade', ?, ?, ?) RETURNING ID_COBRANCA", UUID.class,
                escolaId, alunoId, valor, vencimento, status.name());
    }

    /** Cobranca PAGA em {@code pagaEm}, como fica depois da baixa manual (sem pagamento do provedor). */
    public UUID cobrancaPaga(UUID escolaId, UUID alunoId, BigDecimal valor, LocalDate vencimento, Instant pagaEm) {
        UUID id = cobranca(escolaId, alunoId, StatusCobranca.PAGA, vencimento, valor);
        jdbc.update("UPDATE COBRANCA SET PAGA_EM = ? WHERE ID_COBRANCA = ?", Timestamp.from(pagaEm), id);
        return id;
    }

    /** Pagamento confirmado pelo provedor de uma cobranca ja PAGA, como o webhook grava. */
    public void pagamentoOnline(UUID escolaId, UUID cobrancaId, BigDecimal valorPago, FormaPagamento forma) {
        jdbc.update("INSERT INTO PAGAMENTO_COBRANCA (ESCOLA_ID, COBRANCA_ID, VALOR_PAGO, DATA_PAGAMENTO, "
                + "FORMA_PAGAMENTO, ID_TRANSACAO_EXTERNA, ORIGEM) VALUES (?, ?, ?, CURRENT_DATE, ?, ?, 'ONLINE')",
                escolaId, cobrancaId, valorPago, forma.name(), "pay_" + unico());
    }

    public UUID categoriaDespesa(UUID escolaId) {
        return jdbc.queryForObject("INSERT INTO CATEGORIA_DESPESA (ESCOLA_ID, NOME) VALUES (?, ?) "
                + "RETURNING ID_CATEGORIA_DESPESA", UUID.class, escolaId, "Categoria " + unico());
    }

    /** {@code pagaEm} so vale para a PAGA; nas outras situacoes, passe nulo. */
    public UUID despesa(UUID escolaId, UUID categoriaId, BigDecimal valor, LocalDate vencimento,
            SituacaoDespesa situacao, Instant pagaEm) {
        return jdbc.queryForObject("INSERT INTO DESPESA (ESCOLA_ID, CATEGORIA_ID, DESCRICAO, VALOR, DATA_VENCIMENTO, "
                + "SITUACAO, PAGA_EM) VALUES (?, ?, 'Conta de luz', ?, ?, ?, ?) RETURNING ID_DESPESA", UUID.class,
                escolaId, categoriaId, valor, vencimento, situacao.name(),
                pagaEm != null ? Timestamp.from(pagaEm) : null);
    }

    public void acaoNoLog(UUID escolaId, UUID usuarioId, OperacaoAuditoria operacao, EntidadeAuditada entidade,
            UUID registroId, Instant dataHora) {
        jdbc.update("INSERT INTO LOG_AUDITORIA (ESCOLA_ID, USUARIO_ID, OPERACAO, ENTIDADE, REGISTRO_ID, DATA_HORA) "
                + "VALUES (?, ?, ?, ?, ?, ?)", escolaId, usuarioId, operacao.name(), entidade.name(), registroId,
                Timestamp.from(dataHora));
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
