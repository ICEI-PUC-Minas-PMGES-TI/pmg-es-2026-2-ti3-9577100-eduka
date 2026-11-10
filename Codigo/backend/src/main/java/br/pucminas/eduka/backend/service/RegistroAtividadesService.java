package br.pucminas.eduka.backend.service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.pucminas.eduka.backend.domain.EntidadeAuditada;
import br.pucminas.eduka.backend.domain.LogAuditoria;
import br.pucminas.eduka.backend.domain.OperacaoAuditoria;
import br.pucminas.eduka.backend.domain.Pessoa;
import br.pucminas.eduka.backend.domain.Usuario;
import br.pucminas.eduka.backend.dto.AtividadeResponse;
import br.pucminas.eduka.backend.dto.AutorAtividadeResponse;
import br.pucminas.eduka.backend.dto.Ordenacao;
import br.pucminas.eduka.backend.dto.PaginaResponse;
import br.pucminas.eduka.backend.dto.Paginacao;
import br.pucminas.eduka.backend.repository.LogAuditoriaRepository;
import br.pucminas.eduka.backend.repository.PessoaRepository;
import br.pucminas.eduka.backend.repository.UsuarioRepository;
import br.pucminas.eduka.backend.security.IsolamentoEscola;
import jakarta.persistence.criteria.Predicate;

/**
 * Leitura do LOG_AUDITORIA da escola do token: o Registro de atividades
 * (IU28). So le; quem grava e o {@link AuditoriaService}.
 */
@Service
public class RegistroAtividadesService {

    /** O periodo segue o calendario da escola, nao o de UTC. */
    private static final ZoneId FUSO_ESCOLA = ZoneId.of("America/Sao_Paulo");

    private final LogAuditoriaRepository logAuditoriaRepository;
    private final UsuarioRepository usuarioRepository;
    private final PessoaRepository pessoaRepository;
    private final IsolamentoEscola isolamentoEscola;

    public RegistroAtividadesService(LogAuditoriaRepository logAuditoriaRepository,
            UsuarioRepository usuarioRepository, PessoaRepository pessoaRepository,
            IsolamentoEscola isolamentoEscola) {
        this.logAuditoriaRepository = logAuditoriaRepository;
        this.usuarioRepository = usuarioRepository;
        this.pessoaRepository = pessoaRepository;
        this.isolamentoEscola = isolamentoEscola;
    }

    /**
     * Pagina das atividades da escola pela data, da mais recente por padrao.
     * Filtros opcionais: quem fez, periodo ({@code de} e {@code ate}
     * inclusivos, no dia da escola), entidade e operacao.
     *
     * @throws IllegalArgumentException se o periodo esta invertido
     */
    @Transactional(readOnly = true)
    public PaginaResponse<AtividadeResponse> listar(UUID usuarioId, LocalDate de, LocalDate ate,
            EntidadeAuditada entidade, OperacaoAuditoria operacao, Paginacao paginacao, Direction direcao) {
        if (de != null && ate != null && de.isAfter(ate)) {
            throw new IllegalArgumentException("de nao pode ser depois de ate");
        }
        UUID escolaId = isolamentoEscola.escolaIdAtual();
        Specification<LogAuditoria> filtro = (log, query, cb) -> {
            List<Predicate> condicoes = new ArrayList<>();
            condicoes.add(cb.equal(log.get("escolaId"), escolaId));
            if (usuarioId != null) {
                condicoes.add(cb.equal(log.get("usuarioId"), usuarioId));
            }
            if (de != null) {
                condicoes.add(cb.greaterThanOrEqualTo(log.get("dataHora"), inicioDoDia(de)));
            }
            if (ate != null) {
                condicoes.add(cb.lessThan(log.get("dataHora"), inicioDoDia(ate.plusDays(1))));
            }
            if (entidade != null) {
                condicoes.add(cb.equal(log.get("entidade"), entidade));
            }
            if (operacao != null) {
                condicoes.add(cb.equal(log.get("operacao"), operacao));
            }
            return cb.and(condicoes.toArray(Predicate[]::new));
        };
        Page<LogAuditoria> pagina = logAuditoriaRepository.findAll(filtro,
                paginacao.comOrdem(Ordenacao.noBanco(Ordenacao.ou(direcao, Direction.DESC), "dataHora")));

        Map<UUID, AutorAtividadeResponse> autores = autores(escolaId, pagina.getContent().stream()
                .map(LogAuditoria::getUsuarioId).filter(Objects::nonNull).collect(Collectors.toSet()));

        return PaginaResponse.de(pagina, log -> {
            AutorAtividadeResponse autor = log.getUsuarioId() == null ? null : autores.get(log.getUsuarioId());
            return new AtividadeResponse(log.getId(), log.getDataHora(), log.getOrigem(), log.getOperacao(),
                    log.getEntidade(), log.getRegistroId(), log.getUsuarioId(),
                    autor != null ? autor.nome() : null,
                    autor != null ? autor.perfil() : null);
        });
    }

    /**
     * Quem ja fez alguma atividade na escola, para o filtro por pessoa (IU28).
     * Por nome; quem nao e mais da escola (sem nome) fica no fim.
     */
    @Transactional(readOnly = true)
    public List<AutorAtividadeResponse> listarAutores() {
        UUID escolaId = isolamentoEscola.escolaIdAtual();
        return autores(escolaId, logAuditoriaRepository.listarAutores(escolaId)).values().stream()
                .sorted(Comparator.comparing(AutorAtividadeResponse::nome,
                        Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)))
                .toList();
    }

    /** Os autores e os nomes deles (cifrados), numa consulta cada; login apagado fica de fora. */
    private Map<UUID, AutorAtividadeResponse> autores(UUID escolaId, Collection<UUID> usuarioIds) {
        if (usuarioIds.isEmpty()) {
            return Map.of();
        }
        List<Usuario> usuarios = usuarioRepository.findAllById(Set.copyOf(usuarioIds));
        Set<UUID> pessoaIds = usuarios.stream().map(Usuario::getPessoaId).collect(Collectors.toSet());
        Map<UUID, String> nomes = pessoaIds.isEmpty() ? Map.of()
                : pessoaRepository.findAllByIdInAndEscolaId(pessoaIds, escolaId).stream()
                        .collect(Collectors.toMap(Pessoa::getId, Pessoa::getNome));
        return usuarios.stream().collect(Collectors.toMap(Usuario::getId,
                u -> new AutorAtividadeResponse(u.getId(), nomes.get(u.getPessoaId()), u.getPerfil())));
    }

    private static Instant inicioDoDia(LocalDate dia) {
        return dia.atStartOfDay(FUSO_ESCOLA).toInstant();
    }
}
