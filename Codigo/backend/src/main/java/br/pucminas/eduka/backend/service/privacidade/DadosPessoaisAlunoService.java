package br.pucminas.eduka.backend.service.privacidade;

import java.text.Normalizer;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.pucminas.eduka.backend.domain.academico.Disciplina;
import br.pucminas.eduka.backend.domain.academico.MatriculaTurma;
import br.pucminas.eduka.backend.domain.academico.StatusMatricula;
import br.pucminas.eduka.backend.domain.academico.Turma;
import br.pucminas.eduka.backend.domain.acesso.Usuario;
import br.pucminas.eduka.backend.domain.auditoria.EntidadeAuditada;
import br.pucminas.eduka.backend.domain.auditoria.OperacaoAuditoria;
import br.pucminas.eduka.backend.domain.comum.Endereco;
import br.pucminas.eduka.backend.domain.escola.Escola;
import br.pucminas.eduka.backend.domain.financeiro.Cobranca;
import br.pucminas.eduka.backend.domain.financeiro.StatusCobranca;
import br.pucminas.eduka.backend.domain.financeiro.TipoCobranca;
import br.pucminas.eduka.backend.domain.pessoas.Aluno;
import br.pucminas.eduka.backend.domain.pessoas.Pessoa;
import br.pucminas.eduka.backend.dto.pessoas.ResponsavelResponse;
import br.pucminas.eduka.backend.exception.comum.RecursoNaoEncontradoException;
import br.pucminas.eduka.backend.exception.comum.RegistroEmUsoException;
import br.pucminas.eduka.backend.repository.academico.FrequenciaRepository;
import br.pucminas.eduka.backend.repository.academico.MatriculaTurmaRepository;
import br.pucminas.eduka.backend.repository.academico.NotaRepository;
import br.pucminas.eduka.backend.repository.acesso.UsuarioRepository;
import br.pucminas.eduka.backend.repository.acesso.VerificacaoLoginRepository;
import br.pucminas.eduka.backend.repository.escola.EscolaRepository;
import br.pucminas.eduka.backend.repository.financeiro.CobrancaRepository;
import br.pucminas.eduka.backend.repository.notificacao.NotificacaoRepository;
import br.pucminas.eduka.backend.repository.pessoas.AlunoRepository;
import br.pucminas.eduka.backend.repository.pessoas.AlunoResponsavelRepository;
import br.pucminas.eduka.backend.repository.pessoas.PessoaRepository;
import br.pucminas.eduka.backend.repository.pessoas.SolicitacaoCorrecaoRepository;
import br.pucminas.eduka.backend.security.IsolamentoEscola;
import br.pucminas.eduka.backend.service.auditoria.AuditoriaService;
import br.pucminas.eduka.backend.service.pessoas.AlunoService;
import br.pucminas.eduka.backend.service.pessoas.ResponsavelService;

/**
 * Direitos do titular sobre o aluno (LGPD, RNF05.2, UC39).
 *
 * <ul>
 * <li><b>Exportar</b>: um PDF da escola com os dados do aluno (cadastro,
 * responsaveis, boletim, frequencia e mensalidades), para entregar a quem
 * pediu (art. 18, II, e art. 19).</li>
 * <li><b>Anonimizar</b>: apaga o dado pessoal do aluno que saiu da escola. As
 * cobrancas e os pagamentos ficam, com valores, datas e o responsavel
 * financeiro, porque a lei manda guardar o historico financeiro (art. 16, I);
 * matriculas, notas e frequencia ficam sem nada que identifique o aluno.</li>
 * </ul>
 *
 * <p>
 * A escola sai do token, como em todo o resto. As duas acoes entram no
 * registro de atividades.
 * </p>
 */
@Service
public class DadosPessoaisAlunoService {

    static final String NOME_ANONIMIZADO = "Aluno anonimizado";

    private static final ZoneId FUSO_ESCOLA = ZoneId.of("America/Sao_Paulo");

    /** Matricula que ainda prende o aluno a escola: nao da para anonimizar. */
    private static final Set<StatusMatricula> VIGENTES = EnumSet.of(StatusMatricula.ATIVA,
            StatusMatricula.TRANCADA);

    private final AlunoRepository alunoRepository;
    private final PessoaRepository pessoaRepository;
    private final UsuarioRepository usuarioRepository;
    private final EscolaRepository escolaRepository;
    private final ResponsavelService responsavelService;
    private final AlunoResponsavelRepository alunoResponsavelRepository;
    private final MatriculaTurmaRepository matriculaRepository;
    private final NotaRepository notaRepository;
    private final FrequenciaRepository frequenciaRepository;
    private final CobrancaRepository cobrancaRepository;
    private final SolicitacaoCorrecaoRepository solicitacaoRepository;
    private final NotificacaoRepository notificacaoRepository;
    private final VerificacaoLoginRepository verificacaoLoginRepository;
    private final PasswordEncoder passwordEncoder;
    private final IsolamentoEscola isolamentoEscola;
    private final AuditoriaService auditoriaService;
    private final Clock clock;

    public DadosPessoaisAlunoService(AlunoRepository alunoRepository, PessoaRepository pessoaRepository,
            UsuarioRepository usuarioRepository, EscolaRepository escolaRepository,
            ResponsavelService responsavelService, AlunoResponsavelRepository alunoResponsavelRepository,
            MatriculaTurmaRepository matriculaRepository, NotaRepository notaRepository,
            FrequenciaRepository frequenciaRepository, CobrancaRepository cobrancaRepository,
            SolicitacaoCorrecaoRepository solicitacaoRepository, NotificacaoRepository notificacaoRepository,
            VerificacaoLoginRepository verificacaoLoginRepository, PasswordEncoder passwordEncoder,
            IsolamentoEscola isolamentoEscola, AuditoriaService auditoriaService, Clock clock) {
        this.alunoRepository = alunoRepository;
        this.pessoaRepository = pessoaRepository;
        this.usuarioRepository = usuarioRepository;
        this.escolaRepository = escolaRepository;
        this.responsavelService = responsavelService;
        this.alunoResponsavelRepository = alunoResponsavelRepository;
        this.matriculaRepository = matriculaRepository;
        this.notaRepository = notaRepository;
        this.frequenciaRepository = frequenciaRepository;
        this.cobrancaRepository = cobrancaRepository;
        this.solicitacaoRepository = solicitacaoRepository;
        this.notificacaoRepository = notificacaoRepository;
        this.verificacaoLoginRepository = verificacaoLoginRepository;
        this.passwordEncoder = passwordEncoder;
        this.isolamentoEscola = isolamentoEscola;
        this.auditoriaService = auditoriaService;
        this.clock = clock;
    }

    /** PDF pronto para baixar, com o nome do arquivo. */
    public record Arquivo(String nome, byte[] conteudo) {
    }

    /**
     * Relatorio com todos os dados pessoais do aluno. Aluno anonimizado nao
     * tem mais o que exportar (409).
     */
    @Transactional
    public Arquivo exportar(UUID alunoId) {
        UUID escolaId = isolamentoEscola.escolaIdAtual();
        Aluno aluno = buscarAluno(alunoId, escolaId);
        AlunoService.exigirNaoAnonimizado(aluno);
        DadosPessoaisAluno dados = reunir(aluno, escolaId);
        byte[] pdf = DadosPessoaisPdf.gerar(dados);
        auditoriaService.registrar(OperacaoAuditoria.EXPORTAR_DADOS, EntidadeAuditada.ALUNO, aluno.getId());
        return new Arquivo(nomeDoArquivo(dados.pessoal().nome(), dados.geradoEm()), pdf);
    }

    /**
     * Apaga o dado pessoal do aluno que saiu da escola. Recusado (409) se ele
     * ainda tem matricula ativa ou trancada, ou cobranca em aberto: a escola
     * ainda precisa dele para dar aula ou cobrar.
     */
    @Transactional
    public void anonimizar(UUID alunoId) {
        UUID escolaId = isolamentoEscola.escolaIdAtual();
        Aluno aluno = buscarAluno(alunoId, escolaId);
        motivoParaNaoAnonimizar(aluno, escolaId).ifPresent(motivo -> {
            throw new RegistroEmUsoException(motivo);
        });
        apagarDadosPessoais(aluno, escolaId);
        auditoriaService.registrar(OperacaoAuditoria.ANONIMIZAR, EntidadeAuditada.ALUNO, aluno.getId());
    }

    /**
     * Anonimizacao da politica de retencao ({@link RetencaoDadosAlunoService}),
     * sem usuario: a escola tem que estar no contexto antes de a transacao
     * abrir ({@link br.pucminas.eduka.backend.security.ContextoDoSistema}).
     * Vale tudo o que vale para a manual; se o aluno ainda nao pode ser
     * anonimizado, nada muda.
     *
     * @return se o aluno foi anonimizado
     */
    @Transactional
    public boolean anonimizarPorRetencao(UUID alunoId, UUID escolaId) {
        Aluno aluno = buscarAluno(alunoId, escolaId);
        if (aluno.isAtivo()) {
            return false;
        }
        Optional<String> motivo = motivoParaNaoAnonimizar(aluno, escolaId);
        if (motivo.isPresent()) {
            return false;
        }
        apagarDadosPessoais(aluno, escolaId);
        auditoriaService.registrarDoSistema(OperacaoAuditoria.ANONIMIZAR, EntidadeAuditada.ALUNO, aluno.getId(),
                escolaId);
        return true;
    }

    /**
     * Por que o aluno ainda nao pode ser anonimizado: ja foi, ou a escola ainda
     * precisa dele para dar aula (matricula ativa ou trancada) ou cobrar
     * (cobranca em aberto). Vazio se pode.
     */
    private Optional<String> motivoParaNaoAnonimizar(Aluno aluno, UUID escolaId) {
        if (aluno.isAnonimizado()) {
            return Optional.of("Aluno ja anonimizado");
        }
        if (matriculaRepository.findAllByEscolaIdAndAlunoId(escolaId, aluno.getId()).stream()
                .anyMatch(m -> VIGENTES.contains(m.getStatus()))) {
            return Optional.of(
                    "Aluno com matricula ativa ou trancada; cancele ou conclua a matricula antes de anonimizar");
        }
        if (cobrancaRepository.findAllByEscolaIdAndAlunoId(escolaId, aluno.getId()).stream()
                .anyMatch(c -> c.getStatus().emAberto())) {
            return Optional.of("Aluno com cobranca em aberto; receba ou cancele a cobranca antes de anonimizar");
        }
        return Optional.empty();
    }

    /** Apaga o dado pessoal; o historico fica, sem nada que identifique o aluno. */
    private void apagarDadosPessoais(Aluno aluno, UUID escolaId) {
        List<MatriculaTurma> matriculas = matriculaRepository.findAllByEscolaIdAndAlunoId(escolaId, aluno.getId());
        List<Cobranca> cobrancas = cobrancaRepository.findAllByEscolaIdAndAlunoId(escolaId, aluno.getId());

        Pessoa pessoa = pessoaRepository.findByIdAndEscolaId(aluno.getPessoaId(), escolaId)
                .orElseThrow(DadosPessoaisAlunoService::naoEncontrado);
        pessoa.setNome(NOME_ANONIMIZADO);
        pessoa.setCpf(null);
        pessoa.setDataNascimento(null);
        pessoa.setEmail(null);
        pessoa.setTelefone(null);
        pessoa.setLogradouro(null);
        pessoa.setNumero(null);
        pessoa.setBairro(null);
        pessoa.setCidade(null);
        pessoa.setEstado(null);
        pessoa.setCep(null);
        pessoa.setAtivo(false);
        pessoaRepository.save(pessoa);

        List<UUID> referenciasDeEmail = new ArrayList<>(cobrancas.stream().map(Cobranca::getId).toList());
        if (aluno.getUsuarioId() != null) {
            usuarioRepository.findById(aluno.getUsuarioId()).ifPresent(this::desligarLogin);
            referenciasDeEmail.add(aluno.getUsuarioId());
        }
        if (!referenciasDeEmail.isEmpty()) {
            notificacaoRepository.apagarDasReferencias(referenciasDeEmail);
        }

        // Texto livre pode citar o aluno pelo nome.
        matriculas.stream().filter(m -> m.getObservacao() != null).forEach(m -> {
            m.setObservacao(null);
            matriculaRepository.save(m);
        });
        notaRepository.findAllByEscolaIdAndAlunoId(escolaId, aluno.getId()).stream()
                .filter(n -> n.getObservacao() != null)
                .forEach(n -> {
                    n.setObservacao(null);
                    notaRepository.save(n);
                });
        solicitacaoRepository.deleteAll(
                solicitacaoRepository.findAllByEscolaIdAndAlunoIdOrderByCriadoEmAsc(escolaId, aluno.getId()));
        // O cadastro do responsavel fica (ele e titular a parte); so deixa de apontar para o aluno.
        alunoResponsavelRepository.deleteAll(alunoResponsavelRepository.findAllById_AlunoId(aluno.getId()));

        Instant agora = clock.instant();
        aluno.setGenero(null);
        aluno.setAnonimizadoEm(agora);
        aluno.setAtualizadoEm(agora);
        alunoRepository.save(aluno);
    }

    /** O login para de existir: some o e-mail e nenhuma senha entra mais. */
    private void desligarLogin(Usuario usuario) {
        usuario.setLogin("anonimizado-" + usuario.getId());
        usuario.setSenhaHash(passwordEncoder.encode(UUID.randomUUID().toString()));
        usuario.setAtivo(false);
        usuario.setSenhaProvisoria(false);
        usuario.setSenhaProvisoriaExpiraEm(null);
        usuarioRepository.save(usuario);
        verificacaoLoginRepository.deleteByUsuarioId(usuario.getId());
    }

    private DadosPessoaisAluno reunir(Aluno aluno, UUID escolaId) {
        Pessoa pessoa = pessoaRepository.findByIdAndEscolaId(aluno.getPessoaId(), escolaId)
                .orElseThrow(DadosPessoaisAlunoService::naoEncontrado);
        LocalDate hoje = LocalDate.now(clock.withZone(FUSO_ESCOLA));

        List<MatriculaTurma> matriculas = matriculaRepository.findAllByEscolaIdAndAlunoId(escolaId, aluno.getId())
                .stream()
                .sorted(Comparator.comparing(MatriculaTurma::getDataMatricula).reversed())
                .toList();

        return new DadosPessoaisAluno(
                escola(escolaId),
                clock.instant(),
                nomeDeQuemPediu(escolaId),
                new DadosPessoaisAluno.Pessoal(pessoa.getNome(), pessoa.getCpf(),
                        pessoa.getDataNascimento() == null ? null : LocalDate.parse(pessoa.getDataNascimento()),
                        pessoa.getEmail(), pessoa.getTelefone(), aluno.getGenero(), endereco(pessoa),
                        aluno.getMatricula(), aluno.getCriadoEm()),
                responsaveis(aluno, escolaId),
                frequencias(aluno, escolaId, matriculas),
                cobrancaRepository.findAllByEscolaIdAndAlunoId(escolaId, aluno.getId()).stream()
                        .filter(c -> c.getTipo() == TipoCobranca.MENSALIDADE)
                        .sorted(Comparator.comparing(Cobranca::getDataVencimento).reversed())
                        .map(c -> new DadosPessoaisAluno.Mensalidade(c.getDescricao(), c.getDataVencimento(),
                                c.getValor(), rotulo(StatusCobranca.efetivo(c.getStatus(), c.getDataVencimento(), hoje)),
                                c.getPagaEm() == null ? null : LocalDate.ofInstant(c.getPagaEm(), FUSO_ESCOLA)))
                        .toList());
    }

    private DadosPessoaisAluno.Escola escola(UUID escolaId) {
        Escola escola = escolaRepository.findById(escolaId).orElseThrow(DadosPessoaisAlunoService::naoEncontrado);
        Endereco e = escola.getEndereco();
        String endereco = e == null ? null
                : juntar(", ", e.logradouro(), e.numero(), e.bairro(),
                        juntar(" - ", e.cidade(), e.estado()), e.cep() == null ? null : "CEP " + Formatos.cep(e.cep()));
        return new DadosPessoaisAluno.Escola(escola.getNome(), Formatos.documento(escola.getCnpjCpf()),
                escola.getEmail(), escola.getTelefone(), endereco);
    }

    /** Nome de quem gerou o relatorio na escola; o perfil, se a pessoa nao for achada. */
    private String nomeDeQuemPediu(UUID escolaId) {
        var usuario = isolamentoEscola.usuarioAtual();
        return usuarioRepository.findById(usuario.id())
                .flatMap(u -> pessoaRepository.findByIdAndEscolaId(u.getPessoaId(), escolaId))
                .map(Pessoa::getNome)
                .orElse(usuario.perfil());
    }

    private List<DadosPessoaisAluno.Responsavel> responsaveis(Aluno aluno, UUID escolaId) {
        List<ResponsavelResponse> vinculos = responsavelService.responsaveisDoAluno(aluno.getId(), escolaId);
        Map<UUID, Pessoa> pessoas = pessoaRepository
                .findAllByIdInAndEscolaId(vinculos.stream().map(ResponsavelResponse::pessoaId).toList(), escolaId)
                .stream().collect(Collectors.toMap(Pessoa::getId, Function.identity()));
        return vinculos.stream()
                .map(r -> new DadosPessoaisAluno.Responsavel(r.nome(), r.parentesco(),
                        pessoas.containsKey(r.pessoaId()) ? pessoas.get(r.pessoaId()).getCpf() : null,
                        r.email(), r.telefone(), r.financeiro()))
                .toList();
    }

    private List<DadosPessoaisAluno.FrequenciaDisciplina> frequencias(Aluno aluno, UUID escolaId,
            List<MatriculaTurma> matriculas) {
        List<DadosPessoaisAluno.FrequenciaDisciplina> linhas = new ArrayList<>();
        for (MatriculaTurma matricula : matriculas) {
            Turma turma = matricula.getTurma();
            Map<Long, String> disciplinas = turma.getDisciplinas().stream()
                    .collect(Collectors.toMap(Disciplina::getId, Disciplina::getNome));
            frequenciaRepository.resumirPorDisciplina(escolaId, aluno.getId(), turma.getId()).stream()
                    .filter(f -> f.getAulas() > 0)
                    .map(f -> new DadosPessoaisAluno.FrequenciaDisciplina(turma.getNome(),
                            disciplinas.getOrDefault(f.getDisciplinaId(), "Disciplina " + f.getDisciplinaId()),
                            f.getAulas(), f.getFaltas()))
                    .sorted(Comparator.comparing(DadosPessoaisAluno.FrequenciaDisciplina::disciplina,
                            String.CASE_INSENSITIVE_ORDER))
                    .forEach(linhas::add);
        }
        return linhas;
    }

    private static String endereco(Pessoa p) {
        String cidade = juntar(" - ", p.getCidade(), p.getEstado());
        return juntar(", ", p.getLogradouro(), p.getNumero(), p.getBairro(), cidade,
                p.getCep() == null ? null : "CEP " + Formatos.cep(p.getCep()));
    }

    private static String juntar(String separador, String... partes) {
        String junto = Stream.of(partes).filter(Objects::nonNull).map(String::trim).filter(s -> !s.isEmpty())
                .collect(Collectors.joining(separador));
        return junto.isEmpty() ? null : junto;
    }

    private static String rotulo(StatusCobranca status) {
        return switch (status) {
            case PENDENTE -> "Em aberto";
            case EM_ATRASO -> "Em atraso";
            case PAGA -> "Paga";
            case CANCELADA -> "Cancelada";
        };
    }

    /** "dados-do-aluno-ana-souza-2026-11-08.pdf": sem acento nem espaco, para qualquer sistema abrir. */
    static String nomeDoArquivo(String nome, Instant geradoEm) {
        String semAcento = Normalizer.normalize(nome, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        String slug = semAcento.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
        return "dados-do-aluno-" + (slug.isEmpty() ? "aluno" : slug) + "-"
                + LocalDate.ofInstant(geradoEm, FUSO_ESCOLA) + ".pdf";
    }

    private Aluno buscarAluno(UUID id, UUID escolaId) {
        return alunoRepository.findByIdAndEscolaId(id, escolaId).orElseThrow(DadosPessoaisAlunoService::naoEncontrado);
    }

    private static RecursoNaoEncontradoException naoEncontrado() {
        return new RecursoNaoEncontradoException("Aluno nao encontrado");
    }
}
