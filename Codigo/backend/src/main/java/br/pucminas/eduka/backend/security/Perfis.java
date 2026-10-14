package br.pucminas.eduka.backend.security;

/**
 * Expressoes de {@code @PreAuthorize} usadas pelos controllers. Toda
 * autorizacao por perfil no nivel do endpoint referencia uma constante daqui
 * em vez de repetir strings de perfil:
 *
 * <pre>
 * &#64;PostMapping
 * &#64;PreAuthorize(Perfis.CADASTRO_ACADEMICO)
 * public ... cadastrar(...)
 * </pre>
 *
 * <p>
 * Isto e a segunda camada. A primeira e {@link RotasPorPerfil}, que decide
 * quem entra em cada modulo (a "rota-mae"); aqui se refina por acao dentro do
 * modulo (ler x escrever, por exemplo). Uma constante nunca deve liberar um
 * perfil que a rota-mae do modulo ja barra: o filtro barraria antes.
 * </p>
 *
 * <p>
 * SUPER_ADMIN nao aparece nos grupos de escola: administra a plataforma
 * (cadastro de escolas e seus usuarios), nao os dados de negocio de uma
 * escola.
 * </p>
 */
public final class Perfis {

    /** Plataforma: escolas, planos. */
    public static final String SUPER_ADMIN = "hasRole('SUPER_ADMIN')";

    /** Gestao da escola: configuracoes, auditoria. */
    public static final String GESTAO = "hasRole('MASTER')";

    /** Escrita no cadastro academico: alunos, turmas, matriculas... */
    public static final String CADASTRO_ACADEMICO = "hasAnyRole('MASTER', 'SECRETARIA')";

    /** Leitura do cadastro academico por quem trabalha na escola. */
    public static final String LEITURA_ACADEMICA =
            "hasAnyRole('MASTER', 'SECRETARIA', 'FINANCEIRO', 'PROFESSOR')";

    /**
     * Responsaveis dos alunos (cadastro e vinculo): secretaria cadastra junto
     * com o aluno, financeiro define quem paga. Professor fica de fora: o
     * cadastro traz CPF e contato.
     */
    public static final String RESPONSAVEIS = "hasAnyRole('MASTER', 'SECRETARIA', 'FINANCEIRO')";

    /** Cobrancas, pagamentos, despesas. */
    public static final String FINANCEIRO = "hasAnyRole('MASTER', 'FINANCEIRO')";

    /** Dashboards e relatorios (RF19, RF20). */
    public static final String RELATORIOS = "hasAnyRole('MASTER', 'SECRETARIA', 'FINANCEIRO')";

    /** Portal do professor: as proprias turmas. */
    public static final String PROFESSOR = "hasRole('PROFESSOR')";

    /** Portal do aluno: os proprios dados e cobrancas. */
    public static final String ALUNO = "hasRole('ALUNO')";

    private Perfis() {
    }
}
