package br.pucminas.eduka.backend.service;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.pucminas.eduka.backend.domain.EntidadeAuditada;
import br.pucminas.eduka.backend.domain.OperacaoAuditoria;
import br.pucminas.eduka.backend.domain.PapelPessoa;
import br.pucminas.eduka.backend.domain.PerfilUsuario;
import br.pucminas.eduka.backend.domain.Pessoa;
import br.pucminas.eduka.backend.domain.PessoaPapel;
import br.pucminas.eduka.backend.domain.Professor;
import br.pucminas.eduka.backend.domain.Usuario;
import br.pucminas.eduka.backend.dto.Ordenacao;
import br.pucminas.eduka.backend.dto.PaginaResponse;
import br.pucminas.eduka.backend.dto.Paginacao;
import br.pucminas.eduka.backend.dto.ProfessorRequest;
import br.pucminas.eduka.backend.dto.ProfessorResponse;
import br.pucminas.eduka.backend.exception.CpfJaCadastradoException;
import br.pucminas.eduka.backend.exception.ProfessorComHistoricoException;
import br.pucminas.eduka.backend.exception.RecursoNaoEncontradoException;
import br.pucminas.eduka.backend.repository.PessoaPapelRepository;
import br.pucminas.eduka.backend.repository.PessoaRepository;
import br.pucminas.eduka.backend.repository.ProfessorRepository;
import br.pucminas.eduka.backend.security.IsolamentoEscola;

/**
 * Cadastro de professores com isolamento por escola (RF05, RNF03).
 *
 * <p>
 * A escola nunca e parametro: sai de {@link IsolamentoEscola#escolaIdAtual()}
 * e entra em toda consulta como filtro. Um professor de outra escola e, para
 * o usuario atual, indistinguivel de um inexistente (404 nos dois casos).
 * </p>
 *
 * <p>
 * Nome, CPF, e-mail e telefone vivem em {@link Pessoa}, cifrados. Por isso a
 * busca por nome e a checagem de CPF repetido filtram primeiro por
 * {@code escola_id} e so comparam em memoria, depois de decifrar.
 * </p>
 *
 * <p>
 * Afastamento temporario (licenca) e inativacao: o registro e o login ficam,
 * so desligados. Saida da escola e exclusao fisica, recusada quando ja ha
 * historico (turmas, avaliacoes) apontando para o professor.
 * </p>
 *
 * <p>
 * O login do professor e criado junto com o cadastro, via
 * {@link AcessoService}: login = e-mail, senha provisoria = CPF, enviada por
 * e-mail. Inativar o professor inativa o login.
 * </p>
 */
@Service
public class ProfessorService {

    private final ProfessorRepository professorRepository;
    private final PessoaRepository pessoaRepository;
    private final PessoaPapelRepository pessoaPapelRepository;
    private final IsolamentoEscola isolamentoEscola;
    private final AcessoService acessoService;
    private final AuditoriaService auditoriaService;

    public ProfessorService(ProfessorRepository professorRepository, PessoaRepository pessoaRepository,
            PessoaPapelRepository pessoaPapelRepository, IsolamentoEscola isolamentoEscola,
            AcessoService acessoService, AuditoriaService auditoriaService) {
        this.professorRepository = professorRepository;
        this.pessoaRepository = pessoaRepository;
        this.pessoaPapelRepository = pessoaPapelRepository;
        this.isolamentoEscola = isolamentoEscola;
        this.acessoService = acessoService;
        this.auditoriaService = auditoriaService;
    }

    /**
     * @param nome  filtro parcial, sem diferenciar caixa; nulo ou vazio lista todos
     * @param ativo nulo lista ativos e inativos
     */
    /**
     * Pagina dos professores da escola, com os mesmos filtros de
     * {@link #listar}. O nome e cifrado, entao filtro e ordem por nome sao
     * feitos em memoria, e so a pagina vai na resposta.
     */
    public PaginaResponse<ProfessorResponse> listarPaginado(String nome, Boolean ativo, Paginacao paginacao,
            Ordenacao.Professores ordenarPor, Direction direcao) {
        Comparator<ProfessorResponse> campo = switch (ordenarPor) {
            case NOME -> Comparator.comparing(ProfessorResponse::nome, Paginacao.ORDEM_DE_NOME);
            case DATA_ADMISSAO -> Comparator.comparing(ProfessorResponse::dataAdmissao,
                    Comparator.nullsLast(Comparator.naturalOrder()));
        };
        List<ProfessorResponse> ordenados = listar(nome, ativo).stream()
                .sorted(Ordenacao.emMemoria(campo, Ordenacao.ou(direcao, Direction.ASC),
                        Comparator.comparing(ProfessorResponse::id)))
                .toList();
        return PaginaResponse.recortar(ordenados, paginacao);
    }

    public List<ProfessorResponse> listar(String nome, Boolean ativo) {
        UUID escolaId = isolamentoEscola.escolaIdAtual();
        List<Professor> professores = professorRepository.findAllByEscolaId(escolaId);
        Map<UUID, Pessoa> pessoasPorId = pessoasDe(professores, escolaId);

        String filtro = nome == null || nome.isBlank() ? null : nome.trim().toLowerCase(Locale.ROOT);
        return professores.stream()
                .filter(professor -> ativo == null || professor.isAtivo() == ativo)
                .filter(professor -> filtro == null || pessoasPorId.get(professor.getPessoaId()).getNome()
                        .toLowerCase(Locale.ROOT).contains(filtro))
                .map(professor -> ProfessorResponse.deResumo(professor, pessoasPorId.get(professor.getPessoaId())))
                .toList();
    }

    public ProfessorResponse buscarPorId(UUID id) {
        UUID escolaId = isolamentoEscola.escolaIdAtual();
        Professor professor = buscarProfessor(id, escolaId);
        return ProfessorResponse.de(professor, buscarPessoa(professor, escolaId));
    }

    /**
     * Cadastra o professor na escola do usuario autenticado e cria o login
     * dele. O request nao carrega escola: mesmo que carregasse, seria
     * ignorado. E-mail ja usado como login barra o cadastro inteiro (409).
     */
    @Transactional
    public ProfessorResponse cadastrar(ProfessorRequest request) {
        UUID escolaId = isolamentoEscola.escolaIdAtual();
        exigirCpfLivre(request.cpf(), escolaId, null);

        Pessoa pessoa = pessoaRepository.save(Pessoa.builder()
                .escolaId(escolaId)
                .nome(request.nome().trim())
                .cpf(request.cpf())
                .email(request.email().trim())
                .telefone(request.telefone())
                .ativo(true)
                .build());
        pessoaPapelRepository.save(PessoaPapel.builder().pessoaId(pessoa.getId()).papel(PapelPessoa.PROFESSOR).build());
        Usuario usuario = acessoService.enviarAcessoInicial(pessoa, PerfilUsuario.PROFESSOR);

        Professor professor = professorRepository.save(Professor.builder()
                .escolaId(escolaId)
                .pessoaId(pessoa.getId())
                .usuarioId(usuario.getId())
                .formacao(request.formacao())
                .dataAdmissao(request.dataAdmissao())
                .vinculo(request.vinculo())
                .ativo(true)
                .build());
        auditar(OperacaoAuditoria.CRIAR, professor.getId());
        return ProfessorResponse.de(professor, pessoa);
    }

    /**
     * Novo e-mail de primeiro acesso, com senha provisoria renovada: para
     * quem perdeu o e-mail ou deixou a senha expirar. Recusado (409) se o
     * professor ja definiu a senha.
     */
    @Transactional
    public void reenviarAcesso(UUID id) {
        UUID escolaId = isolamentoEscola.escolaIdAtual();
        Professor professor = buscarProfessor(id, escolaId);
        if (!professor.isAtivo()) {
            throw new IllegalArgumentException("Professor inativo nao recebe acesso");
        }
        Usuario usuario = acessoService.enviarAcessoInicial(buscarPessoa(professor, escolaId),
                PerfilUsuario.PROFESSOR);
        if (!usuario.getId().equals(professor.getUsuarioId())) {
            professor.setUsuarioId(usuario.getId());
            professorRepository.save(professor);
        }
        auditar(OperacaoAuditoria.REENVIAR_ACESSO, professor.getId());
    }

    @Transactional
    public ProfessorResponse atualizar(UUID id, ProfessorRequest request) {
        UUID escolaId = isolamentoEscola.escolaIdAtual();
        Professor professor = buscarProfessor(id, escolaId);
        Pessoa pessoa = buscarPessoa(professor, escolaId);
        exigirCpfLivre(request.cpf(), escolaId, professor.getId());

        pessoa.setNome(request.nome().trim());
        pessoa.setCpf(request.cpf());
        pessoa.setEmail(request.email().trim());
        pessoa.setTelefone(request.telefone());
        professor.setFormacao(request.formacao());
        professor.setDataAdmissao(request.dataAdmissao());
        professor.setVinculo(request.vinculo());
        professor.setAtualizadoEm(Instant.now());

        acessoService.sincronizarLogin(pessoa);
        auditar(OperacaoAuditoria.EDITAR, professor.getId());
        return ProfessorResponse.de(professorRepository.save(professor), pessoaRepository.save(pessoa));
    }

    @Transactional
    public ProfessorResponse inativar(UUID id) {
        return alterarAtivo(id, false);
    }

    @Transactional
    public ProfessorResponse ativar(UUID id) {
        return alterarAtivo(id, true);
    }

    /**
     * Exclusao fisica, para quem saiu da escola: some o professor, o login e
     * o papel PROFESSOR. A pessoa so e apagada se nao tiver outro papel na
     * escola (ex.: tambem e responsavel de aluno).
     *
     * @throws ProfessorComHistoricoException se outros registros ainda
     *         apontam para o professor
     */
    @Transactional
    public void excluir(UUID id) {
        UUID escolaId = isolamentoEscola.escolaIdAtual();
        Professor professor = buscarProfessor(id, escolaId);
        Pessoa pessoa = buscarPessoa(professor, escolaId);

        List<PessoaPapel> papeis = pessoaPapelRepository.findAllByPessoaId(pessoa.getId());
        List<PessoaPapel> papeisProfessor = papeis.stream()
                .filter(papel -> papel.getPapel() == PapelPessoa.PROFESSOR)
                .toList();
        try {
            professorRepository.delete(professor);
            professorRepository.flush();
        } catch (DataIntegrityViolationException e) {
            throw new ProfessorComHistoricoException();
        }
        acessoService.excluirLogin(pessoa);
        pessoaPapelRepository.deleteAll(papeisProfessor);
        if (papeisProfessor.size() == papeis.size()) {
            pessoaRepository.delete(pessoa);
        }
        // O log nao aponta por FK: o id do professor excluido continua identificando o registro.
        auditar(OperacaoAuditoria.EXCLUIR, professor.getId());
    }

    private void auditar(OperacaoAuditoria operacao, UUID professorId) {
        auditoriaService.registrar(operacao, EntidadeAuditada.PROFESSOR, professorId);
    }

    private ProfessorResponse alterarAtivo(UUID id, boolean ativo) {
        UUID escolaId = isolamentoEscola.escolaIdAtual();
        Professor professor = buscarProfessor(id, escolaId);
        Pessoa pessoa = buscarPessoa(professor, escolaId);
        if (professor.isAtivo() != ativo) {
            professor.setAtivo(ativo);
            professor.setAtualizadoEm(Instant.now());
            professor = professorRepository.save(professor);
            acessoService.definirAtivo(pessoa, ativo);
            auditar(ativo ? OperacaoAuditoria.ATIVAR : OperacaoAuditoria.INATIVAR, professor.getId());
        }
        return ProfessorResponse.de(professor, pessoa);
    }

    /**
     * CPF cifrado com IV aleatorio nao pode ter UNIQUE no banco: decifra os
     * professores da escola e compara aqui. {@code ignorarId} e o proprio
     * professor, na edicao.
     */
    private void exigirCpfLivre(String cpf, UUID escolaId, UUID ignorarId) {
        List<Professor> outros = professorRepository.findAllByEscolaId(escolaId).stream()
                .filter(professor -> !professor.getId().equals(ignorarId))
                .toList();
        boolean repetido = pessoasDe(outros, escolaId).values().stream()
                .anyMatch(pessoa -> cpf.equals(pessoa.getCpf()));
        if (repetido) {
            throw new CpfJaCadastradoException();
        }
    }

    private Map<UUID, Pessoa> pessoasDe(List<Professor> professores, UUID escolaId) {
        if (professores.isEmpty()) {
            return Map.of();
        }
        return pessoaRepository
                .findAllByIdInAndEscolaId(professores.stream().map(Professor::getPessoaId).toList(), escolaId)
                .stream().collect(Collectors.toMap(Pessoa::getId, pessoa -> pessoa));
    }

    private Professor buscarProfessor(UUID id, UUID escolaId) {
        return professorRepository.findByIdAndEscolaId(id, escolaId).orElseThrow(ProfessorService::naoEncontrado);
    }

    private Pessoa buscarPessoa(Professor professor, UUID escolaId) {
        return pessoaRepository.findByIdAndEscolaId(professor.getPessoaId(), escolaId)
                .orElseThrow(ProfessorService::naoEncontrado);
    }

    private static RecursoNaoEncontradoException naoEncontrado() {
        return new RecursoNaoEncontradoException("Professor nao encontrado");
    }
}
