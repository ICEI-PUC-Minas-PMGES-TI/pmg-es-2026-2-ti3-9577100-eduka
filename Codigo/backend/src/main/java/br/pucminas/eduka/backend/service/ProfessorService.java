package br.pucminas.eduka.backend.service;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.pucminas.eduka.backend.domain.PapelPessoa;
import br.pucminas.eduka.backend.domain.Pessoa;
import br.pucminas.eduka.backend.domain.PessoaPapel;
import br.pucminas.eduka.backend.domain.Professor;
import br.pucminas.eduka.backend.dto.ProfessorRequest;
import br.pucminas.eduka.backend.dto.ProfessorResponse;
import br.pucminas.eduka.backend.exception.CpfJaCadastradoException;
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
 * Nao ha exclusao fisica: o professor e inativado, para nao quebrar
 * historico (turmas, avaliacoes) que venha a apontar para ele.
 * </p>
 */
@Service
public class ProfessorService {

    private final ProfessorRepository professorRepository;
    private final PessoaRepository pessoaRepository;
    private final PessoaPapelRepository pessoaPapelRepository;
    private final IsolamentoEscola isolamentoEscola;

    public ProfessorService(ProfessorRepository professorRepository, PessoaRepository pessoaRepository,
            PessoaPapelRepository pessoaPapelRepository, IsolamentoEscola isolamentoEscola) {
        this.professorRepository = professorRepository;
        this.pessoaRepository = pessoaRepository;
        this.pessoaPapelRepository = pessoaPapelRepository;
        this.isolamentoEscola = isolamentoEscola;
    }

    /**
     * @param nome  filtro parcial, sem diferenciar caixa; nulo ou vazio lista todos
     * @param ativo nulo lista ativos e inativos
     */
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
     * Cadastra o professor na escola do usuario autenticado. O request nao
     * carrega escola: mesmo que carregasse, seria ignorado.
     */
    @Transactional
    public ProfessorResponse cadastrar(ProfessorRequest request) {
        UUID escolaId = isolamentoEscola.escolaIdAtual();
        exigirCpfLivre(request.cpf(), escolaId, null);

        Pessoa pessoa = pessoaRepository.save(Pessoa.builder()
                .escolaId(escolaId)
                .nome(request.nome().trim())
                .cpf(request.cpf())
                .email(request.email())
                .telefone(request.telefone())
                .ativo(true)
                .build());
        pessoaPapelRepository.save(PessoaPapel.builder().pessoaId(pessoa.getId()).papel(PapelPessoa.PROFESSOR).build());

        Professor professor = professorRepository.save(Professor.builder()
                .escolaId(escolaId)
                .pessoaId(pessoa.getId())
                .formacao(request.formacao())
                .dataAdmissao(request.dataAdmissao())
                .ativo(true)
                .build());
        return ProfessorResponse.de(professor, pessoa);
    }

    @Transactional
    public ProfessorResponse atualizar(UUID id, ProfessorRequest request) {
        UUID escolaId = isolamentoEscola.escolaIdAtual();
        Professor professor = buscarProfessor(id, escolaId);
        Pessoa pessoa = buscarPessoa(professor, escolaId);
        exigirCpfLivre(request.cpf(), escolaId, professor.getId());

        pessoa.setNome(request.nome().trim());
        pessoa.setCpf(request.cpf());
        pessoa.setEmail(request.email());
        pessoa.setTelefone(request.telefone());
        professor.setFormacao(request.formacao());
        professor.setDataAdmissao(request.dataAdmissao());
        professor.setAtualizadoEm(Instant.now());

        return ProfessorResponse.de(professorRepository.save(professor), pessoaRepository.save(pessoa));
    }

    @Transactional
    public void inativar(UUID id) {
        alterarAtivo(id, false);
    }

    @Transactional
    public ProfessorResponse ativar(UUID id) {
        return alterarAtivo(id, true);
    }

    private ProfessorResponse alterarAtivo(UUID id, boolean ativo) {
        UUID escolaId = isolamentoEscola.escolaIdAtual();
        Professor professor = buscarProfessor(id, escolaId);
        Pessoa pessoa = buscarPessoa(professor, escolaId);
        if (professor.isAtivo() != ativo) {
            professor.setAtivo(ativo);
            professor.setAtualizadoEm(Instant.now());
            professor = professorRepository.save(professor);
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
