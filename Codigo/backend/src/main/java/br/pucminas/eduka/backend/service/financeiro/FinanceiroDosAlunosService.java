package br.pucminas.eduka.backend.service.financeiro;

import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.pucminas.eduka.backend.domain.acesso.PerfilUsuario;
import br.pucminas.eduka.backend.domain.escola.ModuloEscola;
import br.pucminas.eduka.backend.dto.financeiro.FinanceiroDoAlunoResponse;
import br.pucminas.eduka.backend.exception.escola.ModuloNaoContratadoException;
import br.pucminas.eduka.backend.repository.escola.EscolaRepository;
import br.pucminas.eduka.backend.repository.financeiro.CobrancaRepository;
import br.pucminas.eduka.backend.repository.financeiro.CobrancaRepository.FinanceiroPorAluno;
import br.pucminas.eduka.backend.security.IsolamentoEscola;
import br.pucminas.eduka.backend.security.PerfilNaoPermitidoException;
import br.pucminas.eduka.backend.security.UsuarioAutenticado;

/**
 * A situacao financeira de cada aluno da escola do token, para a lista de
 * alunos (IU07). So o MASTER e o FINANCEIRO veem, e so com a gestao
 * financeira contratada: a SECRETARIA usa a mesma lista sem o financeiro.
 */
@Service
@Transactional(readOnly = true)
public class FinanceiroDosAlunosService {

    private static final ZoneId FUSO_ESCOLA = ZoneId.of("America/Sao_Paulo");

    private final CobrancaRepository cobrancaRepository;
    private final EscolaRepository escolaRepository;
    private final IsolamentoEscola isolamentoEscola;
    private final Clock clock;

    public FinanceiroDosAlunosService(CobrancaRepository cobrancaRepository, EscolaRepository escolaRepository,
            IsolamentoEscola isolamentoEscola, Clock clock) {
        this.cobrancaRepository = cobrancaRepository;
        this.escolaRepository = escolaRepository;
        this.isolamentoEscola = isolamentoEscola;
        this.clock = clock;
    }

    /** Se o usuario atual ve o financeiro dos alunos. */
    public boolean disponivel() {
        UsuarioAutenticado usuario = isolamentoEscola.usuarioAtual();
        return perfilVeFinanceiro(usuario) && gestaoFinanceiraContratada(usuario.escolaId());
    }

    /**
     * Barra quem pede filtro ou ordem financeira sem poder ver o financeiro.
     *
     * @throws PerfilNaoPermitidoException  se o perfil nao e MASTER nem
     *                                      FINANCEIRO (403)
     * @throws ModuloNaoContratadoException se a escola nao tem a gestao
     *                                      financeira (403)
     */
    public void exigirDisponivel() {
        UsuarioAutenticado usuario = isolamentoEscola.usuarioAtual();
        if (!perfilVeFinanceiro(usuario)) {
            throw new PerfilNaoPermitidoException("Os filtros financeiros dos alunos sao do MASTER e do FINANCEIRO");
        }
        if (!gestaoFinanceiraContratada(usuario.escolaId())) {
            throw new ModuloNaoContratadoException(ModuloEscola.GESTAO_FINANCEIRA);
        }
    }

    /**
     * A situacao de cada aluno com cobranca nao cancelada. Quem nao esta no
     * mapa nao tem cobranca: {@link FinanceiroDoAlunoResponse#SEM_COBRANCA}.
     */
    public Map<UUID, FinanceiroDoAlunoResponse> porAluno() {
        UUID escolaId = isolamentoEscola.escolaIdAtual();
        LocalDate hoje = LocalDate.now(clock.withZone(FUSO_ESCOLA));
        YearMonth mes = YearMonth.from(hoje);
        return cobrancaRepository
                .financeiroPorAluno(escolaId, hoje, hoje.plusDays(FinanceiroDoAlunoResponse.DIAS_A_VENCER),
                        mes.atDay(1), mes.atEndOfMonth())
                .stream()
                .collect(Collectors.toMap(FinanceiroPorAluno::getAlunoId, situacao(hoje)));
    }

    private static Function<FinanceiroPorAluno, FinanceiroDoAlunoResponse> situacao(LocalDate hoje) {
        return t -> FinanceiroDoAlunoResponse.de(t.getEmAtraso(), t.getPendentesNoPrazo(), t.getValorEmAberto(),
                t.getValorEmAtraso(), t.getVencimentoMaisAntigoEmAtraso(), t.getProximoVencimento(),
                t.getValorVenceAte(), t.getMensalidadesNoMes() > 0, hoje);
    }

    private static boolean perfilVeFinanceiro(UsuarioAutenticado usuario) {
        PerfilUsuario perfil = usuario.perfilUsuario();
        return perfil == PerfilUsuario.MASTER || perfil == PerfilUsuario.FINANCEIRO;
    }

    private boolean gestaoFinanceiraContratada(UUID escolaId) {
        return ModuloEscola.dosNomes(escolaRepository.modulos(escolaId)).contains(ModuloEscola.GESTAO_FINANCEIRA);
    }
}
