package br.pucminas.eduka.backend.service.privacidade;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import br.pucminas.eduka.backend.domain.escola.Escola;
import br.pucminas.eduka.backend.repository.escola.EscolaRepository;
import br.pucminas.eduka.backend.repository.pessoas.AlunoRepository;
import br.pucminas.eduka.backend.security.ContextoDoSistema;

/**
 * Aplica a {@link PoliticaRetencao}: anonimiza, em todas as escolas, o aluno
 * inativo que ja passou do prazo. Quem ainda tem matricula ativa ou trancada,
 * ou cobranca em aberto, fica para a proxima execucao: valem as mesmas regras
 * da anonimizacao manual.
 *
 * <p>
 * Nao e transacional de proposito: cada aluno e anonimizado na propria
 * transacao, aberta ja com a escola no contexto ({@link ContextoDoSistema}).
 * Um aluno que falhar nao desfaz os outros.
 * </p>
 */
@Service
public class RetencaoDadosAlunoService {

    private static final Logger log = LoggerFactory.getLogger(RetencaoDadosAlunoService.class);

    private final EscolaRepository escolaRepository;
    private final AlunoRepository alunoRepository;
    private final DadosPessoaisAlunoService dadosPessoaisAlunoService;
    private final PoliticaRetencao politica;
    private final Clock clock;

    public RetencaoDadosAlunoService(EscolaRepository escolaRepository, AlunoRepository alunoRepository,
            DadosPessoaisAlunoService dadosPessoaisAlunoService, PoliticaRetencao politica, Clock clock) {
        this.escolaRepository = escolaRepository;
        this.alunoRepository = alunoRepository;
        this.dadosPessoaisAlunoService = dadosPessoaisAlunoService;
        this.politica = politica;
        this.clock = clock;
    }

    /**
     * @param anonimizados alunos anonimizados nesta execucao
     * @param adiados      alunos que ja passaram do prazo mas ainda tem
     *                     matricula vigente ou cobranca em aberto, ou que
     *                     deram erro
     */
    public record Resultado(int anonimizados, int adiados) {
    }

    public Resultado anonimizarVencidos() {
        Instant inativadosAte = politica.inativadosAte(clock.instant());
        AtomicInteger anonimizados = new AtomicInteger();
        AtomicInteger adiados = new AtomicInteger();
        for (UUID escolaId : escolaRepository.findAll().stream().map(Escola::getId).toList()) {
            ContextoDoSistema.naEscola(escolaId, () -> {
                List<UUID> vencidos = alunoRepository.listarInativosAte(escolaId, inativadosAte);
                for (UUID alunoId : vencidos) {
                    try {
                        if (dadosPessoaisAlunoService.anonimizarPorRetencao(alunoId, escolaId)) {
                            anonimizados.incrementAndGet();
                        } else {
                            adiados.incrementAndGet();
                        }
                    } catch (RuntimeException e) {
                        adiados.incrementAndGet();
                        log.warn("Retencao: falha ao anonimizar o aluno {} da escola {}", alunoId, escolaId, e);
                    }
                }
            });
        }
        return new Resultado(anonimizados.get(), adiados.get());
    }
}
