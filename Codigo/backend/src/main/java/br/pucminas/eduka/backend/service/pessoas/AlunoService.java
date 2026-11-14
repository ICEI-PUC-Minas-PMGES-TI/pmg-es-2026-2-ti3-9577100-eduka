// Codigo/backend/src/main/java/br/pucminas/eduka/backend/service/pessoas/AlunoService.java
package br.pucminas.eduka.backend.service.pessoas;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.Sort.Direction;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.pucminas.eduka.backend.domain.academico.StatusMatricula;
import br.pucminas.eduka.backend.domain.acesso.PerfilUsuario;
import br.pucminas.eduka.backend.domain.acesso.Usuario;
import br.pucminas.eduka.backend.domain.auditoria.EntidadeAuditada;
import br.pucminas.eduka.backend.domain.auditoria.OperacaoAuditoria;
import br.pucminas.eduka.backend.domain.pessoas.Aluno;
import br.pucminas.eduka.backend.domain.pessoas.PapelPessoa;
import br.pucminas.eduka.backend.domain.pessoas.Pessoa;
import br.pucminas.eduka.backend.domain.pessoas.PessoaPapel;
import br.pucminas.eduka.backend.dto.comum.EnderecoRequest;
import br.pucminas.eduka.backend.dto.comum.Ordenacao;
import br.pucminas.eduka.backend.dto.comum.PaginaComResumoResponse;
import br.pucminas.eduka.backend.dto.comum.PaginaResponse;
import br.pucminas.eduka.backend.dto.comum.Paginacao;
import br.pucminas.eduka.backend.dto.pessoas.AlunoRequest;
import br.pucminas.eduka.backend.dto.pessoas.AlunoResponse;
import br.pucminas.eduka.backend.dto.pessoas.ResponsavelResponse;
import br.pucminas.eduka.backend.dto.pessoas.ResumoAlunosResponse;
import br.pucminas.eduka.backend.exception.comum.RecursoNaoEncontradoException;
import br.pucminas.eduka.backend.exception.comum.RegistroEmUsoException;
import br.pucminas.eduka.backend.exception.pessoas.NumeroMatriculaJaCadastradoException;
import br.pucminas.eduka.backend.repository.academico.MatriculaTurmaRepository;
import br.pucminas.eduka.backend.repository.pessoas.AlunoRepository;
import br.pucminas.eduka.backend.repository.pessoas.PessoaPapelRepository;
import br.pucminas.eduka.backend.repository.pessoas.PessoaRepository;
import br.pucminas.eduka.backend.security.IsolamentoEscola;
import br.pucminas.eduka.backend.security.UsuarioAutenticado;
import br.pucminas.eduka.backend.service.acesso.AcessoService;
import br.pucminas.eduka.backend.service.auditoria.AuditoriaService;

/**
 * Regras de aluno com isolamento por escola (RF05, RNF03).
 *
 * <p>
 * A escola nunca e parametro: sai de {@link IsolamentoEscola#escolaIdAtual()}
 * e entra em toda consulta como filtro. Um aluno de outra escola e, para o
 * usuario atual, indistinguivel de um aluno inexistente (404 nos dois casos).
 * </p>
 *
 * <p>
 * Dado pessoal (nome, cpf, data de nascimento, endereco) vive em
 * {@link Pessoa}, cifrado. A busca por nome (enturmacao) sempre filtra
 * primeiro por {@code escola_id} (indice existente) e so decifra em memoria
 * depois - nunca {@code LIKE} sobre coluna cifrada.
 * </p>
 *
 * <p>
 * O login do aluno e criado junto com o cadastro, via {@link AcessoService}:
 * login = e-mail, senha provisoria = CPF, enviada por e-mail.
 * </p>
 */
@Service
public class AlunoService {

    private static final ZoneId FUSO_ESCOLA = ZoneId.of("America/Sao_Paulo");

    private final AlunoRepository alunoRepository;
    private final PessoaRepository pessoaRepository;
    private final PessoaPapelRepository pessoaPapelRepository;
    private final ResponsavelService responsavelService;
    private final IsolamentoEscola isolamentoEscola;
    private final AcessoService acessoService;
    private final MatriculaTurmaRepository matriculaRepository;
    private final Clock clock;
    private final AuditoriaService auditoriaService;

    public AlunoService(AlunoRepository alunoRepository, PessoaRepository pessoaRepository,
            PessoaPapelRepository pessoaPapelRepository, ResponsavelService responsavelService,
            IsolamentoEscola isolamentoEscola, AcessoService acessoService,
            MatriculaTurmaRepository matriculaRepository, Clock clock, AuditoriaService auditoriaService) {
        this.alunoRepository = alunoRepository;
        this.pessoaRepository = pessoaRepository;
        this.pessoaPapelRepository = pessoaPapelRepository;
        this.responsavelService = responsavelService;
        this.isolamentoEscola = isolamentoEscola;
        this.acessoService = acessoService;
        this.matriculaRepository = matriculaRepository;
        this.clock = clock;
        this.auditoriaService = auditoriaService;
    }

    /**
     * Pagina dos alunos da escola, opcionalmente filtrados por parte do nome
     * ou da matricula ({@code busca}) e pela situacao. O nome e cifrado, entao
     * filtro e ordem por nome sao feitos em memoria sobre os alunos da escola,
     * e so a pagina vai na resposta. O resumo conta a escola inteira, sem a
     * busca nem a situacao ({@link PaginaComResumoResponse}).
     */
    public PaginaComResumoResponse<AlunoResponse, ResumoAlunosResponse> listarPaginado(String busca,
            AlunoResponse.Situacao situacao, Paginacao paginacao, Ordenacao.Alunos ordenarPor, Direction direcao) {
        Comparator<AlunoResponse> campo = switch (ordenarPor) {
            case NOME -> Comparator.comparing(AlunoResponse::nome, Paginacao.ORDEM_DE_NOME);
            case MATRICULA -> Comparator.comparing(AlunoResponse::matricula,
                    Comparator.nullsLast(Comparator.naturalOrder()));
        };
        List<AlunoResponse> daEscola = listar(null);
        String termo = busca == null || busca.isBlank() ? null : busca.trim().toLowerCase(Locale.ROOT);
        List<AlunoResponse> ordenados = daEscola.stream()
                .filter(aluno -> termo == null || aluno.nome().toLowerCase(Locale.ROOT).contains(termo)
                        || (aluno.matricula() != null && aluno.matricula().toLowerCase(Locale.ROOT).contains(termo)))
                .filter(aluno -> situacao == null || aluno.situacao() == situacao)
                .sorted(Ordenacao.emMemoria(campo, Ordenacao.ou(direcao, Direction.ASC),
                        Comparator.comparing(AlunoResponse::id)))
                .toList();
        return PaginaComResumoResponse.de(PaginaResponse.recortar(ordenados, paginacao),
                ResumoAlunosResponse.de(daEscola));
    }

    public List<AlunoResponse> listar(String nome) {
        UUID escolaId = isolamentoEscola.escolaIdAtual();
        List<Aluno> alunos = alunoRepository.findAllByEscolaId(escolaId);
        Map<UUID, Pessoa> pessoasPorId = pessoaRepository
                .findAllByIdInAndEscolaId(alunos.stream().map(Aluno::getPessoaId).toList(), escolaId)
                .stream().collect(Collectors.toMap(Pessoa::getId, pessoa -> pessoa));

        // A ATIVA vale mais que a TRANCADA: o aluno com as duas esta matriculado.
        Map<UUID, MatriculaTurmaRepository.Vigente> vigentes = matriculaRepository.listarVigentes(escolaId).stream()
                .collect(Collectors.toMap(MatriculaTurmaRepository.Vigente::getAlunoId, Function.identity(),
                        (a, b) -> b.getStatus() == StatusMatricula.ATIVA ? b : a));

        String filtro = nome == null || nome.isBlank() ? null : nome.trim().toLowerCase(Locale.ROOT);
        return alunos.stream()
                .filter(aluno -> filtro == null
                        || pessoasPorId.get(aluno.getPessoaId()).getNome().toLowerCase(Locale.ROOT).contains(filtro))
                .map(aluno -> resumo(aluno, pessoasPorId.get(aluno.getPessoaId()), vigentes.get(aluno.getId())))
                .toList();
    }

    private static AlunoResponse resumo(Aluno aluno, Pessoa pessoa, MatriculaTurmaRepository.Vigente vigente) {
        if (aluno.isAnonimizado()) {
            return AlunoResponse.deResumo(aluno, pessoa, AlunoResponse.Situacao.ANONIMIZADO, null, null);
        }
        if (vigente == null) {
            return AlunoResponse.deResumo(aluno, pessoa, AlunoResponse.Situacao.SEM_TURMA, null, null);
        }
        AlunoResponse.Situacao situacao = vigente.getStatus() == StatusMatricula.ATIVA
                ? AlunoResponse.Situacao.MATRICULADO
                : AlunoResponse.Situacao.TRANCADO;
        return AlunoResponse.deResumo(aluno, pessoa, situacao, vigente.getTurmaId(), vigente.getTurmaNome());
    }

    public AlunoResponse buscarPorId(UUID id) {
        UUID escolaId = isolamentoEscola.escolaIdAtual();
        Aluno aluno = alunoRepository.findByIdAndEscolaId(id, escolaId).orElseThrow(AlunoService::naoEncontrado);
        Pessoa pessoa = pessoaRepository.findByIdAndEscolaId(aluno.getPessoaId(), escolaId)
                .orElseThrow(AlunoService::naoEncontrado);
        return AlunoResponse.de(aluno, pessoa, responsavelService.responsaveisDoAluno(aluno.getId(), escolaId));
    }

    /**
     * Dados do aluno dono do login (portal do aluno). O aluno nunca e
     * parametro: sai do token, assim como a escola. Nao ha como pedir os
     * dados de outro aluno por aqui.
     *
     * @throws RecursoNaoEncontradoException se o login nao e de um aluno
     *         cadastrado na escola
     */
    public AlunoResponse buscarMeusDados() {
        UsuarioAutenticado usuario = isolamentoEscola.usuarioAtual();
        UUID escolaId = usuario.escolaId();
        Aluno aluno = alunoRepository.findByUsuarioIdAndEscolaId(usuario.id(), escolaId)
                .orElseThrow(AlunoService::naoEncontrado);
        Pessoa pessoa = pessoaRepository.findByIdAndEscolaId(aluno.getPessoaId(), escolaId)
                .orElseThrow(AlunoService::naoEncontrado);
        return AlunoResponse.de(aluno, pessoa, responsavelService.responsaveisDoAluno(aluno.getId(), escolaId));
    }

    /**
     * Cadastra o aluno (e, se informados, seus responsaveis) na escola do
     * usuario autenticado e cria o login dele. O request nao carrega escola:
     * mesmo que carregasse, seria ignorado. E-mail ja usado como login barra
     * o cadastro inteiro (409). Responsavel com CPF ja cadastrado na escola e
     * reaproveitado (ver {@link ResponsavelService}).
     */
    @Transactional
    public AlunoResponse cadastrar(AlunoRequest request) {
        validar(request);
        UUID escolaId = isolamentoEscola.escolaIdAtual();
        if (alunoRepository.existsByEscolaIdAndMatricula(escolaId, request.matricula().trim())) {
            throw new NumeroMatriculaJaCadastradoException();
        }

        Pessoa pessoaAluno = criarPessoa(escolaId, request.nome(), request.cpf(), request.dataNascimento(),
                request.email() == null ? null : request.email().trim(), null, request.endereco());
        pessoaPapelRepository
                .save(PessoaPapel.builder().pessoaId(pessoaAluno.getId()).papel(PapelPessoa.ALUNO).build());
        Usuario usuario = acessoService.enviarAcessoInicial(pessoaAluno, PerfilUsuario.ALUNO);

        Aluno aluno = alunoRepository.save(Aluno.builder()
                .escolaId(escolaId)
                .pessoaId(pessoaAluno.getId())
                .usuarioId(usuario.getId())
                .matricula(request.matricula().trim())
                .genero(request.genero())
                .build());

        List<ResponsavelResponse> responsaveis = responsavelService.vincularAoNovoAluno(aluno, request.responsaveis());
        auditoriaService.registrar(OperacaoAuditoria.CRIAR, EntidadeAuditada.ALUNO, aluno.getId());
        return AlunoResponse.de(aluno, pessoaAluno, responsaveis);
    }

    /**
     * Edita os dados do aluno: substitui o registro inteiro (campo opcional
     * nulo apaga o valor anterior). Os responsaveis nao mudam por aqui: o que
     * vier em {@code responsaveis} e ignorado (use
     * {@code /alunos/{id}/responsaveis}). Trocar o e-mail troca o login.
     */
    @Transactional
    public AlunoResponse atualizar(UUID id, AlunoRequest request) {
        validar(request);
        UUID escolaId = isolamentoEscola.escolaIdAtual();
        Aluno aluno = alunoRepository.findByIdAndEscolaId(id, escolaId).orElseThrow(AlunoService::naoEncontrado);
        exigirNaoAnonimizado(aluno);
        Pessoa pessoa = pessoaRepository.findByIdAndEscolaId(aluno.getPessoaId(), escolaId)
                .orElseThrow(AlunoService::naoEncontrado);
        String matricula = request.matricula().trim();
        if (alunoRepository.existsByEscolaIdAndMatriculaAndIdNot(escolaId, matricula, aluno.getId())) {
            throw new NumeroMatriculaJaCadastradoException();
        }

        EnderecoRequest endereco = request.endereco();
        pessoa.setNome(request.nome().trim());
        pessoa.setCpf(request.cpf());
        pessoa.setEmail(request.email().trim());
        pessoa.setDataNascimento(request.dataNascimento() == null ? null : request.dataNascimento().toString());
        pessoa.setLogradouro(endereco == null ? null : endereco.logradouro());
        pessoa.setNumero(endereco == null ? null : endereco.numero());
        pessoa.setBairro(endereco == null ? null : endereco.bairro());
        pessoa.setCidade(endereco == null ? null : endereco.cidade());
        pessoa.setEstado(endereco == null ? null : endereco.estado());
        pessoa.setCep(endereco == null ? null : endereco.cep());
        aluno.setMatricula(matricula);
        aluno.setGenero(request.genero());
        aluno.setAtualizadoEm(Instant.now());

        acessoService.sincronizarLogin(pessoa);
        Pessoa pessoaSalva = pessoaRepository.save(pessoa);
        auditoriaService.registrar(OperacaoAuditoria.EDITAR, EntidadeAuditada.ALUNO, aluno.getId());
        return AlunoResponse.de(alunoRepository.save(aluno), pessoaSalva, responsavelService.responsaveisDoAluno(aluno.getId(), escolaId));
    }

    /**
     * Novo e-mail de primeiro acesso, com senha provisoria renovada: para
     * quem perdeu o e-mail ou deixou a senha expirar. Recusado (409) se o
     * aluno ja definiu a senha. Aluno anterior ao login automatico ganha o
     * acesso aqui, desde que tenha e-mail e CPF.
     */
    @Transactional
    public void reenviarAcesso(UUID id) {
        UUID escolaId = isolamentoEscola.escolaIdAtual();
        Aluno aluno = alunoRepository.findByIdAndEscolaId(id, escolaId).orElseThrow(AlunoService::naoEncontrado);
        exigirNaoAnonimizado(aluno);
        Pessoa pessoa = pessoaRepository.findByIdAndEscolaId(aluno.getPessoaId(), escolaId)
                .orElseThrow(AlunoService::naoEncontrado);
        Usuario usuario = acessoService.enviarAcessoInicial(pessoa, PerfilUsuario.ALUNO);
        if (!usuario.getId().equals(aluno.getUsuarioId())) {
            aluno.setUsuarioId(usuario.getId());
            alunoRepository.save(aluno);
        }
        auditoriaService.registrar(OperacaoAuditoria.REENVIAR_ACESSO, EntidadeAuditada.ALUNO, aluno.getId());
    }

    private Pessoa criarPessoa(UUID escolaId, String nome, String cpf, java.time.LocalDate dataNascimento,
            String email, String telefone, EnderecoRequest endereco) {
        return pessoaRepository.save(Pessoa.builder()
                .escolaId(escolaId)
                .nome(nome)
                .cpf(cpf)
                .dataNascimento(dataNascimento == null ? null : dataNascimento.toString())
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
    }

    private void validar(AlunoRequest request) {
        if (request.nome() == null || request.nome().isBlank()) {
            throw new IllegalArgumentException("Nome do aluno e obrigatorio");
        }
        if (request.matricula() == null || request.matricula().isBlank()) {
            throw new IllegalArgumentException("Matricula do aluno e obrigatoria");
        }
        // Qualquer data passada vale; "hoje" e o dia na escola, nao em UTC.
        if (request.dataNascimento() != null
                && request.dataNascimento().isAfter(LocalDate.now(clock.withZone(FUSO_ESCOLA)))) {
            throw new IllegalArgumentException("Data de nascimento nao pode ser futura");
        }
    }

    /** Aluno anonimizado nao tem mais dado pessoal: nao e editado nem ganha acesso (409). */
    public static void exigirNaoAnonimizado(Aluno aluno) {
        if (aluno.isAnonimizado()) {
            throw new RegistroEmUsoException("Aluno anonimizado: os dados pessoais foram apagados");
        }
    }

    private static RecursoNaoEncontradoException naoEncontrado() {
        return new RecursoNaoEncontradoException("Aluno nao encontrado");
    }
}
