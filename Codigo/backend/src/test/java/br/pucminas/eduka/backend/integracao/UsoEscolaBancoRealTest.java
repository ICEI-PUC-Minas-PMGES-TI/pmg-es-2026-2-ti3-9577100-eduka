package br.pucminas.eduka.backend.integracao;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import br.pucminas.eduka.backend.domain.EntidadeAuditada;
import br.pucminas.eduka.backend.domain.OperacaoAuditoria;
import br.pucminas.eduka.backend.domain.StatusCobranca;
import br.pucminas.eduka.backend.dto.UsoEscolaResponse;
import br.pucminas.eduka.backend.repository.UsuarioRepository;
import br.pucminas.eduka.backend.service.UsoEscolaService;

/**
 * O uso de cada escola no Postgres: contagens por escola, o mes no fuso da
 * escola e a ultima atividade entre login e log de auditoria.
 */
class UsoEscolaBancoRealTest extends BancoRealTest {

    private static final ZoneId FUSO_ESCOLA = ZoneId.of("America/Sao_Paulo");

    @Autowired
    private UsoEscolaService usoEscolaService;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private JdbcTemplate jdbc;

    private final LocalDate hoje = LocalDate.now(FUSO_ESCOLA);
    private final Instant inicioDoMes = YearMonth.from(hoje).atDay(1).atStartOfDay(FUSO_ESCOLA).toInstant();

    private Map<UUID, UsoEscolaResponse> usoPorEscola() {
        autenticarComo("SUPER_ADMIN", null);
        return usoEscolaService.listar().stream()
                .collect(Collectors.toMap(UsoEscolaResponse::escolaId, Function.identity()));
    }

    private void criadaEm(UUID cobrancaId, Instant criadoEm) {
        jdbc.update("UPDATE COBRANCA SET CRIADO_EM = ? WHERE ID_COBRANCA = ?", Timestamp.from(criadoEm), cobrancaId);
    }

    @Test
    void contaAlunosUsuariosAtivosECobrancasDoMesDeCadaEscola() {
        UUID escola = dados.escola();
        UUID ana = dados.aluno(escola, "Ana Souza");
        dados.aluno(escola, "Bruno Lima");
        dados.usuario(escola, "FINANCEIRO", "Fernanda");
        UUID inativo = dados.usuario(escola, "SECRETARIA", "Sergio");
        jdbc.update("UPDATE USUARIO SET ATIVO = FALSE WHERE ID_USUARIO = ?", inativo);

        // Duas lancadas neste mes (uma ja cancelada) e uma do mes passado.
        dados.cobranca(escola, ana, StatusCobranca.PENDENTE, hoje.plusDays(10));
        criadaEm(dados.cobranca(escola, ana, StatusCobranca.CANCELADA, hoje), inicioDoMes.plus(Duration.ofHours(1)));
        criadaEm(dados.cobranca(escola, ana, StatusCobranca.PAGA, hoje.minusMonths(1)),
                inicioDoMes.minus(Duration.ofHours(1)));

        UUID outra = dados.escola();
        dados.aluno(outra);

        Map<UUID, UsoEscolaResponse> uso = usoPorEscola();

        UsoEscolaResponse daEscola = uso.get(escola);
        assertThat(daEscola.alunos()).isEqualTo(2);
        assertThat(daEscola.usuariosAtivos()).isEqualTo(1);
        assertThat(daEscola.cobrancasNoMes()).isEqualTo(2);
        assertThat(daEscola.mes()).isEqualTo(YearMonth.from(hoje));

        UsoEscolaResponse daOutra = uso.get(outra);
        assertThat(daOutra.alunos()).isEqualTo(1);
        assertThat(daOutra.usuariosAtivos()).isZero();
        assertThat(daOutra.cobrancasNoMes()).isZero();
        assertThat(daOutra.ultimaAtividade()).isNull();
    }

    @Test
    void ultimaAtividadeEOMaisRecenteEntreLoginEAcaoDeUsuario() {
        Instant agora = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        UUID escola = dados.escola();
        UUID fernanda = dados.usuario(escola, "FINANCEIRO", "Fernanda");
        usuarioRepository.registrarAcesso(fernanda, agora.minus(Duration.ofDays(3)));
        dados.acaoNoLog(escola, fernanda, OperacaoAuditoria.CRIAR, EntidadeAuditada.COBRANCA, UUID.randomUUID(),
                agora.minus(Duration.ofDays(1)));

        assertThat(usoPorEscola().get(escola).ultimaAtividade()).isEqualTo(agora.minus(Duration.ofDays(1)));

        // Login mais novo que a ultima acao passa a valer.
        usuarioRepository.registrarAcesso(fernanda, agora);
        assertThat(usoPorEscola().get(escola).ultimaAtividade()).isEqualTo(agora);
    }

    @Test
    void avisoDoProvedorNaoContaComoAtividade() {
        UUID escola = dados.escola();
        jdbc.update("INSERT INTO LOG_AUDITORIA (ESCOLA_ID, USUARIO_ID, ORIGEM, OPERACAO, ENTIDADE, REGISTRO_ID, "
                + "DATA_HORA) VALUES (?, NULL, 'PROVEDOR', 'CONFIRMAR_PAGAMENTO', 'COBRANCA', ?, ?)", escola,
                UUID.randomUUID(), Timestamp.from(Instant.now()));

        assertThat(usoPorEscola().get(escola).ultimaAtividade()).isNull();
    }
}
