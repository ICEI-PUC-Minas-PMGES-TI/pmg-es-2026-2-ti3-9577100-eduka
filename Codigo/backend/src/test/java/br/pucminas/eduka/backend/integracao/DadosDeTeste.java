package br.pucminas.eduka.backend.integracao;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;

import br.pucminas.eduka.backend.domain.EntidadeAuditada;
import br.pucminas.eduka.backend.domain.OperacaoAuditoria;
import br.pucminas.eduka.backend.domain.SituacaoDespesa;
import br.pucminas.eduka.backend.domain.SituacaoProvedor;
import br.pucminas.eduka.backend.domain.StatusCobranca;
import br.pucminas.eduka.backend.domain.StatusEventoProvedor;
import br.pucminas.eduka.backend.domain.StatusMatricula;
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

    /** Aluno com login de ALUNO e CPF, como precisa quem paga pelo portal. */
    public AlunoComAcesso alunoComAcesso(UUID escolaId, String nome, String cpf) {
        UUID pessoaId = jdbc.queryForObject("INSERT INTO PESSOA (ESCOLA_ID, NOME, CPF) VALUES (?, ?, ?) "
                + "RETURNING ID_PESSOA", UUID.class, escolaId, pii.cifrar(nome), pii.cifrar(cpf));
        UUID usuarioId = jdbc.queryForObject("INSERT INTO USUARIO (ESCOLA_ID, PESSOA_ID, LOGIN, SENHA_HASH, PERFIL) "
                + "VALUES (?, ?, ?, '{noop}x', 'ALUNO') RETURNING ID_USUARIO", UUID.class,
                escolaId, pessoaId, unico() + "@teste.com");
        UUID alunoId = jdbc.queryForObject("INSERT INTO alunos (escola_id, numero_matricula, pessoa_id, usuario_id) "
                + "VALUES (?, ?, ?, ?) RETURNING id_aluno", UUID.class, escolaId, unico(), pessoaId, usuarioId);
        return new AlunoComAcesso(alunoId, usuarioId);
    }

    public record AlunoComAcesso(UUID alunoId, UUID usuarioId) {
    }

    private UUID pessoa(UUID escolaId, String nome) {
        return jdbc.queryForObject("INSERT INTO PESSOA (ESCOLA_ID, NOME) VALUES (?, ?) RETURNING ID_PESSOA",
                UUID.class, escolaId, pii.cifrar(nome));
    }

    /**
     * Conta de recebimento APROVADA no Asaas: a escola pode receber.
     *
     * @return o id da conta filha no Asaas, que vem nos avisos do webhook
     */
    public String contaAprovada(UUID escolaId) {
        String idContaExterna = "acc_" + unico();
        jdbc.update("INSERT INTO CONTA_RECEBIMENTO_ESCOLA (ESCOLA_ID, STATUS, RAZAO_SOCIAL, EMAIL_FINANCEIRO, "
                + "TELEFONE_CELULAR, TIPO_EMPRESA, FATURAMENTO_MENSAL, LOGRADOURO, NUMERO, BAIRRO, CEP, BANCO_CODIGO, "
                + "CONTA_FINAL, ID_CONTA_EXTERNA, WALLET_ID, CREDENCIAL_CIFRADA, SOLICITADA_EM, APROVADA_EM) "
                + "VALUES (?, 'APROVADA', 'Escola Teste Ltda', 'financeiro@teste.com', '31999990000', 'LIMITED', "
                + "50000, 'Rua A', '10', 'Centro', '30140000', '001', '1234', ?, ?, ?, CURRENT_TIMESTAMP, "
                + "CURRENT_TIMESTAMP)", escolaId, idContaExterna, "wal_" + unico(), pii.cifrar("chave-da-conta-filha"));
        return idContaExterna;
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

    /** Turma ativa da manha, com periodo letivo (em curso) e curso so dela. */
    public long turma(UUID escolaId, String nome, int vagas) {
        Long periodo = jdbc.queryForObject("INSERT INTO periodo_letivo (escola_id, nome, data_inicio, data_fim) "
                + "VALUES (?, ?, CURRENT_DATE - 30, CURRENT_DATE + 150) RETURNING id", Long.class, escolaId,
                "Periodo " + unico());
        Long curso = jdbc.queryForObject("INSERT INTO curso (escola_id, nome) VALUES (?, ?) RETURNING id", Long.class,
                escolaId, "Curso " + unico());
        return jdbc.queryForObject("INSERT INTO turma (escola_id, periodo_letivo_id, curso_id, nome, vagas, turno) "
                + "VALUES (?, ?, ?, ?, ?, 'MATUTINO') RETURNING id", Long.class, escolaId, periodo, curso, nome, vagas);
    }

    public UUID professor(UUID escolaId, String nome, LocalDate admissao, boolean ativo) {
        return jdbc.queryForObject("INSERT INTO PROFESSOR (ESCOLA_ID, PESSOA_ID, DATA_ADMISSAO, ATIVO) "
                + "VALUES (?, ?, ?, ?) RETURNING ID_PROFESSOR", UUID.class, escolaId, pessoa(escolaId, nome), admissao,
                ativo);
    }

    /**
     * Fatura da plataforma da competencia {@code yyyy-MM}, lancada por
     * {@code criadaPor}, vencendo no dia 10. A PAGA foi paga no vencimento,
     * pelo valor cheio.
     */
    public UUID fatura(UUID escolaId, UUID criadaPor, String competencia, BigDecimal valor, StatusCobranca status) {
        LocalDate vencimento = LocalDate.parse(competencia + "-10");
        boolean paga = status == StatusCobranca.PAGA;
        return jdbc.queryForObject("INSERT INTO FATURA_PLATAFORMA (ESCOLA_ID, COMPETENCIA, DESCRICAO, VALOR, "
                + "DATA_VENCIMENTO, STATUS, CRIADA_POR, VALOR_PAGO, DATA_PAGAMENTO) "
                + "VALUES (?, ?, 'Mensalidade Eduka', ?, ?, ?, ?, ?, ?) RETURNING ID_FATURA", UUID.class, escolaId,
                LocalDate.parse(competencia + "-01"), valor, vencimento, status.name(), criadaPor,
                paga ? valor : null, paga ? vencimento : null);
    }

    public void matricula(UUID escolaId, UUID alunoId, long turmaId, StatusMatricula status, LocalDate data) {
        jdbc.update("INSERT INTO MATRICULA_TURMA (ESCOLA_ID, ALUNO_ID, TURMA_ID, STATUS, DATA_MATRICULA) "
                + "VALUES (?, ?, ?, ?, ?)", escolaId, alunoId, turmaId, status.name(), data);
    }

    /**
     * Prova que vale {@code valor} na turma, numa disciplina nova posta na
     * grade dela, aplicada por um professor novo.
     */
    public UUID avaliacao(UUID escolaId, long turmaId, BigDecimal valor) {
        Long disciplina = jdbc.queryForObject("INSERT INTO disciplina (escola_id, nome, carga_horaria) "
                + "VALUES (?, ?, 60) RETURNING id", Long.class, escolaId, "Disciplina " + unico());
        jdbc.update("INSERT INTO turma_disciplina (turma_id, disciplina_id) VALUES (?, ?)", turmaId, disciplina);
        UUID professor = jdbc.queryForObject("INSERT INTO PROFESSOR (ESCOLA_ID, PESSOA_ID) VALUES (?, ?) "
                + "RETURNING ID_PROFESSOR", UUID.class, escolaId, pessoa(escolaId, "Professor " + unico()));
        return jdbc.queryForObject("INSERT INTO AVALIACAO (ESCOLA_ID, TURMA_ID, DISCIPLINA_ID, PROFESSOR_ID, TIPO, "
                + "TITULO, DATA_AVALIACAO, VALOR) VALUES (?, ?, ?, ?, 'PROVA', 'Prova', CURRENT_DATE, ?) "
                + "RETURNING ID_AVALIACAO", UUID.class, escolaId, turmaId, disciplina, professor, valor);
    }

    public void nota(UUID escolaId, UUID avaliacaoId, UUID alunoId, BigDecimal valor) {
        jdbc.update("INSERT INTO NOTA (ESCOLA_ID, AVALIACAO_ID, ALUNO_ID, VALOR) VALUES (?, ?, ?, ?)", escolaId,
                avaliacaoId, alunoId, valor);
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

    /** Situacao da cobranca no provedor (NAO_EMITIDA, EMITINDO, EMITIDA, FALHA_EMISSAO). */
    public SituacaoProvedor situacaoNoProvedor(UUID cobrancaId) {
        return SituacaoProvedor.valueOf(jdbc.queryForObject(
                "SELECT SITUACAO_PROVEDOR FROM COBRANCA WHERE ID_COBRANCA = ?", String.class, cobrancaId));
    }

    public String ultimoErroProvedor(UUID cobrancaId) {
        return jdbc.queryForObject("SELECT ULTIMO_ERRO_PROVEDOR FROM COBRANCA WHERE ID_COBRANCA = ?", String.class,
                cobrancaId);
    }

    public int pagamentosDaCobranca(UUID cobrancaId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM PAGAMENTO_COBRANCA WHERE COBRANCA_ID = ?", Integer.class,
                cobrancaId);
    }

    /** Quantas vezes o aviso foi gravado (0 ou 1, pela chave unica). */
    public int eventosGravados(String idEventoExterno) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM EVENTO_PROVEDOR WHERE ID_EVENTO_EXTERNO = ?", Integer.class,
                idEventoExterno);
    }

    public StatusEventoProvedor statusDoEvento(String idEventoExterno) {
        return StatusEventoProvedor.valueOf(jdbc.queryForObject(
                "SELECT STATUS FROM EVENTO_PROVEDOR WHERE ID_EVENTO_EXTERNO = ?", String.class, idEventoExterno));
    }

    /** Operacoes do log de auditoria sobre o registro, na ordem em que aconteceram. */
    public List<OperacaoAuditoria> operacoesNoLog(UUID registroId) {
        return jdbc.queryForList("SELECT OPERACAO FROM LOG_AUDITORIA WHERE REGISTRO_ID = ? ORDER BY DATA_HORA",
                String.class, registroId).stream().map(OperacaoAuditoria::valueOf).toList();
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
