package br.pucminas.eduka.backend.service.acesso;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import br.pucminas.eduka.backend.domain.acesso.PerfilUsuario;
import br.pucminas.eduka.backend.domain.acesso.Usuario;
import br.pucminas.eduka.backend.domain.auditoria.EntidadeAuditada;
import br.pucminas.eduka.backend.domain.auditoria.OperacaoAuditoria;
import br.pucminas.eduka.backend.domain.pessoas.PapelPessoa;
import br.pucminas.eduka.backend.domain.pessoas.Pessoa;
import br.pucminas.eduka.backend.domain.pessoas.PessoaPapel;
import br.pucminas.eduka.backend.dto.acesso.MasterInicialRequest;
import br.pucminas.eduka.backend.exception.acesso.LoginJaCadastradoException;
import br.pucminas.eduka.backend.repository.pessoas.PessoaPapelRepository;
import br.pucminas.eduka.backend.repository.pessoas.PessoaRepository;
import br.pucminas.eduka.backend.service.auditoria.AuditoriaService;

@ExtendWith(MockitoExtension.class)
class MasterInicialServiceTest {

    private static final UUID ESCOLA = UUID.randomUUID();

    @Mock
    private PessoaRepository pessoaRepository;
    @Mock
    private PessoaPapelRepository pessoaPapelRepository;
    @Mock
    private AcessoService acessoService;
    @Mock
    private AuditoriaService auditoriaService;

    private MasterInicialService service;

    @BeforeEach
    void setUp() {
        service = new MasterInicialService(pessoaRepository, pessoaPapelRepository, acessoService, auditoriaService);
    }

    @Test
    void criaPessoaNaEscolaEMandaOPrimeiroAcessoComoMaster() {
        UUID pessoaId = UUID.randomUUID();
        UUID usuarioId = UUID.randomUUID();
        when(pessoaRepository.save(any(Pessoa.class))).thenAnswer(inv -> {
            Pessoa pessoa = inv.getArgument(0);
            pessoa.setId(pessoaId);
            return pessoa;
        });
        when(acessoService.enviarAcessoInicial(any(Pessoa.class), eq(PerfilUsuario.MASTER)))
                .thenReturn(Usuario.builder().id(usuarioId).escolaId(ESCOLA).perfil(PerfilUsuario.MASTER).build());

        Usuario usuario = service.criar(ESCOLA,
                new MasterInicialRequest("  Bia Gestora  ", "52998224725", " bia@escola.com "));

        assertThat(usuario.getId()).isEqualTo(usuarioId);
        ArgumentCaptor<Pessoa> pessoa = ArgumentCaptor.forClass(Pessoa.class);
        verify(pessoaRepository).save(pessoa.capture());
        assertThat(pessoa.getValue().getEscolaId()).isEqualTo(ESCOLA);
        assertThat(pessoa.getValue().getNome()).isEqualTo("Bia Gestora");
        assertThat(pessoa.getValue().getCpf()).isEqualTo("52998224725");
        assertThat(pessoa.getValue().getEmail()).isEqualTo("bia@escola.com");

        ArgumentCaptor<PessoaPapel> papel = ArgumentCaptor.forClass(PessoaPapel.class);
        verify(pessoaPapelRepository).save(papel.capture());
        assertThat(papel.getValue().getPessoaId()).isEqualTo(pessoaId);
        assertThat(papel.getValue().getPapel()).isEqualTo(PapelPessoa.FUNCIONARIO);

        verify(auditoriaService).registrarNaEscola(OperacaoAuditoria.CRIAR, EntidadeAuditada.USUARIO, usuarioId,
                ESCOLA);
    }

    @Test
    void loginJaUsadoPropagaENaoAudita() {
        when(pessoaRepository.save(any(Pessoa.class))).thenAnswer(inv -> inv.getArgument(0));
        when(acessoService.enviarAcessoInicial(any(Pessoa.class), eq(PerfilUsuario.MASTER)))
                .thenThrow(new LoginJaCadastradoException("bia@escola.com"));

        assertThatThrownBy(() -> service.criar(ESCOLA,
                new MasterInicialRequest("Bia", "52998224725", "bia@escola.com")))
                .isInstanceOf(LoginJaCadastradoException.class);
        verify(auditoriaService, never()).registrarNaEscola(any(), any(), any(), any());
    }

    @Test
    void semMasterEhRejeitado() {
        assertThatThrownBy(() -> service.criar(ESCOLA, null)).isInstanceOf(IllegalArgumentException.class);
        verify(pessoaRepository, never()).save(any());
    }
}
