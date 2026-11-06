package br.pucminas.eduka.backend.service;

import java.time.Clock;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.pucminas.eduka.backend.dto.UsoEscolaResponse;
import br.pucminas.eduka.backend.repository.UsoEscolaRepository;

/**
 * Uso de cada escola para o SUPER_ADMIN (IU02): alunos, usuarios ativos,
 * cobrancas lancadas no mes e ultima atividade. Uma consulta para todas as
 * escolas.
 */
@Service
@Transactional(readOnly = true)
public class UsoEscolaService {

    private static final ZoneId FUSO_ESCOLA = ZoneId.of("America/Sao_Paulo");

    private final UsoEscolaRepository usoEscolaRepository;
    private final Clock clock;

    public UsoEscolaService(UsoEscolaRepository usoEscolaRepository, Clock clock) {
        this.usoEscolaRepository = usoEscolaRepository;
        this.clock = clock;
    }

    public List<UsoEscolaResponse> listar() {
        YearMonth mes = YearMonth.now(clock.withZone(FUSO_ESCOLA));
        Instant inicio = mes.atDay(1).atStartOfDay(FUSO_ESCOLA).toInstant();
        Instant fim = mes.plusMonths(1).atDay(1).atStartOfDay(FUSO_ESCOLA).toInstant();
        return usoEscolaRepository.usoPorEscola(inicio, fim).stream()
                .map(u -> new UsoEscolaResponse(u.getEscolaId(), u.getAlunos(), u.getUsuariosAtivos(), mes,
                        u.getCobrancasNoMes(), u.getUltimaAtividade()))
                .toList();
    }
}
