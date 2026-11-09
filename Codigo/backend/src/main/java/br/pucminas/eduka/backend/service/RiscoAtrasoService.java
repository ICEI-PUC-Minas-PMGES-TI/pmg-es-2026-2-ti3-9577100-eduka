package br.pucminas.eduka.backend.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.pucminas.eduka.backend.domain.Aluno;
import br.pucminas.eduka.backend.domain.NivelRiscoAtraso;
import br.pucminas.eduka.backend.domain.Pessoa;
import br.pucminas.eduka.backend.dto.RiscoAtrasoResponse;
import br.pucminas.eduka.backend.exception.RecursoNaoEncontradoException;
import br.pucminas.eduka.backend.repository.AlunoRepository;
import br.pucminas.eduka.backend.repository.CobrancaRepository;
import br.pucminas.eduka.backend.repository.CobrancaRepository.CobrancaParaRisco;
import br.pucminas.eduka.backend.repository.PessoaRepository;
import br.pucminas.eduka.backend.security.IsolamentoEscola;

/**
 * Risco de atraso dos alunos da escola do token, pelas regras de
 * {@link RegrasRiscoAtraso}. Le so as colunas que as regras usam, numa
 * consulta, e faz as contas aqui.
 */
@Service
@Transactional(readOnly = true)
public class RiscoAtrasoService {

    private static final ZoneId FUSO_ESCOLA = ZoneId.of("America/Sao_Paulo");

    /** Mais pontos primeiro; no empate, mais valor vencido em aberto, e depois o nome. */
    private static final Comparator<RiscoAtrasoResponse> MAIOR_RISCO_PRIMEIRO = Comparator
            .comparingInt(RiscoAtrasoResponse::pontos).reversed()
            .thenComparing((RiscoAtrasoResponse r) -> r.indicadores().valorEmAtraso(), Comparator.reverseOrder())
            .thenComparing(RiscoAtrasoResponse::alunoNome, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));

    private final CobrancaRepository cobrancaRepository;
    private final AlunoRepository alunoRepository;
    private final PessoaRepository pessoaRepository;
    private final IsolamentoEscola isolamentoEscola;
    private final Clock clock;

    public RiscoAtrasoService(CobrancaRepository cobrancaRepository, AlunoRepository alunoRepository,
            PessoaRepository pessoaRepository, IsolamentoEscola isolamentoEscola, Clock clock) {
        this.cobrancaRepository = cobrancaRepository;
        this.alunoRepository = alunoRepository;
        this.pessoaRepository = pessoaRepository;
        this.isolamentoEscola = isolamentoEscola;
        this.clock = clock;
    }

    /**
     * Risco de um aluno. Sem cobranca nenhuma, e baixo.
     *
     * @throws RecursoNaoEncontradoException se o aluno nao e da escola do token
     */
    public RiscoAtrasoResponse doAluno(UUID alunoId) {
        UUID escolaId = isolamentoEscola.escolaIdAtual();
        Aluno aluno = alunoRepository.findByIdAndEscolaId(alunoId, escolaId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Aluno nao encontrado"));
        LocalDate hoje = hoje();
        List<CobrancaParaRisco> cobrancas = cobrancaRepository.buscarParaRiscoDoAluno(escolaId, alunoId,
                RegrasRiscoAtraso.inicioDaJanela(hoje));
        String nome = pessoaRepository.findByIdAndEscolaId(aluno.getPessoaId(), escolaId).map(Pessoa::getNome)
                .orElse(null);
        return resposta(aluno, nome, cobrancas, hoje);
    }

    /**
     * Risco de cada aluno com cobranca na janela (ou vencida em aberto), do
     * maior para o menor.
     *
     * @param nivelMinimo so os alunos desse nivel para cima; nulo traz todos
     */
    public List<RiscoAtrasoResponse> daEscola(NivelRiscoAtraso nivelMinimo) {
        UUID escolaId = isolamentoEscola.escolaIdAtual();
        LocalDate hoje = hoje();
        Map<UUID, List<CobrancaParaRisco>> porAluno = cobrancaRepository
                .buscarParaRisco(escolaId, RegrasRiscoAtraso.inicioDaJanela(hoje)).stream()
                .collect(Collectors.groupingBy(CobrancaParaRisco::getAlunoId));
        if (porAluno.isEmpty()) {
            return List.of();
        }
        List<Aluno> alunos = alunoRepository.findAllById(porAluno.keySet()).stream()
                .filter(a -> escolaId.equals(a.getEscolaId()))
                .toList();
        Map<UUID, String> nomes = pessoaRepository
                .findAllByIdInAndEscolaId(alunos.stream().map(Aluno::getPessoaId).toList(), escolaId).stream()
                .collect(Collectors.toMap(Pessoa::getId, Pessoa::getNome));
        return alunos.stream()
                .map(a -> resposta(a, nomes.get(a.getPessoaId()), porAluno.get(a.getId()), hoje))
                .filter(r -> nivelMinimo == null || r.nivel().compareTo(nivelMinimo) >= 0)
                .sorted(MAIOR_RISCO_PRIMEIRO)
                .toList();
    }

    private static RiscoAtrasoResponse resposta(Aluno aluno, String nome, List<CobrancaParaRisco> cobrancas,
            LocalDate hoje) {
        RegrasRiscoAtraso.Resultado resultado = RegrasRiscoAtraso.avaliar(
                cobrancas.stream().map(paraRegras()).toList(), hoje);
        return new RiscoAtrasoResponse(aluno.getId(), nome, aluno.getMatricula(), resultado.nivel(),
                resultado.pontos(), resultado.motivo(), resultado.indicadores(), resultado.fatores());
    }

    /** Dia do pagamento: o do provedor no online; na baixa manual, o de PAGA_EM no fuso da escola. */
    private static Function<CobrancaParaRisco, RegrasRiscoAtraso.Cobranca> paraRegras() {
        return c -> {
            LocalDate pagaEm = c.getDataPagamento() != null ? c.getDataPagamento()
                    : c.getPagaEm() != null ? LocalDate.ofInstant(c.getPagaEm(), FUSO_ESCOLA) : null;
            return new RegrasRiscoAtraso.Cobranca(c.getValor(), c.getDataVencimento(), c.getStatus(), pagaEm);
        };
    }

    private LocalDate hoje() {
        return LocalDate.now(clock.withZone(FUSO_ESCOLA));
    }
}
