package br.pucminas.eduka.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import br.pucminas.eduka.backend.domain.Escola;
import br.pucminas.eduka.backend.dto.EscolaResponse;
import br.pucminas.eduka.backend.dto.EscolaUpdateRequest;
import br.pucminas.eduka.backend.repository.EscolaRepository;

@ExtendWith(MockitoExtension.class)
class EscolaServiceTest {

    @Mock
    private EscolaRepository escolaRepository;

    private EscolaService escolaService;

    private Escola escola(UUID id, String nome) {
        return new Escola(id, nome, "12.345.678/0001-90", "Rua das Escolas, 100",
                "contato@escola.com", "(31) 4000-0000", true, Instant.now());
    }

    @Test
    void listaTodasAsEscolasCadastradas() {
        escolaService = new EscolaService(escolaRepository);
        Escola escolaA = escola(UUID.randomUUID(), "Escola A");
        Escola escolaB = escola(UUID.randomUUID(), "Escola B");
        when(escolaRepository.findAll()).thenReturn(List.of(escolaA, escolaB));

        List<EscolaResponse> resultado = escolaService.listar();

        assertThat(resultado).hasSize(2);
        assertThat(resultado).extracting(EscolaResponse::nome).containsExactly("Escola A", "Escola B");
    }

    @Test
    void retornaListaVaziaQuandoNaoHaEscolasCadastradas() {
        escolaService = new EscolaService(escolaRepository);
        when(escolaRepository.findAll()).thenReturn(List.of());

        List<EscolaResponse> resultado = escolaService.listar();

        assertThat(resultado).isEmpty();
    }

    @Test
    void buscaEscolaPorIdQuandoExiste() {
        escolaService = new EscolaService(escolaRepository);
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
        escolaService = new EscolaService(escolaRepository);
        UUID id = UUID.randomUUID();
        when(escolaRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> escolaService.buscarPorId(id))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("404")
                .hasMessageContaining("escola não encontrada");
    }

    @Test
    void atualizaDadosDeUmaEscolaExistente() {
        escolaService = new EscolaService(escolaRepository);
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
    void rejeitaAtualizacaoDeEscolaInexistenteComNaoEncontrado() {
        escolaService = new EscolaService(escolaRepository);
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
        escolaService = new EscolaService(escolaRepository);
        UUID id = UUID.randomUUID();
        Escola escola = new Escola(id, "Escola A", "12.345.678/0001-90", "Rua das Escolas, 100",
                "contato@escola.com", "(31) 4000-0000", false, Instant.now());
        when(escolaRepository.findById(id)).thenReturn(Optional.of(escola));
        when(escolaRepository.save(escola)).thenReturn(escola);

        EscolaResponse resultado = escolaService.ativar(id);

        assertThat(resultado.ativo()).isTrue();
    }

    @Test
    void rejeitaAtivacaoDeEscolaInexistenteComNaoEncontrado() {
        escolaService = new EscolaService(escolaRepository);
        UUID id = UUID.randomUUID();
        when(escolaRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> escolaService.ativar(id))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("404")
                .hasMessageContaining("escola não encontrada");
    }

    @Test
    void suspendeUmaEscolaAtiva() {
        escolaService = new EscolaService(escolaRepository);
        UUID id = UUID.randomUUID();
        Escola escola = escola(id, "Escola A");
        when(escolaRepository.findById(id)).thenReturn(Optional.of(escola));
        when(escolaRepository.save(escola)).thenReturn(escola);

        EscolaResponse resultado = escolaService.suspender(id);

        assertThat(resultado.ativo()).isFalse();
    }

    @Test
    void rejeitaSuspensaoDeEscolaInexistenteComNaoEncontrado() {
        escolaService = new EscolaService(escolaRepository);
        UUID id = UUID.randomUUID();
        when(escolaRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> escolaService.suspender(id))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("404")
                .hasMessageContaining("escola não encontrada");
    }
}
