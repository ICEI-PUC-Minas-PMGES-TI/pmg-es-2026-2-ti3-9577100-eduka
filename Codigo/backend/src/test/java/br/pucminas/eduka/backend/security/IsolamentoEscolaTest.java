package br.pucminas.eduka.backend.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class IsolamentoEscolaTest {

    private static final UUID ESCOLA_A = UUID.randomUUID();
    private static final UUID ESCOLA_B = UUID.randomUUID();

    private final IsolamentoEscola isolamento = new IsolamentoEscola();

    @AfterEach
    void limparContexto() {
        SecurityContextHolder.clearContext();
    }

    private static void autenticarComo(String perfil, UUID escolaId) {
        UsuarioAutenticado usuario = new UsuarioAutenticado(UUID.randomUUID(), perfil, escolaId);
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken(usuario, null, "ROLE_" + perfil));
    }

    @Test
    void mesmaEscolaPassa() {
        autenticarComo("SECRETARIA", ESCOLA_A);

        assertThatCode(() -> isolamento.exigirAcesso(ESCOLA_A)).doesNotThrowAnyException();
    }

    @Test
    void outraEscolaLancaAcessoNegado() {
        autenticarComo("SECRETARIA", ESCOLA_A);

        assertThatThrownBy(() -> isolamento.exigirAcesso(ESCOLA_B))
                .isInstanceOf(AcessoOutraEscolaException.class);
    }

    @Test
    void masterNaoEscapaDaPropriaEscola() {
        autenticarComo("MASTER", ESCOLA_A);

        assertThatThrownBy(() -> isolamento.exigirAcesso(ESCOLA_B))
                .isInstanceOf(AcessoOutraEscolaException.class);
    }

    @Test
    void superAdminPassaEmQualquerEscola() {
        autenticarComo("SUPER_ADMIN", ESCOLA_A);

        assertThatCode(() -> isolamento.exigirAcesso(ESCOLA_B)).doesNotThrowAnyException();
    }

    @Test
    void recursoSemEscolaEhErroDeProgramacao() {
        autenticarComo("SUPER_ADMIN", ESCOLA_A);

        // Nem o super admin passa: escolaId nulo indica entidade mal montada,
        // nao um recurso "global".
        assertThatThrownBy(() -> isolamento.exigirAcesso(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void escolaIdAtualVemDoToken() {
        autenticarComo("FINANCEIRO", ESCOLA_B);

        assertThat(isolamento.escolaIdAtual()).isEqualTo(ESCOLA_B);
    }

    @Test
    void semAutenticacaoNoContextoLancaCredenciaisAusentes() {
        assertThatThrownBy(() -> isolamento.exigirAcesso(ESCOLA_A))
                .isInstanceOf(AuthenticationCredentialsNotFoundException.class);
        assertThatThrownBy(isolamento::escolaIdAtual)
                .isInstanceOf(AuthenticationCredentialsNotFoundException.class);
    }

    @Test
    void principalQueNaoEhUsuarioAutenticadoEhTratadoComoNaoAutenticado() {
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken("string-qualquer", null));

        assertThatThrownBy(() -> isolamento.exigirAcesso(ESCOLA_A))
                .isInstanceOf(AuthenticationCredentialsNotFoundException.class);
    }
}
