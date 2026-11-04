package br.pucminas.eduka.backend.service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.pucminas.eduka.backend.domain.HorarioAula;
import br.pucminas.eduka.backend.domain.Turma;
import br.pucminas.eduka.backend.dto.DefinirHorariosRequest;
import br.pucminas.eduka.backend.dto.DefinirHorariosRequest.Horario;
import br.pucminas.eduka.backend.dto.HorarioAulaResponse;
import br.pucminas.eduka.backend.exception.RecursoNaoEncontradoException;
import br.pucminas.eduka.backend.repository.HorarioAulaRepository;
import br.pucminas.eduka.backend.repository.TurmaRepository;
import br.pucminas.eduka.backend.security.IsolamentoEscola;

/**
 * Horario das aulas da turma (IU10 e IU13): a secretaria define em que dias
 * e horas cada disciplina tem aula; o professor ve o das turmas dele.
 */
@Service
public class HorarioAulaService {

    private final HorarioAulaRepository horarioRepository;
    private final TurmaRepository turmaRepository;
    private final IsolamentoEscola isolamentoEscola;

    public HorarioAulaService(HorarioAulaRepository horarioRepository, TurmaRepository turmaRepository,
            IsolamentoEscola isolamentoEscola) {
        this.horarioRepository = horarioRepository;
        this.turmaRepository = turmaRepository;
        this.isolamentoEscola = isolamentoEscola;
    }

    /** Aulas da turma, por dia e hora. 404 se a turma nao e da escola. */
    @Transactional(readOnly = true)
    public List<HorarioAulaResponse> listarDaTurma(Long turmaId) {
        UUID escolaId = isolamentoEscola.escolaIdAtual();
        turmaDaEscola(turmaId, escolaId);
        return horarioRepository.findAllByEscolaIdAndTurmaIdOrderByDiaSemanaAscHoraInicioAsc(escolaId, turmaId)
                .stream().map(HorarioAulaResponse::de).toList();
    }

    /**
     * Substitui os horarios da disciplina na turma e devolve os da turma toda.
     *
     * @throws IllegalArgumentException disciplina fora da grade da turma,
     *                                  horario repetido no pedido, ou a turma
     *                                  ja tem outra aula no mesmo dia e hora
     */
    @Transactional
    public List<HorarioAulaResponse> definir(Long turmaId, Long disciplinaId, DefinirHorariosRequest request) {
        UUID escolaId = isolamentoEscola.escolaIdAtual();
        Turma turma = turmaDaEscola(turmaId, escolaId);
        if (turma.getDisciplinas().stream().noneMatch(d -> d.getId().equals(disciplinaId))) {
            throw new IllegalArgumentException("Disciplina fora da grade da turma");
        }

        Set<String> pedidos = new HashSet<>();
        for (Horario horario : request.horarios()) {
            if (!pedidos.add(chave(horario.diaSemana(), horario))) {
                throw new IllegalArgumentException("Horario repetido no pedido");
            }
        }
        boolean conflita = horarioRepository
                .findAllByEscolaIdAndTurmaIdOrderByDiaSemanaAscHoraInicioAsc(escolaId, turmaId).stream()
                .filter(h -> !h.getDisciplinaId().equals(disciplinaId))
                .anyMatch(h -> pedidos.contains(h.getDiaSemana() + "|" + h.getHoraInicio()));
        if (conflita) {
            throw new IllegalArgumentException("A turma ja tem outra aula nesse dia e horario");
        }

        horarioRepository.deleteAllByEscolaIdAndTurmaIdAndDisciplinaId(escolaId, turmaId, disciplinaId);
        // No flush o Hibernate insere antes de apagar: sem isto, regravar o mesmo horario bate no indice unico.
        horarioRepository.flush();
        try {
            horarioRepository.saveAllAndFlush(request.horarios().stream()
                    .map(h -> HorarioAula.builder()
                            .escolaId(escolaId)
                            .turmaId(turmaId)
                            .disciplinaId(disciplinaId)
                            .diaSemana((short) h.diaSemana())
                            .horaInicio(h.horaInicio())
                            .build())
                    .toList());
        } catch (DataIntegrityViolationException e) {
            // Outra aula gravada ao mesmo tempo no mesmo horario: o indice unico segura.
            throw new IllegalArgumentException("A turma ja tem outra aula nesse dia e horario");
        }
        return horarioRepository.findAllByEscolaIdAndTurmaIdOrderByDiaSemanaAscHoraInicioAsc(escolaId, turmaId)
                .stream().map(HorarioAulaResponse::de).toList();
    }

    private static String chave(int diaSemana, Horario horario) {
        return diaSemana + "|" + horario.horaInicio();
    }

    private Turma turmaDaEscola(Long turmaId, UUID escolaId) {
        return turmaRepository.findByIdAndEscolaId(turmaId, escolaId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Turma nao encontrada"));
    }
}
