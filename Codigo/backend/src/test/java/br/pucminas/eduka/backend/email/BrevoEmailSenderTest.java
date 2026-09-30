package br.pucminas.eduka.backend.email;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

class BrevoEmailSenderTest {

    private static final BrevoProperties PROPERTIES = new BrevoProperties("chave-teste",
            "https://api.brevo.com/v3", "nao-responda@eduka.com.br", "Eduka");

    private static final Email EMAIL = new Email("ana@escola.com", "Ana", "Seu acesso ao Eduka",
            "<p>Ola</p>", "Ola");

    private MockRestServiceServer servidor;
    private BrevoEmailSender sender;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        servidor = MockRestServiceServer.bindTo(builder).build();
        sender = new BrevoEmailSender(builder, PROPERTIES);
    }

    @Test
    void enviaPelaApiTransacionalComAChaveNoHeader() {
        servidor.expect(requestTo("https://api.brevo.com/v3/smtp/email"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("api-key", "chave-teste"))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.sender.email").value("nao-responda@eduka.com.br"))
                .andExpect(jsonPath("$.sender.name").value("Eduka"))
                .andExpect(jsonPath("$.to[0].email").value("ana@escola.com"))
                .andExpect(jsonPath("$.to[0].name").value("Ana"))
                .andExpect(jsonPath("$.subject").value("Seu acesso ao Eduka"))
                .andExpect(jsonPath("$.htmlContent").value("<p>Ola</p>"))
                .andExpect(jsonPath("$.textContent").value("Ola"))
                .andRespond(withSuccess("{\"messageId\":\"<abc@brevo>\"}", MediaType.APPLICATION_JSON));

        sender.enviar(EMAIL);

        servidor.verify();
    }

    @Test
    void erroDoBrevoViraExcecao() {
        servidor.expect(requestTo("https://api.brevo.com/v3/smtp/email"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        assertThatThrownBy(() -> sender.enviar(EMAIL)).isInstanceOf(RestClientResponseException.class);
    }

    @Test
    void semChaveNaoEstaConfigurado() {
        assertThat(new BrevoProperties("", null, null, null).configurado()).isFalse();
        assertThat(new BrevoProperties(null, null, null, null).configurado()).isFalse();
        assertThat(PROPERTIES.toString()).doesNotContain("chave-teste");
    }
}
