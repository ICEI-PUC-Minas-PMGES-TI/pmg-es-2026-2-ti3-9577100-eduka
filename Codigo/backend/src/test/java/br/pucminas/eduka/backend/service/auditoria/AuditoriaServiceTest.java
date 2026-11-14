package br.pucminas.eduka.backend.service.auditoria;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import br.pucminas.eduka.backend.domain.auditoria.EntidadeAuditada;
import br.pucminas.eduka.backend.domain.auditoria.LogAuditoria;
import br.pucminas.eduka.backend.domain.auditoria.OperacaoAuditoria;
import br.pucminas.eduka.backend.domain.auditoria.OrigemAuditoria;
import br.pucminas.eduka.backend.repository.auditoria.LogAuditoriaRepository;
import br.pucminas.eduka.backend.security.IsolamentoEscola;
import br.pucminas.eduka.backend.security.UsuarioAutenticado;

/** O log grava autor e escola do token (ou, do provedor, so a escola), a entidade, o registro e a hora. */
@ExtendWith(MockitoExtension.class)
class AuditoriaServiceTest {

    private static final UUID ESCOLA = UUID.randomUUID();
    private static final UUID USUARIO = UUID.randomUUID();
    private static final Instant AGORA = Instant.parse("2026-10-14T13:00:00Z");

    @Mock
    private LogAuditoriaRepository logAuditoriaRepository;

    private AuditoriaService service;

    @BeforeEach
    void setUp() {
        service = new AuditoriaService(logAuditoriaRepository, new IsolamentoEscola(),
                Clock.fixed(AGORA, ZoneOffset.UTC));
    }

    @AfterEach
    void limparContexto() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void registraUsuarioEEscolaDoTokenComEntidadeRegistroEHora() {
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(
                new UsuarioAutenticado(USUARIO, "FINANCEIRO", ESCOLA), null, "ROLE_FINANCEIRO"));
        UUID cobrancaId = UUID.randomUUID();

        service.registrar(OperacaoAuditoria.CANCELAR, EntidadeAuditada.COBRANCA, cobrancaId);

        ArgumentCaptor<LogAuditoria> log = ArgumentCaptor.forClass(LogAuditoria.class);
        verify(logAuditoriaRepository).save(log.capture());
        assertThat(log.getValue().getUsuarioId()).isEqualTo(USUARIO);
        assertThat(log.getValue().getEscolaId()).isEqualTo(ESCOLA);
        assertThat(log.getValue().getOrigem()).isEqualTo(OrigemAuditoria.USUARIO);
        assertThat(log.getValue().getOperacao()).isEqualTo(OperacaoAuditoria.CANCELAR);
        assertThat(log.getValue().getEntidade()).isEqualTo(EntidadeAuditada.COBRANCA);
        assertThat(log.getValue().getRegistroId()).isEqualTo(cobrancaId);
        assertThat(log.getValue().getDataHora()).isEqualTo(AGORA);
    }

    @Test
    void semUsuarioAutenticadoNaoGravaLogSemAutor() {
        assertThatThrownBy(
                () -> service.registrar(OperacaoAuditoria.CRIAR, EntidadeAuditada.COBRANCA, UUID.randomUUID()))
                .isInstanceOf(AuthenticationCredentialsNotFoundException.class);
        verify(logAuditoriaRepository, never()).save(any());
    }

    @Test
    void doProvedorGravaSemAutorNaEscolaDoRegistro() {
        UUID cobrancaId = UUID.randomUUID();

        service.registrarDoProvedor(OperacaoAuditoria.CONFIRMAR_PAGAMENTO, EntidadeAuditada.COBRANCA, cobrancaId,
                ESCOLA);

        ArgumentCaptor<LogAuditoria> log = ArgumentCaptor.forClass(LogAuditoria.class);
        verify(logAuditoriaRepository).save(log.capture());
        assertThat(log.getValue().getUsuarioId()).isNull();
        assertThat(log.getValue().getOrigem()).isEqualTo(OrigemAuditoria.PROVEDOR);
        assertThat(log.getValue().getEscolaId()).isEqualTo(ESCOLA);
        assertThat(log.getValue().getOperacao()).isEqualTo(OperacaoAuditoria.CONFIRMAR_PAGAMENTO);
        assertThat(log.getValue().getEntidade()).isEqualTo(EntidadeAuditada.COBRANCA);
        assertThat(log.getValue().getRegistroId()).isEqualTo(cobrancaId);
        assertThat(log.getValue().getDataHora()).isEqualTo(AGORA);
    }
}
