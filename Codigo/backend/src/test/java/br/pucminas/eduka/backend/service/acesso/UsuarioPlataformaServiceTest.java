package br.pucminas.eduka.backend.service.acesso;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.util.ReflectionTestUtils;

import br.pucminas.eduka.backend.domain.acesso.PerfilUsuario;
import br.pucminas.eduka.backend.domain.acesso.Usuario;
import br.pucminas.eduka.backend.domain.auditoria.EntidadeAuditada;
import br.pucminas.eduka.backend.domain.auditoria.OperacaoAuditoria;
import br.pucminas.eduka.backend.domain.escola.Escola;
import br.pucminas.eduka.backend.domain.escola.PlanoEscola;
import br.pucminas.eduka.backend.domain.pessoas.Pessoa;
import br.pucminas.eduka.backend.dto.acesso.UsuarioPlataformaResponse;
import br.pucminas.eduka.backend.dto.comum.PaginaResponse;
import br.pucminas.eduka.backend.dto.comum.Paginacao;
import br.pucminas.eduka.backend.exception.comum.RecursoNaoEncontradoException;
import br.pucminas.eduka.backend.exception.escola.LimiteUsuariosAtingidoException;
import br.pucminas.eduka.backend.repository.acesso.UsuarioRepository;
import br.pucminas.eduka.backend.repository.escola.EscolaRepository;
import br.pucminas.eduka.backend.repository.pessoas.PessoaRepository;
import br.pucminas.eduka.backend.service.auditoria.AuditoriaService;
import br.pucminas.eduka.backend.service.escola.LimiteUsuariosService;

@ExtendWith(MockitoExtension.class)
class UsuarioPlataformaServiceTest {

    private static final UUID ESCOLA = UUID.randomUUID();

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private EscolaRepository escolaRepository;

    @Mock
    private PessoaRepository pessoaRepository;

    @Mock
    private AcessoService acessoService;

    @Mock
    private LimiteUsuariosService limiteUsuarios;

    @Mock
    private AuditoriaService auditoriaService;

    private UsuarioPlataformaService service;

    @BeforeEach
    void setUp() {
        service = new UsuarioPlataformaService(usuarioRepository, escolaRepository, pessoaRepository, acessoService,
                limiteUsuarios, auditoriaService);
    }

    private static Usuario usuario(PerfilUsuario perfil, UUID escolaId, boolean ativo) {
        return Usuario.builder().id(UUID.randomUUID()).escolaId(escolaId).pessoaId(UUID.randomUUID())
                .login(perfil.name().toLowerCase(Locale.ROOT) + "@escola.com").perfil(perfil).ativo(ativo).build();
    }

    private static Escola escola() {
        Escola escola = Escola.cadastrar("Colegio Horizonte", null, null, null, null, PlanoEscola.values()[0], true);
        ReflectionTestUtils.setField(escola, "id", ESCOLA);
        return escola;
    }

    @Test
    @SuppressWarnings("unchecked")
    void listaComONomeDaEscolaESoOMasterEGerenciavel() {
        Usuario master = usuario(PerfilUsuario.MASTER, ESCOLA, true);
        Usuario aluno = usuario(PerfilUsuario.ALUNO, ESCOLA, true);
        Usuario superAdmin = usuario(PerfilUsuario.SUPER_ADMIN, null, true);
        when(usuarioRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(aluno, master, superAdmin), PageRequest.of(0, 20), 3));
        when(escolaRepository.findAllById(anyCollection())).thenReturn(List.of(escola()));

        PaginaResponse<UsuarioPlataformaResponse> pagina = service.listar(null, null, null, " Gestor ",
                new Paginacao(0, 20));

        assertThat(pagina.totalElementos()).isEqualTo(3);
        assertThat(pagina.conteudo()).extracting(UsuarioPlataformaResponse::escolaNome)
                .containsExactly("Colegio Horizonte", "Colegio Horizonte", null);
        assertThat(pagina.conteudo()).extracting(UsuarioPlataformaResponse::gerenciavel)
                .containsExactly(false, true, false);
    }

    @Test
    void inativarMasterLiberaAVagaEAudita() {
        Usuario master = usuario(PerfilUsuario.MASTER, ESCOLA, true);
        when(usuarioRepository.findById(master.getId())).thenReturn(Optional.of(master));
        when(usuarioRepository.save(master)).thenReturn(master);

        assertThat(service.inativar(master.getId()).ativo()).isFalse();
        verify(auditoriaService).registrarNaEscola(OperacaoAuditoria.INATIVAR, EntidadeAuditada.USUARIO,
                master.getId(), ESCOLA);
        verifyNoInteractions(limiteUsuarios);
    }

    @Test
    void ativarMasterRespeitaOLimite() {
        Usuario master = usuario(PerfilUsuario.MASTER, ESCOLA, false);
        when(usuarioRepository.findById(master.getId())).thenReturn(Optional.of(master));
        doThrow(new LimiteUsuariosAtingidoException(2)).when(limiteUsuarios).exigirVaga(ESCOLA);

        assertThatThrownBy(() -> service.ativar(master.getId())).isInstanceOf(LimiteUsuariosAtingidoException.class);
        assertThat(master.isAtivo()).isFalse();
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    void loginQueNaoEDeMasterEGeridoPelaEscolaERespondeComoInexistente() {
        for (PerfilUsuario perfil : List.of(PerfilUsuario.SECRETARIA, PerfilUsuario.ALUNO, PerfilUsuario.SUPER_ADMIN)) {
            Usuario outro = usuario(perfil, perfil == PerfilUsuario.SUPER_ADMIN ? null : ESCOLA, true);
            when(usuarioRepository.findById(outro.getId())).thenReturn(Optional.of(outro));

            assertThatThrownBy(() -> service.inativar(outro.getId())).isInstanceOf(RecursoNaoEncontradoException.class);
            assertThatThrownBy(() -> service.reenviarAcesso(outro.getId()))
                    .isInstanceOf(RecursoNaoEncontradoException.class);
            assertThat(outro.isAtivo()).isTrue();
        }
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    void reenviarAcessoBuscaAPessoaNaEscolaDoMaster() {
        Usuario master = usuario(PerfilUsuario.MASTER, ESCOLA, true);
        Pessoa pessoa = Pessoa.builder().id(master.getPessoaId()).escolaId(ESCOLA).build();
        when(usuarioRepository.findById(master.getId())).thenReturn(Optional.of(master));
        when(pessoaRepository.findByIdAndEscolaId(master.getPessoaId(), ESCOLA)).thenReturn(Optional.of(pessoa));
        when(acessoService.enviarAcessoInicial(pessoa, PerfilUsuario.MASTER)).thenReturn(master);

        service.reenviarAcesso(master.getId());

        verify(auditoriaService).registrarNaEscola(OperacaoAuditoria.REENVIAR_ACESSO, EntidadeAuditada.USUARIO,
                master.getId(), ESCOLA);
    }

    @Test
    void masterInativoNaoRecebeAcesso() {
        Usuario master = usuario(PerfilUsuario.MASTER, ESCOLA, false);
        when(usuarioRepository.findById(master.getId())).thenReturn(Optional.of(master));

        assertThatThrownBy(() -> service.reenviarAcesso(master.getId())).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(acessoService);
    }
}
