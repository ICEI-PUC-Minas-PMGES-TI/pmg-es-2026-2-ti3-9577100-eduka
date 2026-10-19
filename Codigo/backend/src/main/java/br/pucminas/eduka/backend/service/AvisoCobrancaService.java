package br.pucminas.eduka.backend.service;

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

import br.pucminas.eduka.backend.domain.Escola;
import br.pucminas.eduka.backend.domain.Pessoa;
import br.pucminas.eduka.backend.repository.EscolaRepository;
import br.pucminas.eduka.backend.repository.PessoaRepository;

/**
 * Transforma cobrancas em {@link AvisoCobranca}: escolhe o destinatario,
 * decifra os nomes e publica o evento. O envio fica com
 * {@link CobrancaEmailListener}, depois do commit.
 *
 * <p>
 * Destinatario: o responsavel financeiro da cobranca, se ela tem um e ele tem
 * e-mail; senao, o proprio aluno. Sem e-mail nenhum, o aviso e descartado
 * (fica so no log, sem dado pessoal).
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
    private final ApplicationEventPublisher eventos;

    public AvisoCobrancaService(PessoaRepository pessoaRepository, EscolaRepository escolaRepository,
            ApplicationEventPublisher eventos) {
        this.pessoaRepository = pessoaRepository;
        this.escolaRepository = escolaRepository;
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
        Map<UUID, Pessoa> pessoas = pessoasDe(cobrancas);

        int publicados = 0;
        for (CobrancaParaAviso cobranca : cobrancas) {
            Pessoa aluno = pessoas.get(cobranca.alunoPessoaId());
            Pessoa responsavel = cobranca.responsavelPessoaId() == null ? null
                    : pessoas.get(cobranca.responsavelPessoaId());
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

    /** Aluno e responsavel de todas as cobrancas, buscados por escola (Pessoa nao tem filtro de tenant). */
    private Map<UUID, Pessoa> pessoasDe(List<CobrancaParaAviso> cobrancas) {
        return cobrancas.stream()
                .collect(Collectors.groupingBy(CobrancaParaAviso::escolaId))
                .entrySet().stream()
                .flatMap(porEscola -> {
                    List<UUID> ids = porEscola.getValue().stream()
                            .flatMap(c -> Stream.of(c.alunoPessoaId(), c.responsavelPessoaId()))
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
