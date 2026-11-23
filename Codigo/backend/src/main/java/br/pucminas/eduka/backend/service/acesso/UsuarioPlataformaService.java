package br.pucminas.eduka.backend.service.acesso;

import java.util.Collection;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.pucminas.eduka.backend.domain.acesso.PerfilUsuario;
import br.pucminas.eduka.backend.domain.acesso.Usuario;
import br.pucminas.eduka.backend.domain.auditoria.EntidadeAuditada;
import br.pucminas.eduka.backend.domain.auditoria.OperacaoAuditoria;
import br.pucminas.eduka.backend.domain.escola.Escola;
import br.pucminas.eduka.backend.domain.pessoas.Pessoa;
import br.pucminas.eduka.backend.dto.acesso.UsuarioPlataformaResponse;
import br.pucminas.eduka.backend.dto.comum.PaginaResponse;
import br.pucminas.eduka.backend.dto.comum.Paginacao;
import br.pucminas.eduka.backend.exception.comum.RecursoNaoEncontradoException;
import br.pucminas.eduka.backend.repository.acesso.UsuarioRepository;
import br.pucminas.eduka.backend.repository.escola.EscolaRepository;
import br.pucminas.eduka.backend.repository.pessoas.PessoaRepository;
import br.pucminas.eduka.backend.service.auditoria.AuditoriaService;
import br.pucminas.eduka.backend.service.escola.LimiteUsuariosService;

/**
 * Logins da plataforma para o SUPER_ADMIN (S5-14, tela Usuarios). Ele ve os
 * de todas as escolas, mas so gere o do MASTER, que e quem ele cria (ver
 * {@code HierarquiaPerfis}): equipe, professores e alunos sao geridos pela
 * propria escola, nas telas dela, junto com o cadastro da pessoa.
 *
 * <p>
 * A busca e pelo login (o e-mail), que fica em claro; o nome da pessoa e
 * cifrado e nao entra nesta tela.
 * </p>
 */
@Service
public class UsuarioPlataformaService {

    private static final Sort ORDEM = Sort.by("login");

    private final UsuarioRepository usuarioRepository;
    private final EscolaRepository escolaRepository;
    private final PessoaRepository pessoaRepository;
    private final AcessoService acessoService;
    private final LimiteUsuariosService limiteUsuarios;
    private final AuditoriaService auditoriaService;

    public UsuarioPlataformaService(UsuarioRepository usuarioRepository, EscolaRepository escolaRepository,
            PessoaRepository pessoaRepository, AcessoService acessoService, LimiteUsuariosService limiteUsuarios,
            AuditoriaService auditoriaService) {
        this.usuarioRepository = usuarioRepository;
        this.escolaRepository = escolaRepository;
        this.pessoaRepository = pessoaRepository;
        this.acessoService = acessoService;
        this.limiteUsuarios = limiteUsuarios;
        this.auditoriaService = auditoriaService;
    }

    /**
     * Uma pagina de logins em ordem de login. Filtros opcionais: escola,
     * perfil, situacao (ativo ou nao) e trecho do login.
     */
    @Transactional(readOnly = true)
    public PaginaResponse<UsuarioPlataformaResponse> listar(UUID escolaId, PerfilUsuario perfil, Boolean ativo,
            String busca, Paginacao paginacao) {
        Page<Usuario> pagina = usuarioRepository.findAll(filtro(escolaId, perfil, ativo, busca),
                paginacao.comOrdem(ORDEM));
        Map<UUID, String> nomes = nomesDasEscolas(pagina.getContent().stream()
                .map(Usuario::getEscolaId).filter(Objects::nonNull).collect(Collectors.toSet()));
        return PaginaResponse.de(pagina, u -> UsuarioPlataformaResponse.de(u, nomes.get(u.getEscolaId())));
    }

    /** Inativa o login do MASTER: ele deixa de entrar e a vaga no limite da escola fica livre. */
    @Transactional
    public UsuarioPlataformaResponse inativar(UUID id) {
        return alterarAtivo(id, false);
    }

    /**
     * Reativa o login do MASTER.
     *
     * @throws br.pucminas.eduka.backend.exception.escola.LimiteUsuariosAtingidoException
     *         se a escola ja esta no limite de usuarios
     */
    @Transactional
    public UsuarioPlataformaResponse ativar(UUID id) {
        return alterarAtivo(id, true);
    }

    /**
     * Gera nova senha provisoria para o MASTER que ainda nao entrou e reenvia
     * o e-mail de primeiro acesso.
     *
     * @throws br.pucminas.eduka.backend.exception.acesso.AcessoJaAtivadoException se
     *         ele ja definiu a senha definitiva
     */
    @Transactional
    public UsuarioPlataformaResponse reenviarAcesso(UUID id) {
        Usuario usuario = buscarMaster(id);
        if (!usuario.isAtivo()) {
            throw new IllegalArgumentException("Quem esta inativo nao recebe acesso");
        }
        Pessoa pessoa = pessoaRepository.findByIdAndEscolaId(usuario.getPessoaId(), usuario.getEscolaId())
                .orElseThrow(UsuarioPlataformaService::naoEncontrado);
        Usuario atualizado = acessoService.enviarAcessoInicial(pessoa, PerfilUsuario.MASTER);
        auditoriaService.registrarNaEscola(OperacaoAuditoria.REENVIAR_ACESSO, EntidadeAuditada.USUARIO,
                atualizado.getId(), atualizado.getEscolaId());
        return resposta(atualizado);
    }

    private UsuarioPlataformaResponse alterarAtivo(UUID id, boolean ativo) {
        Usuario usuario = buscarMaster(id);
        if (usuario.isAtivo() != ativo) {
            if (ativo) {
                limiteUsuarios.exigirVaga(usuario.getEscolaId());
            }
            usuario.setAtivo(ativo);
            usuario = usuarioRepository.save(usuario);
            auditoriaService.registrarNaEscola(ativo ? OperacaoAuditoria.ATIVAR : OperacaoAuditoria.INATIVAR,
                    EntidadeAuditada.USUARIO, usuario.getId(), usuario.getEscolaId());
        }
        return resposta(usuario);
    }

    /**
     * So o MASTER e gerido aqui. Login de outro perfil responde como nao
     * encontrado, sem dizer que existe.
     */
    private Usuario buscarMaster(UUID id) {
        return usuarioRepository.findById(id)
                .filter(u -> u.getPerfil() == PerfilUsuario.MASTER)
                .orElseThrow(UsuarioPlataformaService::naoEncontrado);
    }

    private UsuarioPlataformaResponse resposta(Usuario usuario) {
        String escolaNome = usuario.getEscolaId() == null ? null
                : nomesDasEscolas(Set.of(usuario.getEscolaId())).get(usuario.getEscolaId());
        return UsuarioPlataformaResponse.de(usuario, escolaNome);
    }

    private Map<UUID, String> nomesDasEscolas(Collection<UUID> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return escolaRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(Escola::getId, Escola::getNome, (a, b) -> a));
    }

    private static Specification<Usuario> filtro(UUID escolaId, PerfilUsuario perfil, Boolean ativo,
            String busca) {
        Specification<Usuario> filtro = (raiz, consulta, cb) -> cb.conjunction();
        if (escolaId != null) {
            filtro = filtro.and((raiz, consulta, cb) -> cb.equal(raiz.get("escolaId"), escolaId));
        }
        if (perfil != null) {
            filtro = filtro.and((raiz, consulta, cb) -> cb.equal(raiz.get("perfil"), perfil));
        }
        if (ativo != null) {
            filtro = filtro.and((raiz, consulta, cb) -> cb.equal(raiz.get("ativo"), ativo));
        }
        if (busca != null && !busca.isBlank()) {
            String trecho = "%" + escaparLike(busca.trim().toLowerCase(Locale.ROOT)) + "%";
            filtro = filtro.and((raiz, consulta, cb) -> cb.like(raiz.get("login"), trecho, '\\'));
        }
        return filtro;
    }

    /** {@code %} e {@code _} digitados valem como texto, nao como curinga. */
    private static String escaparLike(String texto) {
        return texto.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private static RecursoNaoEncontradoException naoEncontrado() {
        return new RecursoNaoEncontradoException("Usuario nao encontrado");
    }
}
