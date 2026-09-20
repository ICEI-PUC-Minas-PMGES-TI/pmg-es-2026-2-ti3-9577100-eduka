package br.pucminas.eduka.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import br.pucminas.eduka.backend.domains.enums.Status;
import br.pucminas.eduka.backend.domains.model.Escola;
import br.pucminas.eduka.backend.dto.EscolaRequest;
import br.pucminas.eduka.backend.exception.CnpjJaCadastradoException;
import br.pucminas.eduka.backend.repository.EscolaRepository;

@ExtendWith(MockitoExtension.class)
class EscolaServiceTest {

    @Mock
    private EscolaRepository escolaRepository;

    private EscolaService escolaService;

    @BeforeEach
    void setUp() {
        escolaService = new EscolaService(escolaRepository);
    }

    @Test
    void cadastrarPersisteCamposDoModeloComStatusAtivoPorPadrao() {
        when(escolaRepository.save(any(Escola.class))).thenAnswer(inv -> inv.getArgument(0));

        escolaService.cadastrar(new EscolaRequest(
                "  Colégio Eduka  ",
                " 12.345.678/0001-90 ",
                " Rua A, 10 ",
                " contato@eduka.com ",
                " 3133334444 ",
                null));

        ArgumentCaptor<Escola> captor = ArgumentCaptor.forClass(Escola.class);
        verify(escolaRepository).save(captor.capture());
        Escola salva = captor.getValue();
        assertThat(salva.getNome()).isEqualTo("Colégio Eduka");
        assertThat(salva.getCnpj()).isEqualTo("12.345.678/0001-90");
        assertThat(salva.getEndereco()).isEqualTo("Rua A, 10");
        assertThat(salva.getEmail()).isEqualTo("contato@eduka.com");
        assertThat(salva.getTelefone()).isEqualTo("3133334444");
        assertThat(salva.getStatus()).isEqualTo(Status.ATIVO);
    }

    @Test
    void cadastrarRespeitaStatusInformado() {
        when(escolaRepository.save(any(Escola.class))).thenAnswer(inv -> inv.getArgument(0));

        escolaService.cadastrar(new EscolaRequest("Colégio Eduka", null, null, null, null, Status.INATIVO));

        ArgumentCaptor<Escola> captor = ArgumentCaptor.forClass(Escola.class);
        verify(escolaRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(Status.INATIVO);
        verify(escolaRepository, never()).existsByCnpj(any());
    }

    @Test
    void cadastrarSemNomeEhRejeitado() {
        assertThatThrownBy(() -> escolaService.cadastrar(
                new EscolaRequest(" ", "123", null, null, null, null)))
                .isInstanceOf(IllegalArgumentException.class);
        verify(escolaRepository, never()).save(any());
    }

    @Test
    void cadastrarCnpjDuplicadoEhRejeitado() {
        when(escolaRepository.existsByCnpj("12345678000190")).thenReturn(true);

        assertThatThrownBy(() -> escolaService.cadastrar(
                new EscolaRequest("Colégio Eduka", "12345678000190", null, null, null, null)))
                .isInstanceOf(CnpjJaCadastradoException.class);
        verify(escolaRepository, never()).save(any());
    }
}
