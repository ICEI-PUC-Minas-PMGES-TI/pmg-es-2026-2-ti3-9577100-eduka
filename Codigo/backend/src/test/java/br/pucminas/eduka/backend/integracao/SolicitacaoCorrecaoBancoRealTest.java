package br.pucminas.eduka.backend.integracao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import br.pucminas.eduka.backend.domain.pessoas.StatusSolicitacaoCorrecao;
import br.pucminas.eduka.backend.dto.pessoas.SolicitacaoCorrecaoResponse;
import br.pucminas.eduka.backend.exception.comum.RecursoNaoEncontradoException;
import br.pucminas.eduka.backend.exception.pessoas.SolicitacaoCorrecaoAbertaException;
import br.pucminas.eduka.backend.service.pessoas.SolicitacaoCorrecaoService;

/**
 * Pedido de correcao de dados (IU15) no banco: uma solicitacao aberta por
 * aluno, a lista da secretaria com o nome decifrado e o isolamento entre
 * escolas.
 */
class SolicitacaoCorrecaoBancoRealTest extends BancoRealTest {

    @Autowired
    private SolicitacaoCorrecaoService service;

    private UUID escola;
    private DadosDeTeste.AlunoComAcesso aluno;

    @BeforeEach
    void alunoDaEscola() {
        escola = dados.escola();
        aluno = dados.alunoComAcesso(escola, "Lucas Andrade", "52998224725");
        autenticarComo(aluno.usuarioId(), "ALUNO", escola);
    }

    @Test
    void alunoPedeUmaVezEVeOPedidoAberto() {
        assertThat(service.minhaAberta()).isEmpty();

        SolicitacaoCorrecaoResponse pedido = service.abrir("  O CPF está errado  ");

        assertThat(pedido.status()).isEqualTo(StatusSolicitacaoCorrecao.ABERTA);
        assertThat(pedido.mensagem()).isEqualTo("O CPF está errado");
        assertThat(pedido.criadoEm()).isNotNull();
        assertThat(service.minhaAberta()).get().extracting(SolicitacaoCorrecaoResponse::id).isEqualTo(pedido.id());
        assertThatThrownBy(() -> service.abrir("De novo")).isInstanceOf(SolicitacaoCorrecaoAbertaException.class);
    }

    @Test
    void secretariaVeOPedidoComONomeEAtendeLiberandoUmNovo() {
        UUID pedido = service.abrir("Telefone mudou").id();

        entrarComo("SECRETARIA", escola);
        assertThat(service.listarAbertas()).singleElement().satisfies(s -> {
            assertThat(s.alunoNome()).isEqualTo("Lucas Andrade");
            assertThat(s.alunoMatricula()).isNotBlank();
        });
        assertThat(service.atender(pedido).status()).isEqualTo(StatusSolicitacaoCorrecao.ATENDIDA);
        assertThat(service.listarAbertas()).isEmpty();

        autenticarComo(aluno.usuarioId(), "ALUNO", escola);
        assertThat(service.minhaAberta()).isEmpty();
        assertThat(service.abrir("Endereço também").status()).isEqualTo(StatusSolicitacaoCorrecao.ABERTA);
    }

    @Test
    void outraEscolaNaoVeNemAtendeOPedido() {
        UUID pedido = service.abrir("Nome com acento errado").id();

        entrarComo("SECRETARIA", dados.escola());
        assertThat(service.listarAbertas()).isEmpty();
        assertThatThrownBy(() -> service.atender(pedido)).isInstanceOf(RecursoNaoEncontradoException.class);
    }
}
