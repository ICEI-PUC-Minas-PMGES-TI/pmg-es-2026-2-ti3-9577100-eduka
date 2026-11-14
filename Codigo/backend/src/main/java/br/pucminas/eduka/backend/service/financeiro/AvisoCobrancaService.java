package br.pucminas.eduka.backend.service.financeiro;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import br.pucminas.eduka.backend.domain.escola.Escola;
import br.pucminas.eduka.backend.domain.pessoas.Pessoa;
import br.pucminas.eduka.backend.repository.escola.EscolaRepository;
import br.pucminas.eduka.backend.repository.pessoas.AlunoResponsavelRepository;
import br.pucminas.eduka.backend.repository.pessoas.PessoaRepository;

/**
 * Transforma cobrancas em {@link AvisoCobranca}: escolhe o destinatario,
 * decifra os nomes e publica o evento. O envio fica com
 * {@link CobrancaEmailListener}, depois do commit.
 *
 * <p>
 * Destinatario: quem paga a cobranca, pela mesma regra do provedor de
 * pagamento (R18, {@code RoteadorPagamento}): o responsavel informado na
 * cobranca; senao o responsavel financeiro do aluno; senao o proprio aluno.
 * Se quem paga nao tem e-mail, vai para o aluno. Sem e-mail nenhum, o aviso e
 * descartado (fica so no log, sem dado pessoal).
 * </p>
 *
 * <p>
 * Nao depende do usuario autenticado: a escola de cada pessoa vem da propria
 * cobranca, e serve tambem a rotina diaria.
 * </p>
 */
@Service
public class AvisoCobrancaService {

    private static final Logger log = LoggerFactory.getLogger(AvisoCobrancaService.class);

    private final PessoaRepository pessoaRepository;
    private final EscolaRepository escolaRepository;
    private final AlunoResponsavelRepository alunoResponsavelRepository;
    private final ApplicationEventPublisher eventos;

    public AvisoCobrancaService(PessoaRepository pessoaRepository, EscolaRepository escolaRepository,
            AlunoResponsavelRepository alunoResponsavelRepository, ApplicationEventPublisher eventos) {
        this.pessoaRepository = pessoaRepository;
        this.escolaRepository = escolaRepository;
        this.alunoResponsavelRepository = alunoResponsavelRepository;
        this.eventos = eventos;
    }

    public void publicar(AvisoCobranca.Tipo tipo, CobrancaParaAviso cobranca) {
        publicar(tipo, List.of(cobranca));
    }

    /** @return quantos avisos foram publicados (os sem destinatario ficam de fora) */
    public int publicar(AvisoCobranca.Tipo tipo, List<CobrancaParaAviso> cobrancas) {
        if (cobrancas.isEmpty()) {
            return 0;
        }
        Map<UUID, String> escolas = escolaRepository
                .findAllById(cobrancas.stream().map(CobrancaParaAviso::escolaId).distinct().toList()).stream()
                .collect(Collectors.toMap(Escola::getId, Escola::getNome));
        Map<UUID, UUID> pagadores = pagadoresDe(cobrancas);
        Map<UUID, Pessoa> pessoas = pessoasDe(cobrancas, pagadores);

        int publicados = 0;
        for (CobrancaParaAviso cobranca : cobrancas) {
            Pessoa aluno = pessoas.get(cobranca.alunoPessoaId());
            UUID pagadorId = pagadores.get(cobranca.id());
            Pessoa responsavel = pagadorId == null ? null : pessoas.get(pagadorId);
            boolean paraResponsavel = temEmail(responsavel);
            Pessoa destinatario = paraResponsavel ? responsavel : aluno;
            if (!temEmail(destinatario)) {
                log.warn("Aviso {} da cobranca {} sem destinatario com e-mail; nao enviado", tipo, cobranca.id());
                continue;
            }
            eventos.publishEvent(new AvisoCobranca(tipo, cobranca.id(), destinatario.getEmail().trim(),
                    destinatario.getNome(), !paraResponsavel, aluno == null ? null : aluno.getNome(),
                    escolas.get(cobranca.escolaId()), cobranca.descricao(), cobranca.valor(),
                    cobranca.dataVencimento(), cobranca.pagaEm()));
            publicados++;
        }
        return publicados;
    }

    /**
     * Cobranca -> pessoa do responsavel que paga (R18). Fica de fora a
     * cobranca que o proprio aluno paga. O responsavel financeiro de todos os
     * alunos sem responsavel na cobranca vem numa consulta so.
     */
    private Map<UUID, UUID> pagadoresDe(List<CobrancaParaAviso> cobrancas) {
        List<UUID> semResponsavel = cobrancas.stream()
                .filter(c -> c.responsavelPessoaId() == null)
                .map(CobrancaParaAviso::alunoPessoaId)
                .distinct()
                .toList();
        Map<UUID, UUID> financeiros = alunoResponsavelRepository.financeiroPorPessoaDoAluno(semResponsavel);
        Map<UUID, UUID> pagadores = new HashMap<>();
        for (CobrancaParaAviso c : cobrancas) {
            UUID pagador = c.responsavelPessoaId() != null ? c.responsavelPessoaId()
                    : financeiros.get(c.alunoPessoaId());
            if (pagador != null) {
                pagadores.put(c.id(), pagador);
            }
        }
        return pagadores;
    }

    /**
     * Aluno e pagador de todas as cobrancas, buscados por escola (Pessoa nao tem
     * filtro de tenant): pessoa de outra escola nao volta e nao recebe aviso.
     */
    private Map<UUID, Pessoa> pessoasDe(List<CobrancaParaAviso> cobrancas, Map<UUID, UUID> pagadores) {
        return cobrancas.stream()
                .collect(Collectors.groupingBy(CobrancaParaAviso::escolaId))
                .entrySet().stream()
                .flatMap(porEscola -> {
                    List<UUID> ids = porEscola.getValue().stream()
                            .flatMap(c -> Stream.of(c.alunoPessoaId(), pagadores.get(c.id())))
                            .filter(Objects::nonNull)
                            .distinct()
                            .toList();
                    return pessoaRepository.findAllByIdInAndEscolaId(ids, porEscola.getKey()).stream();
                })
                .collect(Collectors.toMap(Pessoa::getId, Function.identity(), (a, b) -> a));
    }

    private static boolean temEmail(Pessoa pessoa) {
        return pessoa != null && pessoa.getEmail() != null && !pessoa.getEmail().isBlank();
    }
}
