package br.pucminas.eduka.backend.exception.academico;

/**
 * A aula sobrepoe outra da grade no mesmo dia: da propria turma
 * ({@code HORARIO_TURMA_CONFLITANTE}) ou do mesmo professor em qualquer turma
 * ({@code HORARIO_PROFESSOR_CONFLITANTE}). Aulas que so se encostam nao
 * conflitam.
 */
public class HorarioConflitanteException extends RuntimeException {

    public static final String TURMA = "HORARIO_TURMA_CONFLITANTE";
    public static final String PROFESSOR = "HORARIO_PROFESSOR_CONFLITANTE";

    private final String codigo;

    private HorarioConflitanteException(String codigo, String mensagem) {
        super(mensagem);
        this.codigo = codigo;
    }

    public static HorarioConflitanteException daTurma() {
        return new HorarioConflitanteException(TURMA, "A turma ja tem aula neste horario");
    }

    public static HorarioConflitanteException doProfessor() {
        return new HorarioConflitanteException(PROFESSOR, "O professor ja tem aula neste horario");
    }

    public String getCodigo() {
        return codigo;
    }
}
