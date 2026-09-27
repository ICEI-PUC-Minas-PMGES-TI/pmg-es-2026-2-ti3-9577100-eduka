package br.pucminas.eduka.backend.service;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.pucminas.eduka.backend.domain.PapelPessoa;
import br.pucminas.eduka.backend.domain.PerfilUsuario;
import br.pucminas.eduka.backend.domain.Pessoa;
import br.pucminas.eduka.backend.domain.PessoaPapel;
import br.pucminas.eduka.backend.domain.Usuario;
import br.pucminas.eduka.backend.dto.SecretariaRequest;
import br.pucminas.eduka.backend.dto.SecretariaResponse;
import br.pucminas.eduka.backend.dto.SecretariaUpdateRequest;
import br.pucminas.eduka.backend.exception.CpfJaCadastradoException;
import br.pucminas.eduka.backend.exception.LoginJaCadastradoException;
import br.pucminas.eduka.backend.exception.RecursoNaoEncontradoException;
import br.pucminas.eduka.backend.exception.SenhaInvalidaException;
import br.pucminas.eduka.backend.repository.PessoaPapelRepository;
import br.pucminas.eduka.backend.repository.PessoaRepository;
import br.pucminas.eduka.backend.repository.UsuarioRepository;
import br.pucminas.eduka.backend.security.IsolamentoEscola;

/**
 * Cadastro de secretarias com isolamento por escola (RF05, RNF03), espelhando
 * {@link ProfessorService} quase 1:1.
 *
 * <p>
 * A escola nunca e parametro: sai de {@link IsolamentoEscola#escolaIdAtual()}
 * e entra em toda consulta como filtro. Uma secretaria de outra escola (ou um
 * usuario que nao seja SECRETARIA) e, para o usuario atual, indistinguivel de
 * um inexistente (404 nos dois casos).
 * </p>
 *
 * <p>
 * Nome, CPF, e-mail e telefone vivem em {@link Pessoa}, cifrados. Por isso a
 * busca por nome e a checagem de CPF repetido filtram primeiro por
 * {@code escola_id} e so comparam em memoria, depois de decifrar.
 * </p>
 *
 * <p>
 * O perfil {@code SECRETARIA} e sempre fixado aqui, nunca vem do request: nao
 * ha checagem de {@code HierarquiaPerfis} porque o controller (camada HTTP)
 * ja restringe todo este recurso ao MASTER.
 * </p>
 *
 * <p>
 * Nao ha exclusao fisica: a secretaria e inativada (flag em {@link Usuario},
 * nao em {@link Pessoa}), para nao quebrar historico que venha a apontar para
 * ela.
 * </p>
 */
@Service
public class SecretariaService {

    private final UsuarioRepository usuarioRepository;
    private final PessoaRepository pessoaRepository;
    private final PessoaPapelRepository pessoaPapelRepository;
    private final PasswordEncoder passwordEncoder;
    private final IsolamentoEscola isolamentoEscola;

    public SecretariaService(UsuarioRepository usuarioRepository, PessoaRepository pessoaRepository,
            PessoaPapelRepository pessoaPapelRepository, PasswordEncoder passwordEncoder,
            IsolamentoEscola isolamentoEscola) {
        this.usuarioRepository = usuarioRepository;
        this.pessoaRepository = pessoaRepository;
        this.pessoaPapelRepository = pessoaPapelRepository;
        this.passwordEncoder = passwordEncoder;
        this.isolamentoEscola = isolamentoEscola;
    }

    /**
     * @param nome  filtro parcial, sem diferenciar caixa; nulo ou vazio lista todos
     * @param ativo nulo lista ativos e inativos
     */
    public List<SecretariaResponse> listar(String nome, Boolean ativo) {
        UUID escolaId = isolamentoEscola.escolaIdAtual();
        List<Usuario> secretarias = usuarioRepository.findAllByEscolaIdAndPerfil(escolaId, PerfilUsuario.SECRETARIA);
        Map<UUID, Pessoa> pessoasPorId = pessoasDe(secretarias, escolaId);

        String filtro = nome == null || nome.isBlank() ? null : nome.trim().toLowerCase(Locale.ROOT);
        return secretarias.stream()
                .filter(usuario -> ativo == null || usuario.isAtivo() == ativo)
                .filter(usuario -> filtro == null || pessoasPorId.get(usuario.getPessoaId()).getNome()
                        .toLowerCase(Locale.ROOT).contains(filtro))
                .map(usuario -> SecretariaResponse.deResumo(usuario, pessoasPorId.get(usuario.getPessoaId())))
                .toList();
    }

    public SecretariaResponse buscarPorId(UUID id) {
        UUID escolaId = isolamentoEscola.escolaIdAtual();
        Usuario usuario = buscarUsuario(id, escolaId);
        return SecretariaResponse.de(usuario, buscarPessoa(usuario, escolaId));
    }

    /**
     * Cadastra a secretaria na escola do usuario autenticado. O request nao
     * carrega escola nem perfil: mesmo que carregasse, seriam ignorados.
     */
    @Transactional
    public SecretariaResponse cadastrar(SecretariaRequest request) {
        UUID escolaId = isolamentoEscola.escolaIdAtual();
        exigirCpfLivre(request.cpf(), escolaId, null);

        String login = UsuarioService.normalizarLogin(request.login());
        if (usuarioRepository.existsByLogin(login)) {
            throw new LoginJaCadastradoException(login);
        }
        validarSenha(request.senha());

        Pessoa pessoa = pessoaRepository.save(Pessoa.builder()
                .escolaId(escolaId)
                .nome(request.nome().trim())
                .cpf(request.cpf())
                .email(request.email())
                .telefone(request.telefone())
                .ativo(true)
                .build());
        pessoaPapelRepository
                .save(PessoaPapel.builder().pessoaId(pessoa.getId()).papel(PapelPessoa.FUNCIONARIO).build());

        Usuario usuario = usuarioRepository.save(Usuario.builder()
                .escolaId(escolaId)
                .pessoaId(pessoa.getId())
                .login(login)
                .senhaHash(passwordEncoder.encode(request.senha()))
                .perfil(PerfilUsuario.SECRETARIA)
                .ativo(true)
                .build());
        return SecretariaResponse.de(usuario, pessoa);
    }

    @Transactional
    public SecretariaResponse atualizar(UUID id, SecretariaUpdateRequest request) {
        UUID escolaId = isolamentoEscola.escolaIdAtual();
        Usuario usuario = buscarUsuario(id, escolaId);
        Pessoa pessoa = buscarPessoa(usuario, escolaId);
        exigirCpfLivre(request.cpf(), escolaId, usuario.getId());

        pessoa.setNome(request.nome().trim());
        pessoa.setCpf(request.cpf());
        pessoa.setEmail(request.email());
        pessoa.setTelefone(request.telefone());

        return SecretariaResponse.de(usuario, pessoaRepository.save(pessoa));
    }

    @Transactional
    public void inativar(UUID id) {
        alterarAtivo(id, false);
    }

    @Transactional
    public SecretariaResponse ativar(UUID id) {
        return alterarAtivo(id, true);
    }

    private SecretariaResponse alterarAtivo(UUID id, boolean ativo) {
        UUID escolaId = isolamentoEscola.escolaIdAtual();
        Usuario usuario = buscarUsuario(id, escolaId);
        Pessoa pessoa = buscarPessoa(usuario, escolaId);
        if (usuario.isAtivo() != ativo) {
            usuario.setAtivo(ativo);
            usuario = usuarioRepository.save(usuario);
        }
        return SecretariaResponse.de(usuario, pessoa);
    }

    /**
     * CPF cifrado com IV aleatorio nao pode ter UNIQUE no banco: decifra as
     * secretarias da escola e compara aqui. {@code ignorarId} e a propria
     * secretaria, na edicao.
     */
    private void exigirCpfLivre(String cpf, UUID escolaId, UUID ignorarId) {
        List<Usuario> outras = usuarioRepository
                .findAllByEscolaIdAndPerfil(escolaId, PerfilUsuario.SECRETARIA).stream()
                .filter(usuario -> !usuario.getId().equals(ignorarId))
                .toList();
        boolean repetido = pessoasDe(outras, escolaId).values().stream()
                .anyMatch(pessoa -> cpf.equals(pessoa.getCpf()));
        if (repetido) {
            throw new CpfJaCadastradoException("Ja existe secretaria com este CPF nesta escola");
        }
    }

    /**
     * Mesmas regras de {@link UsuarioService}: comprimento minimo em
     * caracteres, maximo em bytes UTF-8 (limite do BCrypt).
     */
    private void validarSenha(String senha) {
        if (senha == null || senha.length() < UsuarioService.SENHA_TAMANHO_MINIMO) {
            throw new SenhaInvalidaException(
                    "Senha deve ter ao menos %d caracteres".formatted(UsuarioService.SENHA_TAMANHO_MINIMO));
        }
        int bytes = senha.getBytes(StandardCharsets.UTF_8).length;
        if (bytes > UsuarioService.SENHA_TAMANHO_MAXIMO_BYTES) {
            throw new SenhaInvalidaException(
                    "Senha nao pode passar de %d bytes".formatted(UsuarioService.SENHA_TAMANHO_MAXIMO_BYTES));
        }
    }

    private Map<UUID, Pessoa> pessoasDe(List<Usuario> usuarios, UUID escolaId) {
        if (usuarios.isEmpty()) {
            return Map.of();
        }
        return pessoaRepository
                .findAllByIdInAndEscolaId(usuarios.stream().map(Usuario::getPessoaId).toList(), escolaId)
                .stream().collect(Collectors.toMap(Pessoa::getId, pessoa -> pessoa));
    }

    private Usuario buscarUsuario(UUID id, UUID escolaId) {
        return usuarioRepository.findByIdAndEscolaIdAndPerfil(id, escolaId, PerfilUsuario.SECRETARIA)
                .orElseThrow(SecretariaService::naoEncontrada);
    }

    private Pessoa buscarPessoa(Usuario usuario, UUID escolaId) {
        return pessoaRepository.findByIdAndEscolaId(usuario.getPessoaId(), escolaId)
                .orElseThrow(SecretariaService::naoEncontrada);
    }

    private static RecursoNaoEncontradoException naoEncontrada() {
        return new RecursoNaoEncontradoException("Secretaria nao encontrada");
    }
}
