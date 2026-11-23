package br.pucminas.eduka.backend.controller.escola;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import br.pucminas.eduka.backend.domain.acesso.PerfilUsuario;
import br.pucminas.eduka.backend.domain.acesso.Usuario;
import br.pucminas.eduka.backend.domain.pessoas.Pessoa;
import br.pucminas.eduka.backend.exception.ApiExceptionHandler;
import br.pucminas.eduka.backend.exception.acesso.LoginJaCadastradoException;
import br.pucminas.eduka.backend.repository.acesso.UsuarioRepository;
import br.pucminas.eduka.backend.repository.pessoas.PessoaPapelRepository;
import br.pucminas.eduka.backend.repository.pessoas.PessoaRepository;
import br.pucminas.eduka.backend.security.IsolamentoEscola;
import br.pucminas.eduka.backend.security.JwtConfig;
import br.pucminas.eduka.backend.security.JwtService;
import br.pucminas.eduka.backend.service.acesso.AcessoService;
import br.pucminas.eduka.backend.service.auditoria.AuditoriaService;
import br.pucminas.eduka.backend.service.escola.EquipeService;
import br.pucminas.eduka.backend.service.escola.LimiteUsuariosService;

/**
 * Contrato de seguranca de {@code /usuarios/equipe}, ponta a ponta: filter
 * chain, tokens, {@code @PreAuthorize}, validacao e servico reais; so os
 * repositorios e o envio do acesso sao mock.
 */
@WebMvcTest(EquipeController.class)
@Import({ JwtConfig.class, JwtService.class, IsolamentoEscola.class, ApiExceptionHandler.class,
        EquipeService.class })
class EquipeControllerSecurityTest {

    private static final UUID ESCOLA_A = UUID.randomUUID();
    private static final String CORPO_VALIDO = "{\"perfil\":\"FINANCEIRO\",\"nome\":\"Ana\","
            + "\"cpf\":\"52998224725\",\"email\":\"ana@escola.com\"}";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @MockitoBean
    private UsuarioRepository usuarioRepository;
    @MockitoBean
    private AuditoriaService auditoriaService;
    @MockitoBean
    private PessoaRepository pessoaRepository;
    @MockitoBean
    private PessoaPapelRepository pessoaPapelRepository;
    @MockitoBean
    private AcessoService acessoService;
    @MockitoBean
    private LimiteUsuariosService limiteUsuarios;

    private String token(String perfil) {
        return jwtService.generateAccessToken(UUID.randomUUID(), perfil, "SUPER_ADMIN".equals(perfil) ? null
                : ESCOLA_A);
    }

    private static MockHttpServletRequestBuilder com(MockHttpServletRequestBuilder request, String token) {
        return request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, String token,
            String corpo) {
        return com(request, token).contentType(MediaType.APPLICATION_JSON).content(corpo);
    }

    private void stubCadastro() {
        when(usuarioRepository.findAllByEscolaIdAndPerfilIn(eq(ESCOLA_A), anyCollection())).thenReturn(List.of());
        when(pessoaRepository.save(any(Pessoa.class))).thenAnswer(inv -> {
            Pessoa pessoa = inv.getArgument(0);
            pessoa.setId(UUID.randomUUID());
            return pessoa;
        });
    }

    @Test
    void semTokenResponde401() throws Exception {
        mockMvc.perform(get("/usuarios/equipe")).andExpect(status().isUnauthorized());
    }

    @Test
    void soOMasterGerenciaAEquipe() throws Exception {
        for (String perfil : List.of("SECRETARIA", "FINANCEIRO", "PROFESSOR", "ALUNO", "SUPER_ADMIN")) {
            mockMvc.perform(com(get("/usuarios/equipe"), token(perfil))).andExpect(status().isForbidden());
            mockMvc.perform(json(post("/usuarios/equipe"), token(perfil), CORPO_VALIDO))
                    .andExpect(status().isForbidden());
        }
        verify(acessoService, never()).enviarAcessoInicial(any(), any());
    }

    @Test
    void masterCadastraFinanceiroEOAcessoSaiComEssePerfil() throws Exception {
        stubCadastro();
        when(acessoService.enviarAcessoInicial(any(Pessoa.class), eq(PerfilUsuario.FINANCEIRO)))
                .thenAnswer(inv -> Usuario.builder().id(UUID.randomUUID()).escolaId(ESCOLA_A)
                        .pessoaId(((Pessoa) inv.getArgument(0)).getId()).login("ana@escola.com")
                        .perfil(PerfilUsuario.FINANCEIRO).ativo(true).senhaProvisoria(true).build());

        mockMvc.perform(json(post("/usuarios/equipe"), token("MASTER"), CORPO_VALIDO))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.perfil").value("FINANCEIRO"))
                .andExpect(jsonPath("$.login").value("ana@escola.com"))
                .andExpect(jsonPath("$.aguardandoPrimeiroAcesso").value(true));
    }

    @Test
    void corpoInvalidoOuPerfilForaDaEquipeResponde400() throws Exception {
        List<String> corpos = List.of(
                "{\"nome\":\"Ana\",\"cpf\":\"52998224725\",\"email\":\"ana@escola.com\"}",
                "{\"perfil\":\"SECRETARIA\",\"cpf\":\"52998224725\",\"email\":\"ana@escola.com\"}",
                "{\"perfil\":\"SECRETARIA\",\"nome\":\"Ana\",\"cpf\":\"123\",\"email\":\"ana@escola.com\"}",
                "{\"perfil\":\"SECRETARIA\",\"nome\":\"Ana\",\"cpf\":\"52998224725\",\"email\":\"nao-e-email\"}",
                "{\"perfil\":\"PROFESSOR\",\"nome\":\"Ana\",\"cpf\":\"52998224725\",\"email\":\"ana@escola.com\"}",
                "{\"perfil\":\"MASTER\",\"nome\":\"Ana\",\"cpf\":\"52998224725\",\"email\":\"ana@escola.com\"}");
        for (String corpo : corpos) {
            mockMvc.perform(json(post("/usuarios/equipe"), token("MASTER"), corpo)).andExpect(status().isBadRequest());
        }
        verify(acessoService, never()).enviarAcessoInicial(any(), any());
    }

    @Test
    void emailJaUsadoComoLoginResponde409() throws Exception {
        stubCadastro();
        when(acessoService.enviarAcessoInicial(any(Pessoa.class), any()))
                .thenThrow(new LoginJaCadastradoException("ana@escola.com"));

        mockMvc.perform(json(post("/usuarios/equipe"), token("MASTER"), CORPO_VALIDO))
                .andExpect(status().isConflict());
    }

    @Test
    void alguemDeOutraEscolaResponde404EmTodaOperacao() throws Exception {
        UUID id = UUID.randomUUID();
        when(usuarioRepository.findByIdAndEscolaIdAndPerfilIn(eq(id), eq(ESCOLA_A), anyCollection()))
                .thenReturn(Optional.empty());
        String master = token("MASTER");
        String edicao = "{\"nome\":\"Ana\",\"cpf\":\"52998224725\",\"email\":\"ana@escola.com\"}";

        mockMvc.perform(com(get("/usuarios/equipe/" + id), master)).andExpect(status().isNotFound());
        mockMvc.perform(json(put("/usuarios/equipe/" + id), master, edicao)).andExpect(status().isNotFound());
        mockMvc.perform(com(patch("/usuarios/equipe/" + id + "/inativar"), master)).andExpect(status().isNotFound());
        mockMvc.perform(com(patch("/usuarios/equipe/" + id + "/ativar"), master)).andExpect(status().isNotFound());
        mockMvc.perform(com(post("/usuarios/equipe/" + id + "/primeiro-acesso"), master))
                .andExpect(status().isNotFound());
    }
}
