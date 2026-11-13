package br.pucminas.eduka.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import br.pucminas.eduka.backend.domain.Endereco;
import br.pucminas.eduka.backend.domain.Escola;
import br.pucminas.eduka.backend.domain.ModuloEscola;
import br.pucminas.eduka.backend.domain.MotivoSuspensao;
import br.pucminas.eduka.backend.domain.PlanoEscola;
import br.pucminas.eduka.backend.dto.EnderecoDto;
import br.pucminas.eduka.backend.dto.EscolaRequest;
import br.pucminas.eduka.backend.dto.EscolaResponse;
import br.pucminas.eduka.backend.dto.EscolaUpdateRequest;
import br.pucminas.eduka.backend.dto.MasterInicialRequest;
import br.pucminas.eduka.backend.exception.CnpjJaCadastradoException;
import br.pucminas.eduka.backend.repository.EscolaRepository;

@ExtendWith(MockitoExtension.class)
class EscolaServiceTest {

    @Mock
    private EscolaRepository escolaRepository;
    @Mock
    private AuditoriaService auditoriaService;
    @Mock
    private MasterInicialService masterInicialService;

    private static final MasterInicialRequest MASTER = new MasterInicialRequest("Bia Gestora", "52998224725",
            "bia@escola.com");

    private EscolaService escolaService;

    @BeforeEach
    void setUp() {
        escolaService = new EscolaService(escolaRepository, auditoriaService, masterInicialService);
    }

    private static final Endereco ENDERECO = new Endereco("Rua das Escolas", "100", "Centro", "Belo Horizonte",
            "MG", "30110008");

    private Escola escola(UUID id, String nome, boolean ativo) {
        return new Escola(id, nome, "12.345.678/0001-90", ENDERECO, "contato@escola.com", "(31) 4000-0000",
                PlanoEscola.BASICO, ativo, ativo ? null : MotivoSuspensao.MANUAL, LocalDate.of(2024, 1, 15), LocalDate.of(2026, 1, 15), 500,
                new HashSet<>(Set.of(ModuloEscola.PORTAL_ALUNO)), Instant.now(), 0L);
    }

    private Escola escola(UUID id, String nome) {
        return escola(id, nome, true);
    }

    private static EscolaUpdateRequest atualizacao(String nome, String cnpjCpf, EnderecoDto endereco) {
        return new EscolaUpdateRequest(nome, cnpjCpf, endereco, null, null, null, null, null, null, null);
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
                "Escola A Renomeada", "98.765.432/0001-10",
                new EnderecoDto("Av. Nova", "200", "Savassi", "Belo Horizonte", "mg", "30140071"),
                "novo@escola.com", "(31) 5000-0000", PlanoEscola.ENTERPRISE, false,
                LocalDate.of(2027, 2, 1), 1500,
                Set.of(ModuloEscola.GESTAO_FINANCEIRA, ModuloEscola.PORTAL_PROFESSOR));
        when(escolaRepository.findById(id)).thenReturn(Optional.of(escola));
        when(escolaRepository.save(escola)).thenReturn(escola);

        EscolaResponse resultado = escolaService.atualizar(id, request);

        assertThat(resultado.nome()).isEqualTo("Escola A Renomeada");
        assertThat(resultado.cnpjCpf()).isEqualTo("98.765.432/0001-10");
        assertThat(resultado.endereco())
                .isEqualTo(new EnderecoDto("Av. Nova", "200", "Savassi", "Belo Horizonte", "MG", "30140071"));
        assertThat(resultado.email()).isEqualTo("novo@escola.com");
        assertThat(resultado.telefone()).isEqualTo("(31) 5000-0000");
        assertThat(resultado.plano()).isEqualTo(PlanoEscola.ENTERPRISE);
        assertThat(resultado.ativo()).isFalse();
        // O inicio da vigencia nao e alteravel pela edicao.
        assertThat(resultado.inicioVigencia()).isEqualTo(LocalDate.of(2024, 1, 15));
        assertThat(resultado.fimVigencia()).isEqualTo(LocalDate.of(2027, 2, 1));
        assertThat(resultado.limiteUsuarios()).isEqualTo(1500);
        assertThat(resultado.modulos())
                .containsExactly(ModuloEscola.PORTAL_PROFESSOR, ModuloEscola.GESTAO_FINANCEIRA);
    }

    @Test
    void mantemCamposOpcionaisQuandoAtualizacaoParcialNaoOsInforma() {
        UUID id = UUID.randomUUID();
        Escola escola = escola(id, "Escola A");
        EscolaUpdateRequest request = atualizacao("Escola A Renomeada", null, null);
        when(escolaRepository.findById(id)).thenReturn(Optional.of(escola));
        when(escolaRepository.save(escola)).thenReturn(escola);

        EscolaResponse resultado = escolaService.atualizar(id, request);

        assertThat(resultado.nome()).isEqualTo("Escola A Renomeada");
        assertThat(resultado.cnpjCpf()).isEqualTo("12.345.678/0001-90");
        assertThat(resultado.endereco()).isEqualTo(EnderecoDto.from(ENDERECO));
        assertThat(resultado.email()).isEqualTo("contato@escola.com");
        assertThat(resultado.telefone()).isEqualTo("(31) 4000-0000");
        assertThat(resultado.plano()).isEqualTo(PlanoEscola.BASICO);
        assertThat(resultado.ativo()).isTrue();
        assertThat(resultado.inicioVigencia()).isEqualTo(LocalDate.of(2024, 1, 15));
        assertThat(resultado.fimVigencia()).isEqualTo(LocalDate.of(2026, 1, 15));
        assertThat(resultado.limiteUsuarios()).isEqualTo(500);
        assertThat(resultado.modulos()).containsExactly(ModuloEscola.PORTAL_ALUNO);
    }

    @Test
    void atualizacaoComCamposEmBrancoLimpaOsCampos() {
        UUID id = UUID.randomUUID();
        Escola escola = escola(id, "Escola A");
        EscolaUpdateRequest request = new EscolaUpdateRequest("Escola A", null,
                new EnderecoDto(" ", "", null, null, "", ""), null, " ", null, null, null, null, Set.of());
        when(escolaRepository.findById(id)).thenReturn(Optional.of(escola));
        when(escolaRepository.save(escola)).thenReturn(escola);

        EscolaResponse resultado = escolaService.atualizar(id, request);

        assertThat(resultado.endereco()).isNull();
        assertThat(resultado.telefone()).isNull();
        assertThat(resultado.modulos()).isEmpty();
    }

    @Test
    void rejeitaAtualizacaoComCnpjDeOutraEscola() {
        UUID id = UUID.randomUUID();
        when(escolaRepository.findById(id)).thenReturn(Optional.of(escola(id, "Escola A")));
        when(escolaRepository.existsByCnpjCpfAndIdNot("98765432000110", id)).thenReturn(true);

        assertThatThrownBy(() -> escolaService.atualizar(id, atualizacao("Escola A", "98765432000110", null)))
                .isInstanceOf(CnpjJaCadastradoException.class);
        verify(escolaRepository, never()).save(any());
    }

    @Test
    void rejeitaVigenciaQueTerminaAntesDeComecar() {
        UUID id = UUID.randomUUID();
        when(escolaRepository.findById(id)).thenReturn(Optional.of(escola(id, "Escola A")));
        // Inicio da escola de teste: 15/01/2024.
        EscolaUpdateRequest request = new EscolaUpdateRequest("Escola A", null, null, null, null, null, null,
                LocalDate.of(2023, 12, 31), null, null);

        assertThatThrownBy(() -> escolaService.atualizar(id, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("vigencia");
        verify(escolaRepository, never()).save(any());
    }

    @Test
    void rejeitaAtualizacaoDeEscolaInexistenteComNaoEncontrado() {
        UUID id = UUID.randomUUID();
        EscolaUpdateRequest request = atualizacao("Escola A", "12.345.678/0001-90", null);
        when(escolaRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> escolaService.atualizar(id, request))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("404")
                .hasMessageContaining("escola não encontrada");
    }

    @Test
    void ativaUmaEscolaSuspensa() {
        UUID id = UUID.randomUUID();
        Escola escola = escola(id, "Escola A", false);
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
                new EnderecoDto(" Rua A ", " 10 ", " Centro ", " Belo Horizonte ", " mg ", " 30110008 "),
                " contato@eduka.com ",
                " 3133334444 ",
                PlanoEscola.AVANCADO,
                null,
                MASTER));

        ArgumentCaptor<Escola> captor = ArgumentCaptor.forClass(Escola.class);
        verify(escolaRepository).save(captor.capture());
        Escola salva = captor.getValue();
        assertThat(salva.getNome()).isEqualTo("Colégio Eduka");
        assertThat(salva.getCnpjCpf()).isEqualTo("12.345.678/0001-90");
        assertThat(salva.getEndereco())
                .isEqualTo(new Endereco("Rua A", "10", "Centro", "Belo Horizonte", "MG", "30110008"));
        assertThat(salva.getEmail()).isEqualTo("contato@eduka.com");
        assertThat(salva.getTelefone()).isEqualTo("3133334444");
        assertThat(salva.getPlano()).isEqualTo(PlanoEscola.AVANCADO);
        assertThat(salva.isAtivo()).isTrue();
        assertThat(salva.getModulos()).isEmpty();
        assertThat(salva.getInicioVigencia()).isEqualTo(LocalDate.now());
    }

    @Test
    void cadastrarComEnderecoEmBrancoNaoGuardaEndereco() {
        when(escolaRepository.save(any(Escola.class))).thenAnswer(inv -> inv.getArgument(0));

        escolaService.cadastrar(new EscolaRequest("Colégio Eduka", null, new EnderecoDto("", " ", null, null, "", ""),
                null, null, PlanoEscola.BASICO, null, MASTER));

        ArgumentCaptor<Escola> captor = ArgumentCaptor.forClass(Escola.class);
        verify(escolaRepository).save(captor.capture());
        assertThat(captor.getValue().getEndereco()).isNull();
    }

    @Test
    void cadastrarSemPlanoEhRejeitado() {
        assertThatThrownBy(() -> escolaService.cadastrar(
                new EscolaRequest("Colégio Eduka", null, null, null, null, null, null, MASTER)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Plano");
        verify(escolaRepository, never()).save(any());
    }

    @Test
    void cadastrarRespeitaAtivoInformado() {
        when(escolaRepository.save(any(Escola.class))).thenAnswer(inv -> inv.getArgument(0));

        escolaService.cadastrar(new EscolaRequest("Colégio Eduka", null, null, null, null, PlanoEscola.BASICO, false, MASTER));

        ArgumentCaptor<Escola> captor = ArgumentCaptor.forClass(Escola.class);
        verify(escolaRepository).save(captor.capture());
        assertThat(captor.getValue().isAtivo()).isFalse();
        verify(escolaRepository, never()).existsByCnpjCpf(any());
    }

    @Test
    void cadastrarSemNomeEhRejeitado() {
        assertThatThrownBy(() -> escolaService.cadastrar(
                new EscolaRequest(" ", "123", null, null, null, PlanoEscola.BASICO, null, MASTER)))
                .isInstanceOf(IllegalArgumentException.class);
        verify(escolaRepository, never()).save(any());
    }

    @Test
    void cadastrarCnpjDuplicadoEhRejeitado() {
        when(escolaRepository.existsByCnpjCpf("12345678000190")).thenReturn(true);

        assertThatThrownBy(() -> escolaService.cadastrar(
                new EscolaRequest("Colégio Eduka", "12345678000190", null, null, null, PlanoEscola.BASICO, null, MASTER)))
                .isInstanceOf(CnpjJaCadastradoException.class);
        verify(escolaRepository, never()).save(any());
    }

    @Test
    void cadastrarCriaOMasterInicialNaEscolaNova() {
        UUID escolaId = UUID.randomUUID();
        when(escolaRepository.save(any(Escola.class))).thenAnswer(inv -> {
            Escola nova = inv.getArgument(0);
            return escola(escolaId, nova.getNome());
        });

        escolaService.cadastrar(new EscolaRequest("Colégio Eduka", null, null, null, null, PlanoEscola.BASICO,
                null, MASTER));

        verify(masterInicialService).criar(escolaId, MASTER);
    }

    @Test
    void cadastrarSemMasterEhRejeitado() {
        assertThatThrownBy(() -> escolaService.cadastrar(
                new EscolaRequest("Colégio Eduka", null, null, null, null, PlanoEscola.BASICO, null, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("MASTER");
        verify(escolaRepository, never()).save(any());
        verify(masterInicialService, never()).criar(any(), any());
    }

    @Test
    void cnpjDuplicadoNaoCriaMaster() {
        when(escolaRepository.existsByCnpjCpf("12345678000190")).thenReturn(true);

        assertThatThrownBy(() -> escolaService.cadastrar(
                new EscolaRequest("Colégio Eduka", "12345678000190", null, null, null, PlanoEscola.BASICO, null,
                        MASTER)))
                .isInstanceOf(CnpjJaCadastradoException.class);
        verify(masterInicialService, never()).criar(any(), any());
    }
}
