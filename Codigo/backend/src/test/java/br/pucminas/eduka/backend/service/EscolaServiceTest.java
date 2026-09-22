package br.pucminas.eduka.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import br.pucminas.eduka.backend.domain.Escola;
import br.pucminas.eduka.backend.dto.EscolaRequest;
import br.pucminas.eduka.backend.dto.EscolaResponse;
import br.pucminas.eduka.backend.dto.EscolaUpdateRequest;
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

    private Escola escola(UUID id, String nome) {
        return new Escola(id, nome, "12.345.678/0001-90", "Rua das Escolas, 100",
                "contato@escola.com", "(31) 4000-0000", true, Instant.now(), 0L);
    }

    @Test
    void listaTodasAsEscolasCadastradas() {
        Escola escolaA = escola(UUID.randomUUID(), "Escola A");
        Escola escolaB = escola(UUID.randomUUID(), "Escola B");
        when(escolaRepository.findAll()).thenReturn(List.of(escolaA, escolaB));

        List<EscolaResponse> resultado = escolaService.listar();

        assertThat(resultado).hasSize(2);
        assertThat(resultado).extracting(EscolaResponse::nome).containsExactly("Escola A", "Escola B");
    }

    @Test
    void retornaListaVaziaQuandoNaoHaEscolasCadastradas() {
        when(escolaRepository.findAll()).thenReturn(List.of());

        List<EscolaResponse> resultado = escolaService.listar();

        assertThat(resultado).isEmpty();
    }

    @Test
    void buscaEscolaPorIdQuandoExiste() {
        UUID id = UUID.randomUUID();
        Escola escola = escola(id, "Escola A");
        when(escolaRepository.findById(id)).thenReturn(Optional.of(escola));

        EscolaResponse resultado = escolaService.buscarPorId(id);

        assertThat(resultado.id()).isEqualTo(id);
        assertThat(resultado.nome()).isEqualTo("Escola A");
        assertThat(resultado.cnpjCpf()).isEqualTo("12.345.678/0001-90");
        assertThat(resultado.ativo()).isTrue();
    }

    @Test
    void rejeitaBuscaPorIdInexistenteComNaoEncontrado() {
        UUID id = UUID.randomUUID();
        when(escolaRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> escolaService.buscarPorId(id))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("404")
                .hasMessageContaining("escola não encontrada");
    }

    @Test
    void atualizaDadosDeUmaEscolaExistente() {
        UUID id = UUID.randomUUID();
        Escola escola = escola(id, "Escola A");
        EscolaUpdateRequest request = new EscolaUpdateRequest(
                "Escola A Renomeada", "98.765.432/0001-10", "Av. Nova, 200",
                "novo@escola.com", "(31) 5000-0000");
        when(escolaRepository.findById(id)).thenReturn(Optional.of(escola));
        when(escolaRepository.save(escola)).thenReturn(escola);

        EscolaResponse resultado = escolaService.atualizar(id, request);

        assertThat(resultado.nome()).isEqualTo("Escola A Renomeada");
        assertThat(resultado.cnpjCpf()).isEqualTo("98.765.432/0001-10");
        assertThat(resultado.endereco()).isEqualTo("Av. Nova, 200");
        assertThat(resultado.email()).isEqualTo("novo@escola.com");
        assertThat(resultado.telefone()).isEqualTo("(31) 5000-0000");
    }

    @Test
    void mantemCamposOpcionaisQuandoAtualizacaoParcialNaoOsInforma() {
        UUID id = UUID.randomUUID();
        Escola escola = escola(id, "Escola A");
        EscolaUpdateRequest request = new EscolaUpdateRequest("Escola A Renomeada", null, null, null, null);
        when(escolaRepository.findById(id)).thenReturn(Optional.of(escola));
        when(escolaRepository.save(escola)).thenReturn(escola);

        EscolaResponse resultado = escolaService.atualizar(id, request);

        assertThat(resultado.nome()).isEqualTo("Escola A Renomeada");
        assertThat(resultado.cnpjCpf()).isEqualTo("12.345.678/0001-90");
        assertThat(resultado.endereco()).isEqualTo("Rua das Escolas, 100");
        assertThat(resultado.email()).isEqualTo("contato@escola.com");
        assertThat(resultado.telefone()).isEqualTo("(31) 4000-0000");
    }

    @Test
    void rejeitaAtualizacaoDeEscolaInexistenteComNaoEncontrado() {
        UUID id = UUID.randomUUID();
        EscolaUpdateRequest request = new EscolaUpdateRequest(
                "Escola A", "12.345.678/0001-90", "Rua das Escolas, 100",
                "contato@escola.com", "(31) 4000-0000");
        when(escolaRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> escolaService.atualizar(id, request))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("404")
                .hasMessageContaining("escola não encontrada");
    }

    @Test
    void ativaUmaEscolaSuspensa() {
        UUID id = UUID.randomUUID();
        Escola escola = new Escola(id, "Escola A", "12.345.678/0001-90", "Rua das Escolas, 100",
                "contato@escola.com", "(31) 4000-0000", false, Instant.now(), 0L);
        when(escolaRepository.findById(id)).thenReturn(Optional.of(escola));
        when(escolaRepository.save(escola)).thenReturn(escola);

        EscolaResponse resultado = escolaService.ativar(id);

        assertThat(resultado.ativo()).isTrue();
    }

    @Test
    void rejeitaAtivacaoDeEscolaInexistenteComNaoEncontrado() {
        UUID id = UUID.randomUUID();
        when(escolaRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> escolaService.ativar(id))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("404")
                .hasMessageContaining("escola não encontrada");
    }

    @Test
    void suspendeUmaEscolaAtiva() {
        UUID id = UUID.randomUUID();
        Escola escola = escola(id, "Escola A");
        when(escolaRepository.findById(id)).thenReturn(Optional.of(escola));
        when(escolaRepository.save(escola)).thenReturn(escola);

        EscolaResponse resultado = escolaService.suspender(id);

        assertThat(resultado.ativo()).isFalse();
    }

    @Test
    void rejeitaSuspensaoDeEscolaInexistenteComNaoEncontrado() {
        UUID id = UUID.randomUUID();
        when(escolaRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> escolaService.suspender(id))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("404")
                .hasMessageContaining("escola não encontrada");
    }

    @Test
    void cadastrarPersisteCamposDoModeloComAtivoPorPadrao() {
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
        assertThat(salva.getCnpjCpf()).isEqualTo("12.345.678/0001-90");
        assertThat(salva.getEndereco()).isEqualTo("Rua A, 10");
        assertThat(salva.getEmail()).isEqualTo("contato@eduka.com");
        assertThat(salva.getTelefone()).isEqualTo("3133334444");
        assertThat(salva.isAtivo()).isTrue();
    }

    @Test
    void cadastrarRespeitaAtivoInformado() {
        when(escolaRepository.save(any(Escola.class))).thenAnswer(inv -> inv.getArgument(0));

        escolaService.cadastrar(new EscolaRequest("Colégio Eduka", null, null, null, null, false));

        ArgumentCaptor<Escola> captor = ArgumentCaptor.forClass(Escola.class);
        verify(escolaRepository).save(captor.capture());
        assertThat(captor.getValue().isAtivo()).isFalse();
        verify(escolaRepository, never()).existsByCnpjCpf(any());
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
        when(escolaRepository.existsByCnpjCpf("12345678000190")).thenReturn(true);

        assertThatThrownBy(() -> escolaService.cadastrar(
                new EscolaRequest("Colégio Eduka", "12345678000190", null, null, null, null)))
                .isInstanceOf(CnpjJaCadastradoException.class);
        verify(escolaRepository, never()).save(any());
    }
}
