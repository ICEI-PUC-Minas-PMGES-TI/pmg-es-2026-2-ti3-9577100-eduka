package br.pucminas.eduka.backend.mock;

import java.text.Normalizer;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Nomes, CPFs, telefones, e-mails e enderecos ficticios, mas com cara de
 * reais, para o {@link MockDataSeeder}. A semente e fixa: todo banco
 * recriado ganha as mesmas pessoas.
 *
 * <p>
 * CPF com digitos verificadores validos (a tela valida), e-mail sempre em
 * {@value #DOMINIO} e telefone sempre de Belo Horizonte.
 * </p>
 */
class GeradorMock {

    static final String DOMINIO = "mock.eduka.com.br";

    private static final List<String> MENINAS = List.of("Alice", "Ana Clara", "Beatriz", "Camila", "Cecília",
            "Clara", "Eduarda", "Gabriela", "Giovanna", "Helena", "Isabela", "Júlia", "Laura", "Larissa", "Lívia",
            "Lorena", "Luana", "Manuela", "Mariana", "Marina", "Melissa", "Natália", "Rafaela", "Sofia",
            "Valentina", "Yasmin");
    private static final List<String> MENINOS = List.of("Arthur", "Bernardo", "Bruno", "Caio", "Daniel", "Davi",
            "Enzo", "Felipe", "Gabriel", "Guilherme", "Gustavo", "Heitor", "Henrique", "João Pedro", "Leonardo",
            "Lorenzo", "Lucas", "Matheus", "Miguel", "Murilo", "Nicolas", "Otávio", "Pedro", "Rafael", "Samuel",
            "Vinícius");
    private static final List<String> MULHERES = List.of("Adriana", "Aline", "Andréa", "Cláudia", "Cristiane",
            "Daniela", "Elaine", "Fabiana", "Juliana", "Kátia", "Luciana", "Márcia", "Patrícia", "Renata", "Sandra",
            "Silvia", "Simone", "Tatiane", "Vanessa", "Viviane");
    private static final List<String> HOMENS = List.of("Alexandre", "Anderson", "André", "Carlos", "Cláudio",
            "Eduardo", "Fábio", "Fernando", "Flávio", "Gilberto", "Leandro", "Marcelo", "Marcos", "Paulo", "Renato",
            "Ricardo", "Roberto", "Rodrigo", "Sérgio", "Wagner");
    private static final List<String> SOBRENOMES = List.of("Silva", "Santos", "Oliveira", "Souza", "Rodrigues",
            "Ferreira", "Alves", "Pereira", "Lima", "Gomes", "Costa", "Ribeiro", "Martins", "Carvalho", "Almeida",
            "Lopes", "Soares", "Fernandes", "Vieira", "Barbosa", "Rocha", "Dias", "Nascimento", "Andrade", "Moreira",
            "Nunes", "Marques", "Machado", "Mendes", "Freitas", "Cardoso", "Ramos", "Gonçalves", "Santana",
            "Teixeira", "Moura", "Campos", "Pinto", "Araújo", "Batista", "Fonseca", "Rezende", "Guimarães",
            "Coelho", "Tavares", "Castro", "Miranda", "Mello", "Duarte", "Assis");

    /** Logradouro, bairro e CEP de Belo Horizonte. */
    private static final List<String[]> RUAS = List.of(
            new String[] { "Rua Pernambuco", "Funcionários", "30130150" },
            new String[] { "Rua Grão Mogol", "Carmo", "30310010" },
            new String[] { "Rua Ceará", "Santa Efigênia", "30150310" },
            new String[] { "Rua Alvarenga Peixoto", "Lourdes", "30180120" },
            new String[] { "Rua Professor Morais", "Savassi", "30150370" },
            new String[] { "Rua Pium-í", "Anchieta", "30310080" },
            new String[] { "Rua Itapeva", "Sion", "30315410" },
            new String[] { "Rua Conselheiro Lafaiete", "Sagrada Família", "31030120" },
            new String[] { "Rua Platina", "Prado", "30411325" },
            new String[] { "Rua Silva Ortiz", "Santo Antônio", "30330240" },
            new String[] { "Rua Jacuí", "Renascença", "31110050" },
            new String[] { "Avenida Silviano Brandão", "Floresta", "31015000" });

    private final Random random;
    private final Set<String> cpfs = new HashSet<>();
    private final Set<String> emails = new HashSet<>();
    private final Set<String> nomes = new HashSet<>();

    GeradorMock(long semente) {
        this.random = new Random(semente);
    }

    Random random() {
        return random;
    }

    String mulher() {
        return sortear(MULHERES);
    }

    String homem() {
        return sortear(HOMENS);
    }

    String sobrenome() {
        return sortear(SOBRENOMES);
    }

    /** Nome completo de crianca ainda nao usado: nome + sobrenome da mae + do pai. */
    String nomeDeFilho(boolean menina, String sobrenomeMae, String sobrenomePai) {
        String nome;
        do {
            nome = sortear(menina ? MENINAS : MENINOS) + " " + sobrenomeMae + " " + sobrenomePai;
        } while (!nomes.add(nome));
        return nome;
    }

    String cpf() {
        String cpf;
        do {
            StringBuilder base = new StringBuilder();
            for (int i = 0; i < 9; i++) {
                base.append(random.nextInt(10));
            }
            base.append(digitoCpf(base, 10));
            base.append(digitoCpf(base, 11));
            cpf = base.toString();
        } while (cpf.chars().distinct().count() == 1 || !cpfs.add(cpf));
        return cpf;
    }

    String celular() {
        return "(31) 9%04d-%04d".formatted(8000 + random.nextInt(2000), random.nextInt(10000));
    }

    /** {@code nome.ultimosobrenome@dominio}, com numero no fim se ja existir. */
    String email(String nomeCompleto) {
        String[] partes = semAcento(nomeCompleto).toLowerCase().split(" ");
        String base = partes[0] + "." + partes[partes.length - 1];
        String email = base + "@" + DOMINIO;
        for (int i = 2; !emails.add(email); i++) {
            email = base + i + "@" + DOMINIO;
        }
        return email;
    }

    /** Logradouro, numero, bairro e CEP. */
    String[] endereco() {
        String[] rua = sortear(RUAS);
        return new String[] { rua[0], String.valueOf(20 + random.nextInt(1800)), rua[1], rua[2] };
    }

    boolean chance(double probabilidade) {
        return random.nextDouble() < probabilidade;
    }

    int entre(int minimo, int maximo) {
        return minimo + random.nextInt(maximo - minimo + 1);
    }

    private <T> T sortear(List<T> lista) {
        return lista.get(random.nextInt(lista.size()));
    }

    private static int digitoCpf(CharSequence digitos, int peso) {
        int soma = 0;
        for (int i = 0; i < peso - 1; i++) {
            soma += (digitos.charAt(i) - '0') * (peso - i);
        }
        int resto = soma % 11;
        return resto < 2 ? 0 : 11 - resto;
    }

    private static String semAcento(String texto) {
        return Normalizer.normalize(texto, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
    }
}
