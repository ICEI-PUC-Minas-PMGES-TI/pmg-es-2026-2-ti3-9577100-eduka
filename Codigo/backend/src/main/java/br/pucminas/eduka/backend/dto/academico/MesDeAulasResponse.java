package br.pucminas.eduka.backend.dto.academico;

import br.pucminas.eduka.backend.repository.academico.AulaRepository;

/**
 * Um mes com aulas no historico da disciplina na turma: quantas aulas e o
 * total de presencas e de faltas das chamadas delas.
 *
 * @param mes "AAAA-MM"
 */
public record MesDeAulasResponse(String mes, long aulas, long presentes, long faltas) {

    public static MesDeAulasResponse de(AulaRepository.AulasDoMes linha) {
        return new MesDeAulasResponse("%04d-%02d".formatted(linha.getAno(), linha.getMes()), linha.getAulas(),
                linha.getPresentes(), linha.getFaltas());
    }
}
