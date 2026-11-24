# Política de retenção de dados pessoais do aluno (LGPD)

Requisito: **RNF05** (privacidade e LGPD). Tarefa: **S5-18** (#370). Depende de **S5-09** (#361, inativar e reativar aluno).

## 1. Objetivo

A LGPD (Lei 13.709/2018) diz que o dado pessoal deve ser eliminado ou anonimizado quando o tratamento termina (art. 15 e 16). A exceção é o que a lei manda guardar. Até a Sprint 4, o Eduka só anonimizava manualmente: o MASTER clicava em **Anonimizar** no detalhe do aluno (UC39). Esta política define **por quanto tempo** a escola guarda os dados de quem saiu e **quando o sistema anonimiza sozinho**.

## 2. Ciclo de vida do aluno

| Situação | O que acontece com os dados | Quem decide |
|---|---|---|
| **Ativo** (matriculado, trancado ou sem turma) | Todos os dados ficam guardados e o aluno usa o portal. | — |
| **Inativo** | O aluno saiu da escola. Os dados continuam guardados, mas o login fica bloqueado e ele não pode ser matriculado. A contagem do prazo de retenção começa aqui. | MASTER ou SECRETARIA, em Alunos |
| **Anonimizado** | Nome, CPF, nascimento, contatos, endereço, gênero e vínculo com os responsáveis são apagados. O histórico escolar e o financeiro ficam, sem nada que identifique o aluno. Não há volta. | O MASTER manualmente, a qualquer momento, ou a rotina de retenção (seção 3) |

## 3. Regra de retenção

**O aluno inativo há 5 anos é anonimizado automaticamente.**

- O prazo conta a partir da **inativação** (`alunos.inativado_em`). Reativar o aluno apaga essa data, e o prazo recomeça do zero numa nova inativação.
- A rotina diária `RetencaoDadosAlunoRotina` roda às 03:00 (horário de Brasília) e percorre todas as escolas.
- Valem as mesmas travas da anonimização manual. Se, no dia previsto, o aluno ainda tiver **matrícula ativa ou trancada** ou **cobrança em aberto**, a anonimização é **adiada**. A rotina tenta de novo todo dia até essas pendências serem resolvidas.
- Cada anonimização automática entra no registro de atividades (`LOG_AUDITORIA`) com a operação `ANONIMIZAR` e a origem `SISTEMA`, sem autor.
- O detalhe do aluno inativo mostra a data prevista para a anonimização. Assim, a escola pode reativá-lo ou exportar os dados antes, se precisar.

### Por que 5 anos

| Base | Prazo |
|---|---|
| Cobrança de mensalidade em atraso: prescrição da dívida líquida em documento (Código Civil, art. 206, §5º, I) | 5 anos |
| Guarda de documentos fiscais e contábeis que comprovam receitas (CTN, art. 173 e 174) | 5 anos |
| Defesa da escola em ação de consumo (CDC, art. 27) | 5 anos |

Depois desse prazo, a escola não precisa mais saber **quem** era o aluno para cobrar ou se defender. Os números (cobranças, pagamentos, notas e frequência) continuam guardados de forma anônima, para relatórios e estatísticas (LGPD, art. 12 e art. 16, IV).

### O que não muda

- **Histórico escolar oficial.** A escrituração escolar é dever da escola e deve ser guardada sem prazo. O Eduka não substitui o arquivo oficial da secretaria: antes de inativar um aluno, a escola deve emitir ou exportar o que precisar arquivar (**Exportar dados (PDF)** no detalhe do aluno).
- **Responsáveis.** O responsável é um titular à parte e pode ser responsável por outros alunos. A anonimização do aluno só desfaz o vínculo e não apaga o cadastro do responsável.
- **Pedido do titular.** O aluno (ou o responsável) pode pedir a anonimização antes do prazo, e o MASTER a faz manualmente (UC39), com as mesmas travas.
- **Equipe da escola e alunos ativos.** Esta política não trata desses casos.

## 4. Configuração

| Propriedade | Variável de ambiente | Padrão | Uso |
|---|---|---|---|
| `app.lgpd.retencao.anos` | `LGPD_RETENCAO_ANOS` | `5` | Anos de inatividade até a anonimização (mínimo 1) |
| `app.lgpd.retencao.cron` | — | `0 0 3 * * *` | Horário da rotina diária |

Mudar o prazo vale para todos os alunos inativos, inclusive os já inativados. Se o prazo diminuir, quem já passou do novo limite é anonimizado na execução seguinte.

## 5. Implantação

- A migration `V52__aluno_inativado_em.sql` cria a coluna `inativado_em`. Os alunos que já estavam inativos recebem a data da migração: ninguém é anonimizado sem que a escola tenha tido o prazo inteiro para reativá-lo.
- O aviso de privacidade passou para a **versão 1.1** e ganhou a seção "Por quanto tempo guardamos". Todos os usuários veem o aviso de novo no próximo acesso (IU29).

## 6. Onde está no código

| Parte | Arquivo |
|---|---|
| Prazo e data prevista | `service/privacidade/PoliticaRetencao.java` |
| Varredura das escolas | `service/privacidade/RetencaoDadosAlunoService.java` |
| Agendamento | `service/privacidade/RetencaoDadosAlunoRotina.java` |
| Anonimização (manual e automática) | `service/privacidade/DadosPessoaisAlunoService.java` |
| Escola no contexto, sem token | `security/ContextoDoSistema.java` |
| Teste com Postgres real | `integracao/RetencaoDadosAlunoBancoRealTest.java` |
