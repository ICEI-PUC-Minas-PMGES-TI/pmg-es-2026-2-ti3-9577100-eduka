# Casos de Uso do Eduka

Diagramas UML e descrições de cada caso de uso, conferidos com o código da branch `developer` (commit `98c0355`) em 04/11/2026 e atualizados com o planejamento da Sprint 4.

| Conteúdo | Arquivo |
|---|---|
| Descrições no template da disciplina, com os diagramas e o Anexo A (versão de 04/11, commit `25a92f8`) | [Casos_de_Uso_Descritivos_Eduka.docx](descritivos/Casos_de_Uso_Descritivos_Eduka.docx) · [PDF](descritivos/Casos_de_Uso_Descritivos_Eduka.pdf) |
| Diagramas (imagem) | [diagramas/png/](diagramas/png/) |
| Diagramas (fonte PlantUML) | [diagramas/](diagramas/) |

São 39 casos de uso: 25 completos, 10 parciais e 4 planejados.

> O descritivo (DOCX/PDF) ainda não traz UC34 a UC41 nem as demais mudanças de situação desta revisão; só o UC12 foi revisado nele (14/11, baixa manual com valor, dia e forma). Até ser atualizado, vale este README e os diagramas.

## Índice por módulo

**Situação:**

- *Completo*: o fluxo está disponível ao ator pela interface, ou por integração no caso de UC30 a UC32.
- *Parcial*: parte do comportamento previsto não existe ou só está disponível pela API.
- *Planejado*: está no planejamento da Sprint 4 e ainda não existe no código. A coluna **Sprint 4** traz o ID da tarefa no planejamento.

### Administração da plataforma ([diagrama](diagramas/png/01-administracao-plataforma.png))

| ID | Caso de uso | Ator primário | Requisitos | Situação | Tela | Sprint 4 |
|---|---|---|---|---|---|---|
| UC01 | Cadastrar Escola | SUPER_ADMIN | RF01 | Parcial | `/super-admin/escolas` | — |
| UC02 | Gerenciar Escolas Cadastradas | SUPER_ADMIN | RF02, RF23, RF24 | Parcial | `/super-admin/escolas` | RF02.1 |
| UC27 | Gerenciar Faturas da Plataforma | SUPER_ADMIN | RF23, RF24 | Completo | `/super-admin/escolas/:id/faturas`, `/super-admin/assinaturas` | NOVA-5, NOVA-5.1 |
| UC28 | Pagar Fatura da Plataforma | MASTER | RF23, RF24 | Completo | `/master/plano-e-faturas`, `/master/recebimentos` | NOVA-5.2 |

### Acesso e usuários ([diagrama](diagramas/png/02-acesso-e-usuarios.png))

| ID | Caso de uso | Ator primário | Requisitos | Situação | Tela | Sprint 4 |
|---|---|---|---|---|---|---|
| UC03 | Autenticar Usuário | Usuário (todos os perfis) | RF03, RF04, RF24 | Parcial | `/login`, `/login/admin`, `/acesso-suspenso` | RF24.1 |
| UC38 | Aceitar Aviso de Privacidade | Usuário (todos os perfis) | RNF05 | Planejado | IU29 (primeiro acesso) | RNF05.1 |
| UC04 | Gerenciar Usuários da Escola | MASTER | RF07 | Parcial | Sem tela (API `/usuarios/secretarias`) | — |
| UC16 | Visualizar Dashboard | MASTER, SECRETARIA, FINANCEIRO, PROFESSOR, ALUNO | RF20, RF25 | Completo (Início do aluno planejado) | `/<perfil>/inicio` | RF20.3, RF20.4, FR.5, RF25.5 |
| UC37 | Consultar Registro de Atividades | MASTER | RF22 | Completo | `/master/atividades` (IU28), com filtros por pessoa, período, entidade e operação | RNF17.1, RNF17.2, RF22.2 |

### Gestão acadêmica ([diagrama](diagramas/png/03-gestao-academica.png))

| ID | Caso de uso | Ator primário | Requisitos | Situação | Tela | Sprint 4 |
|---|---|---|---|---|---|---|
| UC05 | Gerenciar Alunos | SECRETARIA, MASTER | RF08 | Parcial | `/secretaria/alunos`, `/master/alunos` | RF08.6 |
| UC29 | Gerenciar Responsáveis | SECRETARIA, MASTER | RF08, RF12 | Completo | No cadastro do aluno | RF12.2 |
| UC06 | Gerenciar Professores | SECRETARIA, MASTER | RF09 | Parcial | `/secretaria/professores`, `/master/professores` | RF09.4 |
| UC07 | Gerenciar Estrutura Acadêmica | SECRETARIA, MASTER | RF10 | Completo | `/secretaria/academico`, `/master/academico` | — |
| UC18 | Alocar Professor em Turmas e Disciplinas | SECRETARIA, MASTER | RF09, RF10 | Completo | `/secretaria/academico/alocacoes` e edição do professor | RF09.4 |
| UC34 | Montar Horário das Turmas | SECRETARIA, MASTER | RF10 | Completo | `/secretaria/academico/alocacoes` (IU26) | FR.4 |
| UC08 | Realizar Matrícula de Aluno | SECRETARIA, MASTER | RF11 | Completo | `/secretaria/matriculas`, `/master/matriculas` | — |
| UC39 | Exportar e Anonimizar Dados Pessoais | MASTER, SECRETARIA (exportar); MASTER (anonimizar) | RNF05 | Completo | Detalhe do aluno em `/secretaria/alunos`, `/master/alunos` | RNF05.2 |

### Gestão financeira ([diagrama](diagramas/png/04-gestao-financeira.png))

| ID | Caso de uso | Ator primário | Requisitos | Situação | Tela | Sprint 4 |
|---|---|---|---|---|---|---|
| UC09 | Gerenciar Cobranças | FINANCEIRO, MASTER | RF12, RF15, RF22 | Completo | `/financeiro/cobrancas`, `/master/cobrancas` | — |
| UC11 | Consultar e Filtrar Cobranças | FINANCEIRO, MASTER | RF14 | Parcial | `/financeiro/cobrancas`, `/master/cobrancas` | PAG.1, PAG.2 |
| UC12 | Registrar Pagamento Manual | FINANCEIRO, MASTER | RF16 | Completo | `/financeiro/cobrancas`, `/master/cobrancas` (Registrar pagamento, com valor, dia e forma) | — |
| UC13 | Consultar Histórico Financeiro do Aluno | ALUNO, MASTER, FINANCEIRO | RF17, RF25 | Parcial | `/aluno/pagamentos`; aba Financeiro do aluno em `/master/alunos` e `/financeiro/alunos` | RF17.1, RF17.2, RF17.3 |
| UC14 | Gerenciar Despesas | FINANCEIRO, MASTER | RF18 | Parcial | `/financeiro/despesas`, `/master/despesas` | RF18.1 |
| UC15 | Consultar Relatórios Financeiros | FINANCEIRO, MASTER | RF19 | Planejado | IU19 e IU19-A | RF19.1, RF19.2 |
| UC40 | Avaliar Risco de Atraso | FINANCEIRO | — (tarefas IA) | Planejado | Lista de Cobranças e Início do Financeiro | IA.1, IA.2 |
| UC33 | Ativar Recebimento Online | MASTER | RF16, RNF13 | Completo | `/master/recebimentos` | — |

### Portal do professor ([diagrama](diagramas/png/05-portal-do-professor.png))

| ID | Caso de uso | Ator primário | Requisitos | Situação | Tela | Sprint 4 |
|---|---|---|---|---|---|---|
| UC19 | Consultar Turmas e Disciplinas Alocadas | PROFESSOR | RF10 | Completo | `/professor/turmas` | FR.5 |
| UC20 | Cadastrar Avaliações | PROFESSOR | RF25 | Completo | `/professor/avaliacoes` | — |
| UC21 | Lançar Notas dos Alunos | PROFESSOR | RF25 | Completo | `/professor/avaliacoes` | — |
| UC35 | Registrar Aula e Chamada | PROFESSOR | RF25 | Completo | `/professor/chamada` (IU25) | FR.1, FR.2 |

### Portal do aluno ([diagrama](diagramas/png/06-portal-do-aluno.png))

| ID | Caso de uso | Ator primário | Requisitos | Situação | Tela | Sprint 4 |
|---|---|---|---|---|---|---|
| UC22 | Visualizar Meu Perfil | ALUNO | RF08, RF25 | Completo | `/aluno/meus-dados` | — |
| UC36 | Solicitar Correção de Dados | ALUNO | RF08, RF25 | Completo | `/aluno/meus-dados`; atendimento no cadastro do aluno | — |
| UC23 | Consultar Boletim Acadêmico | ALUNO | RF25 | Completo | `/aluno/boletim` | FR.3 |
| UC24 | Consultar Disciplinas e Turmas | ALUNO | RF25 | Parcial | `/aluno/meus-dados` (resumo) | — |
| UC25 | Consultar Agenda de Provas e Tarefas | ALUNO | RF25 | Completo | `/aluno/boletim?aba=provas` | — |
| UC26 | Pagar Cobrança Online | ALUNO | RF15, RF16, RF17 | Completo | `/aluno/pagamentos` | PO.4 |

### Rotinas e integrações ([diagrama](diagramas/png/07-rotinas-e-integracoes.png))

| ID | Caso de uso | Ator primário | Requisitos | Situação | Origem | Sprint 4 |
|---|---|---|---|---|---|---|
| UC30 | Confirmar Pagamento pelo Gateway | Gateway de Pagamento (Asaas) | RF15, RF16 | Completo | Webhook `/webhooks/asaas`, reprocessamento e reconciliação | RF16.1, NOVA-4, RF22.2 |
| UC31 | Marcar Cobranças e Faturas em Atraso | Tempo | RF15 | Completo | Rotina diária, às 00:05 | — |
| UC32 | Enviar Notificações por E-mail | Tempo | RF21 | Completo | Lembrete diário de vencimento e fila de envio | RF21.1, RF21.2 |
| UC41 | Suspender Escola por Inadimplência | Tempo | RF24 | Planejado | Rotina agendada | RF24.1 |

A [visão geral](diagramas/png/00-visao-geral.png) reúne todos os casos de uso. Os requisitos seguem o Documento de Visão e, para as telas das Sprints 2 a 4, o Documento de Interface de Usuário.

## O que mudou nesta revisão

### Casos de uso novos

| ID | Origem |
|---|---|
| UC34 Montar Horário das Turmas | Grade semanal da turma (dia, horário, disciplina e professor), montada a partir das alocações. Já implementado. |
| UC35 Registrar Aula e Chamada | Aula do dia, conteúdo, presença ou falta e histórico de aulas, com edição e exclusão da aula. Já implementado. |
| UC36 Solicitar Correção de Dados | O aluno pede correção em Meus dados e a secretaria atende o pedido no cadastro do aluno. Já implementado. |
| UC37 Consultar Registro de Atividades | Tela Registro de atividades (IU28), paginada, com filtros por pessoa, período, entidade e operação. Já implementado. |
| UC15 Consultar Relatórios Financeiros | Identificador reservado desde a Sprint 3, agora com tarefas na Sprint 4. |
| UC38, UC39, UC40, UC41 | Itens do planejamento da Sprint 4: aviso de privacidade, LGPD, risco de atraso e suspensão automática. |

### Mudanças de situação

| Caso de uso | Antes | Agora | Motivo |
|---|---|---|---|
| UC09 | Parcial | Completo | Editar e cancelar cobrança estão na tela, com matrícula. O MASTER tem a tela Cobranças. |
| UC29 | Parcial | Completo | O cadastro do aluno edita e desvincula responsáveis e marca o responsável financeiro. |
| UC02 | Parcial | Parcial | A suspensão já bloqueia o login (IU04). Falta o uso por escola no painel (RF02.1), e módulos, limite de usuários e vigência continuam sem restringir o uso. |
| UC13 | Parcial | Parcial | O MASTER e o FINANCEIRO consultam pela aba Financeiro do aluno (IU18), com atalhos para Cobranças e Nova cobrança. Falta o resumo no Início do aluno (IU27). |
| UC14 | Parcial | Parcial | Cancelar despesa e criar categoria estão na tela. Editar continua só na API. |
| UC05, UC06, UC07, UC08, UC11, UC18 | Só pela API para o MASTER | Com tela para o MASTER | Rotas `/master/...` reutilizam as telas da secretaria e do financeiro. |

### Novos comportamentos em casos de uso existentes

- **UC03:** usuário de escola suspensa é levado ao aviso de acesso suspenso (IU04).
- **UC06:** a alocação (UC18) também pode ser feita ao editar o professor.
- **UC07:** encerrar o período letivo e abrir matrículas.
- **UC21:** salvar rascunho das notas antes de lançar.
- **UC23:** faltas e percentual de frequência por disciplina, com alerta acima de 25% de faltas.
- **UC26 e UC13:** comprovante em PDF montado pelo Eduka.
- **UC30:** pagamento recebido depois do cancelamento é registrado e sinalizado. Rotinas reprocessam avisos com erro e reconciliam cobranças sem resposta.
- **UC32:** e-mails de cobrança criada, vencimento próximo (3 dias antes) e pagamento confirmado.

## Lacunas que continuam abertas

| UC | Situação |
|---|---|
| UC01 | Não há fluxo para criar o usuário MASTER de uma escola nova. |
| UC02 | Uso por escola no painel (RF02.1, Sprint 4). Módulos, limite de usuários e vigência não restringem o uso. |
| UC03 | Não há recuperação de senha nem renovação de sessão. |
| UC04 | Somente API e somente para o perfil SECRETARIA. |
| UC05 | Não há inativação de aluno. A data de nascimento só aceita até 2016 (RF08.6, Sprint 4). |
| UC06 | O reenvio do acesso ao professor existe só na API. |
| UC11 | A tela filtra por nome e situação, e por aluno quando aberta pela aba Financeiro dele. Período letivo e vencimento só na API. |
| UC13 | O resumo financeiro no Início do aluno (IU27, RF25.5) ainda não existe. |
| UC14 | Editar despesa só na API. |
| UC24 | A tela mostra só o resumo da turma atual. |

## Tarefas da Sprint 4 sem caso de uso próprio

São requisitos não funcionais ou trabalho interno, sem ator nem objetivo de usuário: RNF08.1 a RNF08.4 (documentação, protótipos, Swagger, DER), NOVA-6 (atalhos do Sandbox, só em desenvolvimento), NOVA-7 (spec de pagamentos), NOVA-8, RNF09.1 e RNF09.2 (testes), RNF10.1 (teste de carga), RNF14.1 (deploy), RNF02.1 (autorização), RNF06.1 (health check), RNF11.1 (backup), RNF12.1 (logs), RNF15.1 e RNF15.2 (360 px) e NOVA-2 (homologação no Sandbox).

## Identificadores sem descrição

| ID | Motivo |
|---|---|
| UC10 Anexar Boleto à Cobrança | Retirado do escopo em 15/10/2026. |
| UC17 Consultar Informações Acadêmicas e Financeiras Próprias | Consolidado em UC13 e UC22 a UC25, para evitar duplicidade. |

## Atores

| Ator | Papel |
|---|---|
| Usuário | Ator abstrato, especializado pelos seis perfis abaixo. Inicia UC03 e UC38. |
| SUPER_ADMIN | Administra a plataforma, as escolas e as faturas da assinatura. |
| MASTER | Gestor da escola. Usa as telas da secretaria e do financeiro, além do dashboard, recebimento online e plano e faturas. Usuários da escola e registro de atividades, só pela API. |
| SECRETARIA | Alunos, responsáveis, professores, estrutura acadêmica, alocações, horários, matrículas e pedidos de correção. |
| FINANCEIRO | Cobranças, despesas e dashboard financeiro. |
| PROFESSOR | Turmas alocadas, avaliações, notas, aulas e chamada. |
| ALUNO | Perfil, pedidos de correção, boletim com frequência, agenda, histórico financeiro e pagamento online próprios. |
| Gateway de Pagamento (Asaas) | Sistema externo. Gera cobranças e envia avisos. |
| Serviço de E-mail (Brevo) | Sistema externo. Entrega os e-mails. |
| Tempo | Dispara as rotinas agendadas. |

## Convenções dos diagramas

- **Generalização:** entre atores (perfis especializam *Usuário*) e entre casos de uso (*Pagar com PIX* especializa UC26).
- **`<<include>>`:** comportamento que faz parte de toda execução do caso de uso base.
- **`<<extend>>`:** comportamento opcional ou condicional, derivado de um fluxo alternativo.
- **Elipse amarela:** implementação parcial.
- **Elipse cinza tracejada:** planejado para a Sprint 4, ainda não existe no código.
- **Linha cinza com rótulo `API`:** o perfil tem permissão na API, mas a interface web não oferece tela.
- **Elipse sem `UCxx`:** fluxo alternativo da descrição, destacado para dar legibilidade. Não é um requisito novo.
- **Auditoria:** a gravação não aparece como caso de uso, por ser ação interna do sistema. A consulta do registro é o UC37.

## Diferenças em relação aos artefatos anteriores

As pastas [`../casos-de-uso-descritivo`](../casos-de-uso-descritivo/) e [`../casos-de-uso-diagramas`](../casos-de-uso-diagramas/) são da Sprint 3 e foram mantidas sem alteração. Esta pasta as substitui. Os identificadores foram preservados, mas o conteúdo foi corrigido onde divergia do código:

| Item anterior | Situação encontrada |
|---|---|
| Descritivo ia até UC17 | Há descrição para os casos de uso implementados até UC33. UC34 a UC41 ainda estão só nos diagramas e neste README. |
| *Estornar Pagamento* como extensão de UC12 | Não existe. Paga é situação final da cobrança. |
| *Recuperar Senha* como extensão de UC03 | Não existe. A tela só orienta procurar a secretaria. |
| *Inativar Aluno* em UC05 | Não existe. |
| MASTER gerencia SECRETARIA e FINANCEIRO em UC04 | Só SECRETARIA, e somente pela API. |
| *Registrar Operação na Auditoria* como `<<include>>` | Removido dos diagramas, por ser ação interna do sistema. |
| Dependências rotuladas `pré-condição` | Removidas, por não serem relacionamento UML. Constam nas precondições das descrições. |
| Boletim com média ponderada | O boletim mostra pontos obtidos, avaliados e distribuídos. |

## Como renderizar os diagramas

Os `.puml` são a fonte. Para gerar os PNGs (requer Java):

```bash
# a partir de Artefatos/casos-de-uso/diagramas
java -jar plantuml.jar -Playout=smetana -tpng -charset UTF-8 -o png *.puml
```

Esse comando substitui os PNGs atuais. As imagens incorporadas no DOCX precisam ser trocadas também.
