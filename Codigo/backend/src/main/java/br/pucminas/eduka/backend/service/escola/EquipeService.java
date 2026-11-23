package br.pucminas.eduka.backend.service.escola;

import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.pucminas.eduka.backend.domain.acesso.PerfilUsuario;
import br.pucminas.eduka.backend.domain.acesso.Usuario;
import br.pucminas.eduka.backend.domain.auditoria.EntidadeAuditada;
import br.pucminas.eduka.backend.domain.auditoria.OperacaoAuditoria;
import br.pucminas.eduka.backend.domain.pessoas.PapelPessoa;
import br.pucminas.eduka.backend.domain.pessoas.Pessoa;
import br.pucminas.eduka.backend.domain.pessoas.PessoaPapel;
import br.pucminas.eduka.backend.dto.escola.MembroEquipeRequest;
import br.pucminas.eduka.backend.dto.escola.MembroEquipeResponse;
import br.pucminas.eduka.backend.dto.escola.MembroEquipeUpdateRequest;
import br.pucminas.eduka.backend.exception.comum.RecursoNaoEncontradoException;
import br.pucminas.eduka.backend.exception.pessoas.CpfJaCadastradoException;
import br.pucminas.eduka.backend.repository.acesso.UsuarioRepository;
import br.pucminas.eduka.backend.repository.pessoas.PessoaPapelRepository;
import br.pucminas.eduka.backend.repository.pessoas.PessoaRepository;
import br.pucminas.eduka.backend.security.IsolamentoEscola;
import br.pucminas.eduka.backend.service.acesso.AcessoService;
import br.pucminas.eduka.backend.service.auditoria.AuditoriaService;

/**
 * Equipe da escola: quem trabalha como SECRETARIA ou FINANCEIRO. So o MASTER
 * chega aqui (o controller restringe), sempre na escola do token (RF05,
 * RNF03).
 *
 * <p>
 * O login e criado pelo {@link AcessoService}, como o de professor e aluno: o
 * e-mail vira o login e o primeiro acesso vai para ele, com o CPF de senha
 * provisoria. O MASTER nunca define a senha de ninguem.
 * </p>
 *
 * <p>
 * Alguem de outra escola, ou com outro perfil, e indistinguivel de um
 * inexistente (404). Nome, CPF, e-mail e telefone vivem em {@link Pessoa},
 * cifrados: a busca por nome e a checagem de CPF repetido filtram primeiro
 * pela escola e comparam em memoria. Nao ha exclusao: inativar tira o login,
 * e o registro fica para o historico que aponta para ele.
 * </p>
 */
@Service
public class EquipeService {

    /** Perfis que o MASTER cadastra como equipe. */
    static final Set<PerfilUsuario> PERFIS_DA_EQUIPE = EnumSet.of(PerfilUsuario.SECRETARIA,
            PerfilUsuario.FINANCEIRO);

    private final UsuarioRepository usuarioRepository;
    private final PessoaRepository pessoaRepository;
    private final PessoaPapelRepository pessoaPapelRepository;
    private final AcessoService acessoService;
    private final IsolamentoEscola isolamentoEscola;
    private final AuditoriaService auditoriaService;
    private final LimiteUsuariosService limiteUsuarios;

    public EquipeService(UsuarioRepository usuarioRepository, PessoaRepository pessoaRepository,
            PessoaPapelRepository pessoaPapelRepository, AcessoService acessoService,
            IsolamentoEscola isolamentoEscola, AuditoriaService auditoriaService,
            LimiteUsuariosService limiteUsuarios) {
        this.usuarioRepository = usuarioRepository;
        this.pessoaRepository = pessoaRepository;
        this.pessoaPapelRepository = pessoaPapelRepository;
        this.acessoService = acessoService;
        this.isolamentoEscola = isolamentoEscola;
        this.auditoriaService = auditoriaService;
        this.limiteUsuarios = limiteUsuarios;
    }

    /**
     * Em ordem de nome.
     *
     * @param perfil nulo lista secretaria e financeiro
     * @param nome   filtro parcial, sem diferenciar caixa; nulo ou vazio lista todos
     * @param ativo  nulo lista ativos e inativos
     */
    public List<MembroEquipeResponse> listar(PerfilUsuario perfil, String nome, Boolean ativo) {
        UUID escolaId = isolamentoEscola.escolaIdAtual();
        Set<PerfilUsuario> perfis = perfil == null ? PERFIS_DA_EQUIPE : Set.of(exigirPerfilDaEquipe(perfil));
        List<Usuario> usuarios = usuarioRepository.findAllByEscolaIdAndPerfilIn(escolaId, perfis);
        Map<UUID, Pessoa> pessoasPorId = pessoasDe(usuarios, escolaId);

        String filtro = nome == null || nome.isBlank() ? null : nome.trim().toLowerCase(Locale.ROOT);
        return usuarios.stream()
                .filter(usuario -> ativo == null || usuario.isAtivo() == ativo)
                // Pessoa ausente (dado orfao) nao pode aparecer na listagem nem estourar NPE no filtro.
                .filter(usuario -> pessoasPorId.containsKey(usuario.getPessoaId()))
                .filter(usuario -> filtro == null || pessoasPorId.get(usuario.getPessoaId()).getNome()
                        .toLowerCase(Locale.ROOT).contains(filtro))
                .map(usuario -> MembroEquipeResponse.deResumo(usuario, pessoasPorId.get(usuario.getPessoaId())))
                .sorted(Comparator.comparing(MembroEquipeResponse::nome, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    public MembroEquipeResponse buscarPorId(UUID id) {
        UUID escolaId = isolamentoEscola.escolaIdAtual();
        Usuario usuario = buscarUsuario(id, escolaId);
        return MembroEquipeResponse.de(usuario, buscarPessoa(usuario, escolaId));
    }

    /** Cadastra a pessoa e cria o login, que manda o e-mail de primeiro acesso. */
    @Transactional
    public MembroEquipeResponse cadastrar(MembroEquipeRequest request) {
        UUID escolaId = isolamentoEscola.escolaIdAtual();
        PerfilUsuario perfil = exigirPerfilDaEquipe(request.perfil());
        exigirCpfLivre(request.cpf(), escolaId, null);

        Pessoa pessoa = pessoaRepository.save(Pessoa.builder()
                .escolaId(escolaId)
                .nome(request.nome().trim())
                .cpf(request.cpf())
                .email(request.email().trim())
                .telefone(request.telefone())
                .ativo(true)
                .build());
        pessoaPapelRepository
                .save(PessoaPapel.builder().pessoaId(pessoa.getId()).papel(PapelPessoa.FUNCIONARIO).build());

        Usuario usuario = acessoService.enviarAcessoInicial(pessoa, perfil);
        auditar(OperacaoAuditoria.CRIAR, usuario);
        return MembroEquipeResponse.de(usuario, pessoa);
    }

    /** Substitui os dados da pessoa; trocar o e-mail troca o login junto. */
    @Transactional
    public MembroEquipeResponse atualizar(UUID id, MembroEquipeUpdateRequest request) {
        UUID escolaId = isolamentoEscola.escolaIdAtual();
        Usuario usuario = buscarUsuario(id, escolaId);
        Pessoa pessoa = buscarPessoa(usuario, escolaId);
        exigirCpfLivre(request.cpf(), escolaId, usuario.getId());

        pessoa.setNome(request.nome().trim());
        pessoa.setCpf(request.cpf());
        pessoa.setEmail(request.email().trim());
        pessoa.setTelefone(request.telefone());
        Pessoa salva = pessoaRepository.save(pessoa);
        acessoService.sincronizarLogin(salva);
        auditar(OperacaoAuditoria.EDITAR, usuario);

        return MembroEquipeResponse.de(usuario, salva);
    }

    /**
     * Gera nova senha provisoria e reenvia o e-mail.
     *
     * @throws br.pucminas.eduka.backend.exception.acesso.AcessoJaAtivadoException se a
     *         pessoa ja definiu a senha definitiva
     */
    @Transactional
    public void reenviarAcesso(UUID id) {
        UUID escolaId = isolamentoEscola.escolaIdAtual();
        Usuario usuario = buscarUsuario(id, escolaId);
        if (!usuario.isAtivo()) {
            throw new IllegalArgumentException("Quem esta inativo nao recebe acesso");
        }
        acessoService.enviarAcessoInicial(buscarPessoa(usuario, escolaId), usuario.getPerfil());
        auditar(OperacaoAuditoria.REENVIAR_ACESSO, usuario);
    }

    @Transactional
    public MembroEquipeResponse inativar(UUID id) {
        return alterarAtivo(id, false);
    }

    @Transactional
    public MembroEquipeResponse ativar(UUID id) {
        return alterarAtivo(id, true);
    }

    private MembroEquipeResponse alterarAtivo(UUID id, boolean ativo) {
        UUID escolaId = isolamentoEscola.escolaIdAtual();
        Usuario usuario = buscarUsuario(id, escolaId);
        Pessoa pessoa = buscarPessoa(usuario, escolaId);
        if (usuario.isAtivo() != ativo) {
            if (ativo) {
                limiteUsuarios.exigirVaga(escolaId);
            }
            usuario.setAtivo(ativo);
            usuario = usuarioRepository.save(usuario);
            auditar(ativo ? OperacaoAuditoria.ATIVAR : OperacaoAuditoria.INATIVAR, usuario);
        }
        return MembroEquipeResponse.de(usuario, pessoa);
    }

    private void auditar(OperacaoAuditoria operacao, Usuario usuario) {
        auditoriaService.registrar(operacao, EntidadeAuditada.USUARIO, usuario.getId());
    }

    private static PerfilUsuario exigirPerfilDaEquipe(PerfilUsuario perfil) {
        if (!PERFIS_DA_EQUIPE.contains(perfil)) {
            throw new IllegalArgumentException("A equipe tem so SECRETARIA e FINANCEIRO");
        }
        return perfil;
    }

    /**
     * CPF cifrado com IV aleatorio nao pode ter UNIQUE no banco: decifra a
     * equipe da escola e compara aqui. A mesma pessoa nao fica nos dois
     * perfis. {@code ignorarId} e a propria pessoa, na edicao.
     */
    private void exigirCpfLivre(String cpf, UUID escolaId, UUID ignorarId) {
        List<Usuario> outros = usuarioRepository.findAllByEscolaIdAndPerfilIn(escolaId, PERFIS_DA_EQUIPE).stream()
                .filter(usuario -> !usuario.getId().equals(ignorarId))
                .toList();
        boolean repetido = pessoasDe(outros, escolaId).values().stream()
                .anyMatch(pessoa -> cpf.equals(pessoa.getCpf()));
        if (repetido) {
            throw new CpfJaCadastradoException("Ja existe alguem da equipe com este CPF nesta escola");
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
        return usuarioRepository.findByIdAndEscolaIdAndPerfilIn(id, escolaId, PERFIS_DA_EQUIPE)
                .orElseThrow(EquipeService::naoEncontrado);
    }

    private Pessoa buscarPessoa(Usuario usuario, UUID escolaId) {
        return pessoaRepository.findByIdAndEscolaId(usuario.getPessoaId(), escolaId)
                .orElseThrow(EquipeService::naoEncontrado);
    }

    private static RecursoNaoEncontradoException naoEncontrado() {
        return new RecursoNaoEncontradoException("Pessoa da equipe nao encontrada");
    }
}
