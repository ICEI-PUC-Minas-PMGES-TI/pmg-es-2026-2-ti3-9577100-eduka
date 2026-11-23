package br.pucminas.eduka.backend.service.privacidade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import br.pucminas.eduka.backend.domain.pessoas.Aluno;

class PoliticaRetencaoTest {

    private final PoliticaRetencao politica = new PoliticaRetencao(5);

    private static Aluno inativadoEm(Instant quando) {
        Aluno aluno = Aluno.builder().matricula("2026-001").build();
        aluno.setAtivo(false);
        aluno.setInativadoEm(quando);
        return aluno;
    }

    @Test
    void anonimizacaoPrevistaEhCincoAnosDepoisDaInativacaoNoDiaDaEscola() {
        // 23h de 14/11/2026 em Brasilia: em UTC ja e dia 15.
        Aluno aluno = inativadoEm(Instant.parse("2026-11-15T02:00:00Z"));

        assertThat(politica.anonimizacaoPrevista(aluno)).isEqualTo(LocalDate.of(2031, 11, 14));
    }

    @Test
    void semPrevisaoParaAtivoOuAnonimizado() {
        Aluno ativo = Aluno.builder().matricula("1").build();
        Aluno anonimizado = inativadoEm(Instant.parse("2020-01-01T12:00:00Z"));
        anonimizado.setAnonimizadoEm(Instant.parse("2025-01-02T12:00:00Z"));

        assertThat(politica.anonimizacaoPrevista(ativo)).isNull();
        assertThat(politica.anonimizacaoPrevista(anonimizado)).isNull();
    }

    @Test
    void limiteEhOMesmoInstanteCincoAnosAntes() {
        assertThat(politica.inativadosAte(Instant.parse("2031-11-15T06:00:00Z")))
                .isEqualTo(Instant.parse("2026-11-15T06:00:00Z"));
    }

    @Test
    void prazoMenorQueUmAnoNaoSobe() {
        assertThatThrownBy(() -> new PoliticaRetencao(0)).isInstanceOf(IllegalArgumentException.class);
    }
}
