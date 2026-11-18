package br.pucminas.eduka.backend.service.pessoas;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.pucminas.eduka.backend.domain.financeiro.StatusCobranca;
import br.pucminas.eduka.backend.domain.pessoas.Aluno;
import br.pucminas.eduka.backend.domain.pessoas.AlunoResponsavel;
import br.pucminas.eduka.backend.domain.pessoas.AlunoResponsavelId;
import br.pucminas.eduka.backend.domain.pessoas.PapelPessoa;
import br.pucminas.eduka.backend.domain.pessoas.Pessoa;
import br.pucminas.eduka.backend.domain.pessoas.PessoaPapel;
import br.pucminas.eduka.backend.domain.pessoas.Responsavel;
import br.pucminas.eduka.backend.dto.comum.EnderecoRequest;
import br.pucminas.eduka.backend.dto.pessoas.CadastroResponsavelRequest;
import br.pucminas.eduka.backend.dto.pessoas.CadastroResponsavelResponse.AlunoDoResponsavelResponse;
import br.pucminas.eduka.backend.dto.pessoas.CadastroResponsavelResponse;
import br.pucminas.eduka.backend.dto.pessoas.ResponsavelRequest;
import br.pucminas.eduka.backend.dto.pessoas.ResponsavelResponse;
import br.pucminas.eduka.backend.dto.pessoas.VinculoResponsavelRequest;
import br.pucminas.eduka.backend.exception.comum.RecursoNaoEncontradoException;
import br.pucminas.eduka.backend.exception.comum.RegistroEmUsoException;
import br.pucminas.eduka.backend.exception.pessoas.CpfJaCadastradoException;
import br.pucminas.eduka.backend.repository.financeiro.CobrancaRepository;
import br.pucminas.eduka.backend.repository.pessoas.AlunoRepository;
import br.pucminas.eduka.backend.repository.pessoas.AlunoResponsavelRepository;
import br.pucminas.eduka.backend.repository.pessoas.PessoaPapelRepository;
import br.pucminas.eduka.backend.repository.pessoas.PessoaRepository;
import br.pucminas.eduka.backend.repository.pessoas.ResponsavelRepository;
import br.pucminas.eduka.backend.security.IsolamentoEscola;
import br.pucminas.eduka.backend.validation.CpfValidator;
import br.pucminas.eduka.backend.domain.auditoria.EntidadeAuditada;
import br.pucminas.eduka.backend.domain.auditoria.OperacaoAuditoria;
import br.pucminas.eduka.backend.service.auditoria.AuditoriaService;

/**
 * Cadastro de responsaveis e vinculo deles com os alunos, com isolamento por
 * escola (RF05, RNF03).
 *
 * <p>
 * A escola nunca e parametro: sai de {@link IsolamentoEscola#escolaIdAtual()}
 * e entra em toda consulta como filtro. Responsavel ou aluno de outra escola
 * e, para o usuario atual, indistinguivel de um inexistente (404).
 * </p>
 *
 * <p>
 * Nome, CPF e contato vivem em {@link Pessoa}, cifrados: busca por nome e
 * checagem de CPF repetido filtram primeiro pela escola e comparam em
 * memoria, depois de decifrar.
 * </p>
 *
 * <p>
 * Regras do vinculo:
 * </p>
 * <ul>
 * <li>Um responsavel pode ter varios alunos (irmaos) e um aluno varios
 * responsaveis.</li>
 * <li>No maximo um responsavel financeiro por aluno: marcar outro tira a
 * marca do anterior.</li>
 * <li>O financeiro e o pagador no provedor (R18), que exige CPF: sem CPF valido
 * nao se marca, em vez de a recusa aparecer so quando o aluno for pagar.</li>
 * <li>Nao se desvincula responsavel com cobranca em aberto daquele aluno, nem
 * se exclui responsavel ainda vinculado ou com cobranca no historico.</li>
 * </ul>
 */
@Service
public class ResponsavelService {

    private static final Set<StatusCobranca> EM_ABERTO = Set.of(StatusCobranca.PENDENTE, StatusCobranca.EM_ATRASO);

    private final ResponsavelRepository responsavelRepository;
    private final AlunoResponsavelRepository alunoResponsavelRepository;
    private final AlunoRepository alunoRepository;
    private final PessoaRepository pessoaRepository;
    private final PessoaPapelRepository pessoaPapelRepository;
    private final CobrancaRepository cobrancaRepository;
    private final IsolamentoEscola isolamentoEscola;
    private final AuditoriaService auditoriaService;

    public ResponsavelService(ResponsavelRepository responsavelRepository,
            AlunoResponsavelRepository alunoResponsavelRepository, AlunoRepository alunoRepository,
            PessoaRepository pessoaRepository, PessoaPapelRepository pessoaPapelRepository,
            CobrancaRepository cobrancaRepository, IsolamentoEscola isolamentoEscola,
            AuditoriaService auditoriaService) {
        this.responsavelRepository = responsavelRepository;
        this.alunoResponsavelRepository = alunoResponsavelRepository;
        this.alunoRepository = alunoRepository;
        this.pessoaRepository = pessoaRepository;
        this.pessoaPapelRepository = pessoaPapelRepository;
        this.cobrancaRepository = cobrancaRepository;
        this.isolamentoEscola = isolamentoEscola;
        this.auditoriaService = auditoriaService;
    }

    // ------------------------------------------------------------------
    // Cadastro (/responsaveis)
    // ------------------------------------------------------------------

    /** @param nome filtro parcial, sem diferenciar caixa; nulo ou vazio lista todos */
    @Transactional(readOnly = true)
    public List<CadastroResponsavelResponse> listar(String nome) {
        UUID escolaId = isolamentoEscola.escolaIdAtual();
        List<Responsavel> responsaveis = responsavelRepository.findAllByEscolaId(escolaId);
        Map<UUID, Pessoa> pessoasPorId = pessoasPorId(responsaveis.stream().map(Responsavel::getPessoaId).toList(),
                escolaId);

        String filtro = nome == null || nome.isBlank() ? null : nome.trim().toLowerCase(Locale.ROOT);
        return responsaveis.stream()
                .filter(responsavel -> filtro == null || pessoasPorId.get(responsavel.getPessoaId()).getNome()
                        .toLowerCase(Locale.ROOT).contains(filtro))
                .map(responsavel -> CadastroResponsavelResponse.deResumo(responsavel,
                        pessoasPorId.get(responsavel.getPessoaId()), List.of()))
                .toList();
    }

    @Transactional(readOnly = true)
    public CadastroResponsavelResponse buscarPorId(UUID id) {
        UUID escolaId = isolamentoEscola.escolaIdAtual();
        Responsavel responsavel = buscarResponsavel(id, escolaId);
        return CadastroResponsavelResponse.de(responsavel, buscarPessoa(responsavel, escolaId),
                alunosDe(responsavel, escolaId));
    }

    /**
     * Cadastra o responsavel na escola do usuario autenticado, ainda sem
     * aluno. O request nao carrega escola: mesmo que carregasse, seria
     * ignorado.
     *
     * @throws CpfJaCadastradoException se ja ha responsavel com o CPF na escola
     */
    @Transactional
    public CadastroResponsavelResponse cadastrar(CadastroResponsavelRequest request) {
        UUID escolaId = isolamentoEscola.escolaIdAtual();
        exigirCpfLivre(request.cpf(), escolaId, null);
        ResponsavelComPessoa criado = criar(escolaId, request.nome().trim(), request.cpf(), aparar(request.email()),
                request.telefone(), request.endereco());
        return CadastroResponsavelResponse.de(criado.responsavel(), criado.pessoa(), List.of());
    }

    @Transactional
    public CadastroResponsavelResponse atualizar(UUID id, CadastroResponsavelRequest request) {
        UUID escolaId = isolamentoEscola.escolaIdAtual();
        Responsavel responsavel = buscarResponsavel(id, escolaId);
        Pessoa pessoa = buscarPessoa(responsavel, escolaId);
        exigirCpfLivre(request.cpf(), escolaId, responsavel.getId());

        EnderecoRequest endereco = request.endereco();
        pessoa.setNome(request.nome().trim());
        pessoa.setCpf(request.cpf());
        pessoa.setEmail(aparar(request.email()));
        pessoa.setTelefone(request.telefone());
        pessoa.setLogradouro(endereco == null ? null : endereco.logradouro());
        pessoa.setNumero(endereco == null ? null : endereco.numero());
        pessoa.setBairro(endereco == null ? null : endereco.bairro());
        pessoa.setCidade(endereco == null ? null : endereco.cidade());
        pessoa.setEstado(endereco == null ? null : endereco.estado());
        pessoa.setCep(endereco == null ? null : endereco.cep());
        responsavel.setAtualizadoEm(Instant.now());
        auditar(OperacaoAuditoria.EDITAR, responsavel);

        return CadastroResponsavelResponse.de(responsavelRepository.save(responsavel), pessoaRepository.save(pessoa),
                alunosDe(responsavel, escolaId));
    }

    /**
     * Exclusao fisica de responsavel cadastrado por engano. A pessoa so e
     * apagada se nao tiver outro papel na escola.
     *
     * @throws RegistroEmUsoException se ainda ha aluno vinculado ou cobranca
     *         lancada para o responsavel
     */
    @Transactional
    public void excluir(UUID id) {
        UUID escolaId = isolamentoEscola.escolaIdAtual();
        Responsavel responsavel = buscarResponsavel(id, escolaId);
        Pessoa pessoa = buscarPessoa(responsavel, escolaId);
        if (alunoResponsavelRepository.existsById_PessoaId(pessoa.getId())) {
            throw new RegistroEmUsoException("Responsavel ainda vinculado a aluno; desvincule antes de excluir");
        }
        if (cobrancaRepository.existsByEscolaIdAndResponsavelId(escolaId, pessoa.getId())) {
            throw new RegistroEmUsoException("Responsavel tem cobrancas lancadas e nao pode ser excluido");
        }

        List<PessoaPapel> papeis = pessoaPapelRepository.findAllByPessoaId(pessoa.getId());
        List<PessoaPapel> papeisResponsavel = papeis.stream()
                .filter(papel -> papel.getPapel() == PapelPessoa.RESPONSAVEL)
                .toList();
        responsavelRepository.delete(responsavel);
        pessoaPapelRepository.deleteAll(papeisResponsavel);
        if (papeisResponsavel.size() == papeis.size()) {
            pessoaRepository.delete(pessoa);
        }
        auditar(OperacaoAuditoria.EXCLUIR, responsavel);
    }

    // ------------------------------------------------------------------
    // Vinculo com aluno (/alunos/{alunoId}/responsaveis)
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<ResponsavelResponse> listarDoAluno(UUID alunoId) {
        UUID escolaId = isolamentoEscola.escolaIdAtual();
        return responsaveisDoAluno(buscarAluno(alunoId, escolaId).getId(), escolaId);
    }

    /**
     * Liga o responsavel ao aluno, ou atualiza o vinculo se ja existir.
     * Marcar como financeiro tira a marca do financeiro anterior do aluno.
     */
    @Transactional
    public ResponsavelResponse vincular(UUID alunoId, UUID responsavelId, VinculoResponsavelRequest request) {
        UUID escolaId = isolamentoEscola.escolaIdAtual();
        Aluno aluno = buscarAluno(alunoId, escolaId);
        Responsavel responsavel = buscarResponsavel(responsavelId, escolaId);
        Pessoa pessoa = buscarPessoa(responsavel, escolaId);
        boolean financeiro = Boolean.TRUE.equals(request.financeiro());
        if (financeiro) {
            exigirCpfDoFinanceiro(pessoa);
            tirarFinanceiroAnterior(aluno.getId(), pessoa.getId());
        }

        AlunoResponsavelId id = new AlunoResponsavelId(aluno.getId(), pessoa.getId());
        AlunoResponsavel vinculo = alunoResponsavelRepository.findById(id)
                .orElseGet(() -> AlunoResponsavel.builder().id(id).build());
        vinculo.setParentesco(request.parentesco());
        vinculo.setFinanceiro(financeiro);
        alunoResponsavelRepository.save(vinculo);
        auditar(OperacaoAuditoria.VINCULAR, responsavel);
        return paraResposta(responsavel, pessoa, vinculo);
    }

    /**
     * Cadastra e vincula numa transacao so, a partir da edicao do aluno. CPF ja
     * cadastrado na escola reaproveita o responsavel existente (irmaos), como
     * no cadastro do aluno.
     *
     * @throws RegistroEmUsoException   se esse responsavel ja esta vinculado ao
     *                                  aluno
     * @throws IllegalArgumentException se faltar nome, se o CPF for invalido ou
     *                                  se o financeiro vier sem CPF
     */
    @Transactional
    public ResponsavelResponse incluirNoAluno(UUID alunoId, ResponsavelRequest request) {
        UUID escolaId = isolamentoEscola.escolaIdAtual();
        Aluno aluno = buscarAluno(alunoId, escolaId);
        ResponsavelComPessoa responsavel = acharOuCriar(request, escolaId);
        AlunoResponsavelId id = new AlunoResponsavelId(aluno.getId(), responsavel.pessoa().getId());
        if (alunoResponsavelRepository.existsById(id)) {
            throw new RegistroEmUsoException("Responsavel ja esta vinculado a este aluno");
        }
        boolean financeiro = Boolean.TRUE.equals(request.financeiro());
        if (financeiro) {
            exigirCpfDoFinanceiro(responsavel.pessoa());
            tirarFinanceiroAnterior(aluno.getId(), responsavel.pessoa().getId());
        }
        AlunoResponsavel vinculo = AlunoResponsavel.builder()
                .id(id)
                .parentesco(request.parentesco())
                .financeiro(financeiro)
                .build();
        alunoResponsavelRepository.save(vinculo);
        auditar(OperacaoAuditoria.VINCULAR, responsavel.responsavel());
        return paraResposta(responsavel.responsavel(), responsavel.pessoa(), vinculo);
    }

    /**
     * Desfaz o vinculo. O cadastro do responsavel continua.
     *
     * @throws RegistroEmUsoException se ha cobranca em aberto do aluno em nome
     *         deste responsavel
     */
    @Transactional
    public void desvincular(UUID alunoId, UUID responsavelId) {
        UUID escolaId = isolamentoEscola.escolaIdAtual();
        Aluno aluno = buscarAluno(alunoId, escolaId);
        Responsavel responsavel = buscarResponsavel(responsavelId, escolaId);
        AlunoResponsavel vinculo = alunoResponsavelRepository
                .findById(new AlunoResponsavelId(aluno.getId(), responsavel.getPessoaId()))
                .orElseThrow(() -> new RecursoNaoEncontradoException("Responsavel nao esta vinculado ao aluno"));
        if (cobrancaRepository.existsByEscolaIdAndAluno_IdAndResponsavelIdAndStatusIn(escolaId, aluno.getId(),
                responsavel.getPessoaId(), EM_ABERTO)) {
            throw new RegistroEmUsoException(
                    "Responsavel tem cobrancas em aberto deste aluno; troque o responsavel delas antes");
        }
        alunoResponsavelRepository.delete(vinculo);
        auditar(OperacaoAuditoria.DESVINCULAR, responsavel);
    }

    // ------------------------------------------------------------------
    // Usado por AlunoService
    // ------------------------------------------------------------------

    /**
     * Responsaveis informados no cadastro do aluno. CPF ja cadastrado na
     * escola reaproveita o responsavel existente (irmaos); sem CPF ou com CPF
     * novo, cria um. Roda na transacao do cadastro do aluno.
     *
     * @throws IllegalArgumentException se faltar nome, se o CPF for invalido,
     *                                  se o mesmo responsavel vier repetido, se
     *                                  houver mais de um financeiro ou se o
     *                                  financeiro vier sem CPF
     */
    List<ResponsavelResponse> vincularAoNovoAluno(Aluno aluno, List<ResponsavelRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            return List.of();
        }
        if (requests.stream().filter(r -> Boolean.TRUE.equals(r.financeiro())).count() > 1) {
            throw new IllegalArgumentException("Aluno pode ter apenas um responsavel financeiro");
        }
        UUID escolaId = aluno.getEscolaId();
        List<ResponsavelResponse> respostas = new ArrayList<>();
        Set<UUID> jaVinculados = new HashSet<>();
        for (ResponsavelRequest request : requests) {
            ResponsavelComPessoa responsavel = acharOuCriar(request, escolaId);
            if (!jaVinculados.add(responsavel.pessoa().getId())) {
                throw new IllegalArgumentException("Responsavel informado mais de uma vez");
            }
            if (Boolean.TRUE.equals(request.financeiro())) {
                exigirCpfDoFinanceiro(responsavel.pessoa());
            }
            AlunoResponsavel vinculo = AlunoResponsavel.builder()
                    .id(new AlunoResponsavelId(aluno.getId(), responsavel.pessoa().getId()))
                    .parentesco(request.parentesco())
                    .financeiro(Boolean.TRUE.equals(request.financeiro()))
                    .build();
            alunoResponsavelRepository.save(vinculo);
            auditar(OperacaoAuditoria.VINCULAR, responsavel.responsavel());
            respostas.add(paraResposta(responsavel.responsavel(), responsavel.pessoa(), vinculo));
        }
        return respostas;
    }

    /** Responsaveis do aluno, com o vinculo; o aluno ja deve ter sido conferido na escola. */
    public List<ResponsavelResponse> responsaveisDoAluno(UUID alunoId, UUID escolaId) {
        List<AlunoResponsavel> vinculos = alunoResponsavelRepository.findAllById_AlunoId(alunoId);
        if (vinculos.isEmpty()) {
            return List.of();
        }
        List<UUID> pessoaIds = vinculos.stream().map(vinculo -> vinculo.getId().pessoaId()).toList();
        Map<UUID, Pessoa> pessoasPorId = pessoasPorId(pessoaIds, escolaId);
        Map<UUID, Responsavel> responsaveisPorPessoa = responsavelRepository
                .findAllByPessoaIdInAndEscolaId(pessoaIds, escolaId).stream()
                .collect(Collectors.toMap(Responsavel::getPessoaId, Function.identity()));
        return vinculos.stream()
                .filter(vinculo -> pessoasPorId.containsKey(vinculo.getId().pessoaId()))
                .map(vinculo -> {
                    Pessoa pessoa = pessoasPorId.get(vinculo.getId().pessoaId());
                    Responsavel responsavel = responsaveisPorPessoa.get(pessoa.getId());
                    return new ResponsavelResponse(pessoa.getId(), responsavel == null ? null : responsavel.getId(),
                            pessoa.getNome(), pessoa.getEmail(), pessoa.getTelefone(), vinculo.getParentesco(),
                            vinculo.isFinanceiro());
                })
                .toList();
    }

    // ------------------------------------------------------------------

    /** O responsavel e a pessoa dele, para nao rebuscar a pessoa logo depois de criar/achar. */
    private record ResponsavelComPessoa(Responsavel responsavel, Pessoa pessoa) {
    }

    /** CPF ja cadastrado na escola reaproveita o responsavel; sem CPF ou com CPF novo, cria um. */
    private ResponsavelComPessoa acharOuCriar(ResponsavelRequest request, UUID escolaId) {
        String cpf = aparar(request.cpf());
        if (cpf != null && !new CpfValidator().isValid(cpf, null)) {
            throw new IllegalArgumentException("CPF do responsavel invalido");
        }
        return (cpf == null ? Optional.<ResponsavelComPessoa>empty() : buscarPorCpf(cpf, escolaId))
                .orElseGet(() -> {
                    if (request.nome() == null || request.nome().isBlank()) {
                        throw new IllegalArgumentException("Nome do responsavel e obrigatorio");
                    }
                    return criar(escolaId, request.nome().trim(), cpf, aparar(request.email()),
                            request.telefone(), null);
                });
    }

    /** Mesma conferencia do provedor ao emitir ({@code RoteadorPagamento}). */
    private static void exigirCpfDoFinanceiro(Pessoa pessoa) {
        if (pessoa.getCpf() == null || !new CpfValidator().isValid(pessoa.getCpf(), null)) {
            throw new IllegalArgumentException("Responsavel financeiro precisa de CPF valido");
        }
    }

    private ResponsavelComPessoa criar(UUID escolaId, String nome, String cpf, String email, String telefone,
            EnderecoRequest endereco) {
        Pessoa pessoa = pessoaRepository.save(Pessoa.builder()
                .escolaId(escolaId)
                .nome(nome)
                .cpf(cpf)
                .email(email)
                .telefone(telefone)
                .logradouro(endereco == null ? null : endereco.logradouro())
                .numero(endereco == null ? null : endereco.numero())
                .bairro(endereco == null ? null : endereco.bairro())
                .cidade(endereco == null ? null : endereco.cidade())
                .estado(endereco == null ? null : endereco.estado())
                .cep(endereco == null ? null : endereco.cep())
                .ativo(true)
                .build());
        pessoaPapelRepository
                .save(PessoaPapel.builder().pessoaId(pessoa.getId()).papel(PapelPessoa.RESPONSAVEL).build());
        Responsavel responsavel = responsavelRepository.save(Responsavel.builder()
                .escolaId(escolaId)
                .pessoaId(pessoa.getId())
                .build());
        auditar(OperacaoAuditoria.CRIAR, responsavel);
        return new ResponsavelComPessoa(responsavel, pessoa);
    }

    /** O registro do log e o cadastro do responsavel; o vinculo com o aluno entra como VINCULAR. */
    private void auditar(OperacaoAuditoria operacao, Responsavel responsavel) {
        auditoriaService.registrar(operacao, EntidadeAuditada.RESPONSAVEL, responsavel.getId());
    }

    private void tirarFinanceiroAnterior(UUID alunoId, UUID novoPessoaId) {
        alunoResponsavelRepository.findById_AlunoIdAndFinanceiroTrue(alunoId)
                .filter(anterior -> !anterior.getId().pessoaId().equals(novoPessoaId))
                .ifPresent(anterior -> {
                    anterior.setFinanceiro(false);
                    // Flush antes de marcar o novo: o indice unico parcial nao
                    // aceita dois financeiros nem por um instante.
                    alunoResponsavelRepository.saveAndFlush(anterior);
                });
    }

    private Optional<ResponsavelComPessoa> buscarPorCpf(String cpf, UUID escolaId) {
        List<Responsavel> responsaveis = responsavelRepository.findAllByEscolaId(escolaId);
        Map<UUID, Pessoa> pessoas = pessoasPorId(responsaveis.stream().map(Responsavel::getPessoaId).toList(),
                escolaId);
        return responsaveis.stream()
                .filter(responsavel -> pessoas.containsKey(responsavel.getPessoaId()))
                .map(responsavel -> new ResponsavelComPessoa(responsavel, pessoas.get(responsavel.getPessoaId())))
                .filter(encontrado -> cpf.equals(encontrado.pessoa().getCpf()))
                .findFirst();
    }

    /**
     * CPF cifrado com IV aleatorio nao pode ter UNIQUE no banco: decifra os
     * responsaveis da escola e compara aqui. {@code ignorarId} e o proprio
     * responsavel, na edicao.
     */
    private void exigirCpfLivre(String cpf, UUID escolaId, UUID ignorarId) {
        buscarPorCpf(cpf, escolaId)
                .filter(existente -> !existente.responsavel().getId().equals(ignorarId))
                .ifPresent(existente -> {
                    throw new CpfJaCadastradoException("Ja existe responsavel com este CPF nesta escola");
                });
    }

    private List<AlunoDoResponsavelResponse> alunosDe(Responsavel responsavel, UUID escolaId) {
        List<AlunoResponsavel> vinculos = alunoResponsavelRepository.findAllById_PessoaId(responsavel.getPessoaId());
        if (vinculos.isEmpty()) {
            return List.of();
        }
        Map<UUID, Aluno> alunosPorId = alunoRepository.findAllByEscolaId(escolaId).stream()
                .collect(Collectors.toMap(Aluno::getId, Function.identity()));
        List<Aluno> alunos = vinculos.stream()
                .map(vinculo -> alunosPorId.get(vinculo.getId().alunoId()))
                .filter(Objects::nonNull)
                .toList();
        Map<UUID, Pessoa> pessoasPorId = pessoasPorId(alunos.stream().map(Aluno::getPessoaId).toList(), escolaId);
        return vinculos.stream()
                .filter(vinculo -> alunosPorId.containsKey(vinculo.getId().alunoId()))
                .map(vinculo -> {
                    Aluno aluno = alunosPorId.get(vinculo.getId().alunoId());
                    Pessoa pessoa = pessoasPorId.get(aluno.getPessoaId());
                    return new AlunoDoResponsavelResponse(aluno.getId(), pessoa == null ? null : pessoa.getNome(),
                            aluno.getMatricula(), vinculo.getParentesco(), vinculo.isFinanceiro());
                })
                .toList();
    }

    private static ResponsavelResponse paraResposta(Responsavel responsavel, Pessoa pessoa,
            AlunoResponsavel vinculo) {
        return new ResponsavelResponse(pessoa.getId(), responsavel.getId(), pessoa.getNome(), pessoa.getEmail(),
                pessoa.getTelefone(), vinculo.getParentesco(), vinculo.isFinanceiro());
    }

    private Map<UUID, Pessoa> pessoasPorId(Collection<UUID> ids, UUID escolaId) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return pessoaRepository.findAllByIdInAndEscolaId(ids, escolaId).stream()
                .collect(Collectors.toMap(Pessoa::getId, Function.identity()));
    }

    private Aluno buscarAluno(UUID alunoId, UUID escolaId) {
        return alunoRepository.findByIdAndEscolaId(alunoId, escolaId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Aluno nao encontrado"));
    }

    private Responsavel buscarResponsavel(UUID id, UUID escolaId) {
        return responsavelRepository.findByIdAndEscolaId(id, escolaId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Responsavel nao encontrado"));
    }

    private Pessoa buscarPessoa(Responsavel responsavel, UUID escolaId) {
        return pessoaRepository.findByIdAndEscolaId(responsavel.getPessoaId(), escolaId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Responsavel nao encontrado"));
    }

    private static String aparar(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }
}
