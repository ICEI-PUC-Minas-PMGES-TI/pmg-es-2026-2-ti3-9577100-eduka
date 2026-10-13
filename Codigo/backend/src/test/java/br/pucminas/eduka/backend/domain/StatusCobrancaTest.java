package br.pucminas.eduka.backend.domain;

import static br.pucminas.eduka.backend.domain.StatusCobranca.CANCELADA;
import static br.pucminas.eduka.backend.domain.StatusCobranca.EM_ATRASO;
import static br.pucminas.eduka.backend.domain.StatusCobranca.PAGA;
import static br.pucminas.eduka.backend.domain.StatusCobranca.PENDENTE;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

/** Mudancas de situacao permitidas e o calculo de "em atraso". */
class StatusCobrancaTest {

    private static final LocalDate HOJE = LocalDate.of(2026, 10, 10);

    @Test
    void soCobrancaEmAbertoMudaDeSituacao() {
        Map<StatusCobranca, Set<StatusCobranca>> permitidas = Map.of(
                PENDENTE, EnumSet.of(EM_ATRASO, PAGA, CANCELADA),
                EM_ATRASO, EnumSet.of(PENDENTE, PAGA, CANCELADA),
                PAGA, EnumSet.noneOf(StatusCobranca.class),
                CANCELADA, EnumSet.noneOf(StatusCobranca.class));

        for (StatusCobranca de : StatusCobranca.values()) {
            for (StatusCobranca para : StatusCobranca.values()) {
                assertThat(de.podeMudarPara(para))
                        .as("%s -> %s", de, para)
                        .isEqualTo(permitidas.get(de).contains(para));
            }
        }
    }

    @Test
    void cobrancaVenceNoProprioDiaEAtrasaNoSeguinte() {
        assertThat(StatusCobranca.emAbertoCom(HOJE.plusDays(1), HOJE)).isEqualTo(PENDENTE);
        assertThat(StatusCobranca.emAbertoCom(HOJE, HOJE)).isEqualTo(PENDENTE);
        assertThat(StatusCobranca.emAbertoCom(HOJE.minusDays(1), HOJE)).isEqualTo(EM_ATRASO);
    }

    @Test
    void situacaoExibidaSoTrocaPendenteVencida() {
        LocalDate ontem = HOJE.minusDays(1);
        assertThat(StatusCobranca.efetivo(PENDENTE, ontem, HOJE)).isEqualTo(EM_ATRASO);
        assertThat(StatusCobranca.efetivo(PENDENTE, HOJE, HOJE)).isEqualTo(PENDENTE);
        assertThat(StatusCobranca.efetivo(EM_ATRASO, ontem, HOJE)).isEqualTo(EM_ATRASO);
        assertThat(StatusCobranca.efetivo(PAGA, ontem, HOJE)).isEqualTo(PAGA);
        assertThat(StatusCobranca.efetivo(CANCELADA, ontem, HOJE)).isEqualTo(CANCELADA);
    }
}
