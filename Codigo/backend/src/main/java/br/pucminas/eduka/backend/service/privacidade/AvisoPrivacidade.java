package br.pucminas.eduka.backend.service.privacidade;

import java.time.LocalDate;
import java.util.List;

/**
 * O aviso de privacidade em vigor (IU29, RNF05). O texto fica aqui, com a
 * versao, para as duas nunca se separarem: mudou o texto, mude a
 * {@link #VERSAO} e a {@link #PUBLICADO_EM}, e todo usuario ve o aviso de novo
 * no proximo acesso.
 */
public final class AvisoPrivacidade {

    public static final String VERSAO = "1.1";
    public static final LocalDate PUBLICADO_EM = LocalDate.of(2026, 11, 15);

    /** Um item do resumo: o icone e da tela (nome do Icon do frontend). */
    public record ItemResumo(String icone, String titulo, String texto) {
    }

    /** Uma secao do texto completo. */
    public record Secao(String titulo, List<String> paragrafos) {
    }

    public static final List<ItemResumo> RESUMO = List.of(
            new ItemResumo("user", "Quais dados usamos",
                    "Nome, CPF, contatos e endereço, além das notas, da frequência e das cobranças, "
                            + "para a escola cuidar da sua vida escolar e financeira."),
            new ItemResumo("users", "Quem vê",
                    "Só a equipe da sua escola, cada um com o acesso do seu perfil. Outras escolas não veem nada."),
            new ItemResumo("lock", "Como protegemos",
                    "Seus dados pessoais ficam cifrados no banco, o acesso exige login e as alterações "
                            + "ficam registradas."),
            new ItemResumo("shield", "Seus direitos",
                    "Você pode pedir cópia, correção ou exclusão dos seus dados à secretaria da escola."));

    public static final List<Secao> SECOES = List.of(
            new Secao("Quem é responsável pelos dados", List.of(
                    "A sua escola é a controladora dos seus dados pessoais: é ela que decide para que eles são "
                            + "usados. O Eduka é o sistema que a escola usa para isso e trata os dados em nome dela.")),
            new Secao("Quais dados usamos e para quê", List.of(
                    "Dados cadastrais (nome, CPF, data de nascimento, e-mail, telefone e endereço) do aluno, dos "
                            + "responsáveis e da equipe da escola, para identificar cada pessoa e dar acesso ao sistema.",
                    "Dados escolares (matrículas, turmas, notas, aulas e frequência), para a escola registrar e "
                            + "acompanhar a vida escolar do aluno.",
                    "Dados financeiros (cobranças, pagamentos e quem paga cada cobrança), para a escola cobrar as "
                            + "mensalidades e dar baixa nos pagamentos.")),
            new Secao("Com quem os dados são compartilhados", List.of(
                    "Dentro da escola, cada perfil (gestão, secretaria, financeiro, professor e aluno) vê só o que "
                            + "precisa para o seu trabalho. Uma escola nunca vê os dados de outra.",
                    "Para os pagamentos online, os dados de quem paga (nome, CPF e e-mail) vão para o Asaas, a "
                            + "instituição de pagamento que gera o PIX e o boleto.",
                    "Os e-mails do sistema (acesso, cobranças e avisos) são enviados pelo Brevo, que recebe só o "
                            + "nome e o e-mail do destinatário.")),
            new Secao("Como protegemos", List.of(
                    "Nome, CPF, data de nascimento, contatos e endereço ficam cifrados no banco de dados. O acesso "
                            + "exige login com senha e pode exigir também um código enviado por e-mail.",
                    "As alterações feitas no sistema ficam registradas, com quem fez e quando.")),
            new Secao("Por quanto tempo guardamos", List.of(
                    "Os dados do aluno ficam guardados enquanto ele estuda na escola. Quando o aluno sai, a escola "
                            + "o inativa: o acesso ao sistema é bloqueado, mas os dados continuam guardados, porque a "
                            + "escola ainda pode precisar deles para cobranças e documentos.",
                    "Cinco anos depois da inativação, o sistema anonimiza o aluno automaticamente: nome, CPF, data "
                            + "de nascimento, contatos e endereço são apagados. As notas, a frequência e o histórico "
                            + "financeiro continuam, sem nada que identifique o aluno. Se nessa data ainda houver "
                            + "matrícula ou cobrança em aberto, a anonimização espera até elas serem encerradas.",
                    "Você também pode pedir a anonimização antes desse prazo à secretaria da escola.")),
            new Secao("Seus direitos", List.of(
                    "Pela Lei Geral de Proteção de Dados (Lei 13.709/2018), você pode pedir a confirmação de que "
                            + "seus dados são tratados, uma cópia deles, a correção do que estiver errado e a exclusão "
                            + "ou anonimização do que não for mais necessário.",
                    "Os pedidos são feitos na secretaria da escola, que exporta os seus dados e faz as correções. "
                            + "Dados que a escola precisa guardar por lei (como o histórico escolar) podem ser mantidos.")),
            new Secao("Mudanças neste aviso", List.of(
                    "Quando este aviso mudar, você o verá de novo no próximo acesso, com a nova versão, e precisará "
                            + "confirmar a leitura para continuar.")));

    private AvisoPrivacidade() {
    }
}
