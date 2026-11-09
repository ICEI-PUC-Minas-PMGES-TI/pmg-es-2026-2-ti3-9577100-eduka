package br.pucminas.eduka.backend.integracao;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import br.pucminas.eduka.backend.domain.StatusCobranca;
import br.pucminas.eduka.backend.dto.SuspensaoPrevistaResponse;
import br.pucminas.eduka.backend.security.PiiCryptoService;
import br.pucminas.eduka.backend.service.EscolaService;
import br.pucminas.eduka.backend.service.SuspensaoPorAtrasoProperties;
import br.pucminas.eduka.backend.service.SuspensaoPorAtrasoService;

/**
 * Aviso e suspensao por fatura em atraso no Postgres: quais faturas entram,
 * o prazo gravado, os e-mails na fila, a escola suspensa com o motivo e o
 * ciclo que recomeca ao reativar.
 */
class SuspensaoPorAtrasoBancoRealTest extends BancoRealTest {

    private static final ZoneId FUSO_ESCOLA = ZoneId.of("America/Sao_Paulo");

    @Autowired
    private SuspensaoPorAtrasoService service;

    @Autowired
    private SuspensaoPorAtrasoProperties prazos;

    @Autowired
    private EscolaService escolaService;

    @Autowired
    private PiiCryptoService pii;

    @Autowired
    private JdbcTemplate jdbc;

    private final LocalDate hoje = LocalDate.now(FUSO_ESCOLA);

    /** Escola com um MASTER com e-mail, e uma fatura em aberto vencida ha {@code diasVencida} dias. */
    private record Cenario(UUID escola, UUID fatura) {
    }

    private Cenario cenario(int diasVencida, StatusCobranca status) {
        UUID escola = dados.escola();
        UUID master = dados.usuario(escola, "MASTER", "Marta Gestora");
        jdbc.update("UPDATE PESSOA SET EMAIL = ? WHERE ID_PESSOA = (SELECT PESSOA_ID FROM USUARIO WHERE ID_USUARIO = ?)",
                pii.cifrar(DadosDeTeste.unico() + "@escola.com"), master);
        UUID fatura = dados.fatura(escola, master, hoje.minusMonths(1).toString().substring(0, 7),
                new BigDecimal("690.00"), status);
        jdbc.update("UPDATE FATURA_PLATAFORMA SET DATA_VENCIMENTO = ? WHERE ID_FATURA = ?",
                hoje.minusDays(diasVencida), fatura);
        return new Cenario(escola, fatura);
    }

    private int notificacoes(String tipo, UUID fatura) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM NOTIFICACAO WHERE TIPO = ? AND REFERENCIA_ID = ?",
                Integer.class, tipo, fatura);
    }

    private LocalDate prevista(UUID fatura) {
        return jdbc.queryForObject("SELECT SUSPENSAO_PREVISTA_PARA FROM FATURA_PLATAFORMA WHERE ID_FATURA = ?",
                LocalDate.class, fatura);
    }

    private boolean ativa(UUID escola) {
        return jdbc.queryForObject("SELECT ATIVO FROM ESCOLA WHERE ID_ESCOLA = ?", Boolean.class, escola);
    }

    private String motivo(UUID escola) {
        return jdbc.queryForObject("SELECT MOTIVO_SUSPENSAO FROM ESCOLA WHERE ID_ESCOLA = ?", String.class, escola);
    }

    @Test
    void faturaQuePassouDaToleranciaAvisaOMasterUmaVezSo() {
        Cenario passou = cenario(prazos.avisoAposDias(), StatusCobranca.EM_ATRASO);
        Cenario aindaNao = cenario(prazos.avisoAposDias() - 1, StatusCobranca.EM_ATRASO);
        Cenario paga = cenario(prazos.avisoAposDias() + 5, StatusCobranca.PAGA);

        service.avisar();

        assertThat(prevista(passou.fatura())).isEqualTo(hoje.plusDays(prazos.diasAteSuspender()));
        assertThat(notificacoes("AVISO_SUSPENSAO", passou.fatura())).isEqualTo(1);
        assertThat(prevista(aindaNao.fatura())).isNull();
        assertThat(prevista(paga.fatura())).isNull();

        // A segunda execucao nao manda de novo nem empurra o prazo.
        service.avisar();
        assertThat(notificacoes("AVISO_SUSPENSAO", passou.fatura())).isEqualTo(1);
        assertThat(prevista(passou.fatura())).isEqualTo(hoje.plusDays(prazos.diasAteSuspender()));
    }

    @Test
    void masterVeQuantosDiasFaltamParaASuspensao() {
        Cenario cenario = cenario(prazos.avisoAposDias(), StatusCobranca.EM_ATRASO);
        assertThat(service.prevista(cenario.escola())).isEmpty();

        service.avisar();

        SuspensaoPrevistaResponse aviso = service.prevista(cenario.escola()).orElseThrow();
        assertThat(aviso.suspensaoEm()).isEqualTo(hoje.plusDays(prazos.diasAteSuspender()));
        assertThat(aviso.diasRestantes()).isEqualTo(prazos.diasAteSuspender());
        assertThat(aviso.faturaId()).isEqualTo(cenario.fatura());
        assertThat(aviso.faturaValor()).isEqualByComparingTo("690.00");
    }

    @Test
    void prazoVencidoComAFaturaEmAbertoSuspendeAEscola() {
        Cenario cenario = cenario(prazos.avisoAposDias() + prazos.diasAteSuspender(), StatusCobranca.EM_ATRASO);
        service.avisar();
        // O prazo do aviso acabou hoje.
        jdbc.update("UPDATE FATURA_PLATAFORMA SET SUSPENSAO_PREVISTA_PARA = ? WHERE ID_FATURA = ?", hoje,
                cenario.fatura());

        // O banco e dividido com as outras classes: conta a partir de 1, e o que vale e o estado desta escola.
        assertThat(service.suspender()).isPositive();

        assertThat(ativa(cenario.escola())).isFalse();
        assertThat(motivo(cenario.escola())).isEqualTo("FATURA_EM_ATRASO");
        assertThat(notificacoes("ESCOLA_SUSPENSA", cenario.fatura())).isEqualTo(1);
        // Ja suspensa: a proxima execucao nao repete o e-mail.
        service.suspender();
        assertThat(notificacoes("ESCOLA_SUSPENSA", cenario.fatura())).isEqualTo(1);
    }

    @Test
    void faturaPagaDentroDoPrazoNaoSuspende() {
        Cenario cenario = cenario(prazos.avisoAposDias(), StatusCobranca.EM_ATRASO);
        service.avisar();
        jdbc.update("UPDATE FATURA_PLATAFORMA SET STATUS = 'PAGA', VALOR_PAGO = VALOR, DATA_PAGAMENTO = CURRENT_DATE, "
                + "SUSPENSAO_PREVISTA_PARA = ? WHERE ID_FATURA = ?",
                hoje, cenario.fatura());

        service.suspender();
        assertThat(ativa(cenario.escola())).isTrue();
        assertThat(service.prevista(cenario.escola())).isEmpty();
    }

    @Test
    void reativarRecomecaOCicloComNovoAviso() {
        Cenario cenario = cenario(prazos.avisoAposDias() + prazos.diasAteSuspender(), StatusCobranca.EM_ATRASO);
        service.avisar();
        jdbc.update("UPDATE FATURA_PLATAFORMA SET SUSPENSAO_PREVISTA_PARA = ? WHERE ID_FATURA = ?", hoje,
                cenario.fatura());
        service.suspender();

        // O log aponta para o autor: o SUPER_ADMIN precisa existir no banco.
        UUID suporte = dados.usuario(null, "SUPER_ADMIN", "Suporte Eduka");
        autenticarComo(suporte, "SUPER_ADMIN", null);
        escolaService.ativar(cenario.escola());

        assertThat(ativa(cenario.escola())).isTrue();
        // A rotina suspendeu sem autor; o SUPER_ADMIN reativou, e as duas ficam na escola.
        assertThat(jdbc.queryForList("SELECT ORIGEM || ' ' || OPERACAO || ' ' || COALESCE(USUARIO_ID::text, '-') "
                + "FROM LOG_AUDITORIA WHERE ESCOLA_ID = ? AND ENTIDADE = 'ESCOLA' ORDER BY DATA_HORA",
                String.class, cenario.escola()))
                .containsExactly("SISTEMA SUSPENDER -", "USUARIO ATIVAR " + suporte);
        assertThat(motivo(cenario.escola())).isNull();
        assertThat(prevista(cenario.fatura())).isNull();
        // Sem aviso novo, a rotina nao suspende de novo no mesmo dia.
        service.suspender();
        assertThat(ativa(cenario.escola())).isTrue();
        service.avisar();
        assertThat(prevista(cenario.fatura())).isEqualTo(hoje.plusDays(prazos.diasAteSuspender()));
    }
}
