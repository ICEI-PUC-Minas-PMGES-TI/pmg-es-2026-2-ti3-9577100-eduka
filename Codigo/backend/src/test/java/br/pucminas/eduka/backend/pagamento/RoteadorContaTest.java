package br.pucminas.eduka.backend.pagamento;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import br.pucminas.eduka.backend.domain.ContaRecebimentoEscola;
import br.pucminas.eduka.backend.domain.Escola;
import br.pucminas.eduka.backend.domain.PlanoEscola;
import br.pucminas.eduka.backend.domain.StatusContaRecebimento;
import br.pucminas.eduka.backend.dto.HabilitacaoRecebimentos;
import br.pucminas.eduka.backend.repository.ContaRecebimentoEscolaRepository;
import br.pucminas.eduka.backend.repository.EscolaRepository;

/** TST-U04: habilitacao devolve cada motivo do R11. */
class RoteadorContaTest {

    private static final UUID ESCOLA = UUID.randomUUID();

    private ContaRecebimentoEscolaRepository contaRepository;
    private EscolaRepository escolaRepository;
    private RoteadorConta roteador;

    @BeforeEach
    void setUp() {
        contaRepository = mock(ContaRecebimentoEscolaRepository.class);
        escolaRepository = mock(EscolaRepository.class);
        roteador = new RoteadorConta(contaRepository, escolaRepository);
    }

    @Test
    void contaAprovadaDeEscolaAtivaEstaHabilitada() {
        escola(true);
        conta(StatusContaRecebimento.APROVADA);

        assertThat(roteador.habilitacao(ESCOLA)).isEqualTo(new HabilitacaoRecebimentos(true, null));
    }

    @ParameterizedTest
    @CsvSource({
            "CRIANDO, EM_CRIACAO",
            "VERIFICACAO_PENDENTE, EM_CRIACAO",
            "ERRO_CRIACAO, ERRO_CRIACAO",
            "AGUARDANDO_APROVACAO, AGUARDANDO_APROVACAO",
            "REJEITADA, REJEITADA",
            "CREDENCIAL_INDISPONIVEL, CREDENCIAL_INDISPONIVEL" })
    void contaNaoAprovadaDevolveOMotivo(StatusContaRecebimento status, String motivo) {
        escola(true);
        conta(status);

        assertThat(roteador.habilitacao(ESCOLA)).isEqualTo(new HabilitacaoRecebimentos(false, motivo));
    }

    @Test
    void semContaDevolveNaoIniciada() {
        escola(true);
        when(contaRepository.findByEscolaId(ESCOLA)).thenReturn(Optional.empty());

        assertThat(roteador.habilitacao(ESCOLA)).isEqualTo(new HabilitacaoRecebimentos(false, "NAO_INICIADA"));
    }

    @Test
    void escolaInativaNaoRecebeNemComContaAprovada() {
        escola(false);
        conta(StatusContaRecebimento.APROVADA);

        assertThat(roteador.habilitacao(ESCOLA)).isEqualTo(new HabilitacaoRecebimentos(false, "ESCOLA_INATIVA"));
    }

    private void escola(boolean ativa) {
        Escola escola = Escola.cadastrar("Escola", "12345678000195", null, null, null, PlanoEscola.values()[0], ativa);
        when(escolaRepository.findById(ESCOLA)).thenReturn(Optional.of(escola));
    }

    private void conta(StatusContaRecebimento status) {
        when(contaRepository.findByEscolaId(ESCOLA))
                .thenReturn(Optional.of(ContaRecebimentoEscola.builder().escolaId(ESCOLA).status(status).build()));
    }
}
