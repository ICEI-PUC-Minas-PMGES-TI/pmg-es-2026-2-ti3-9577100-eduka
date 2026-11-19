# Casos de Uso do Eduka

Diagramas UML e descrições de cada caso de uso, conferidos com o código da branch `developer` (commit `39de40a`) em 17/11/2026 e atualizados com o planejamento da Sprint 5 (GitHub Projects, *Eduka - Project Development*).

| Conteúdo | Arquivo |
|---|---|
| Descrições no template da disciplina, com os diagramas e o Anexo A (versão de 04/11, commit `25a92f8`) | [Casos_de_Uso_Descritivos_Eduka.docx](descritivos/Casos_de_Uso_Descritivos_Eduka.docx) · [PDF](descritivos/Casos_de_Uso_Descritivos_Eduka.pdf) |
| Diagramas (imagem) | [diagramas/png/](diagramas/png/) |
| Diagramas (fonte PlantUML) | [diagramas/](diagramas/) |

São 40 casos de uso: 35 completos, 4 parciais e 1 planejado.

> As imagens incorporadas no descritivo (DOCX/PDF) ainda são as da Sprint 4. Até ele ser atualizado, vale este README e os diagramas.

## Índice por módulo

**Situação:**

- *Completo*: o fluxo está disponível ao ator pela interface, ou por integração e rotina no caso de UC30 a UC32 e UC41.
- *Parcial*: parte do comportamento previsto não existe ou só está disponível pela API.
- *Planejado*: está no planejamento da Sprint 5 e ainda não existe no código.

A coluna **Sprint 5** traz o ID da tarefa no planejamento. Tarefas marcadas com ✓ já foram entregues.

### Administração da plataforma ([diagrama](diagramas/png/01-administracao-plataforma.png))

| ID | Caso de uso | Ator primário | Requisitos | Situação | Tela | Sprint 5 |
|---|---|---|---|---|---|---|
| UC01 | Cadastrar Escola | SUPER_ADMIN | RF01 | Completo | `/super-admin/escolas` (cria o primeiro login MASTER) | S5-01 ✓ |
| UC02 | Gerenciar Escolas Cadastradas | SUPER_ADMIN | RF02, RF23, RF24 | Parcial | `/super-admin/escolas`, com o uso de cada escola | S5-11 |
| UC27 | Gerenciar Faturas da Plataforma | SUPER_ADMIN | RF23, RF24 | Completo | `/super-admin/escolas/:id/faturas`, `/super-admin/assinaturas` | — |
| UC28 | Pagar Fatura da Plataforma | MASTER | RF23, RF24 | Completo | `/master/plano-e-faturas`, `/master/recebimentos` | — |

### Acesso e usuários ([diagrama](diagramas/png/02-acesso-e-usuarios.png))

| ID | Caso de uso | Ator primário | Requisitos | Situação | Tela | Sprint 5 |
|---|---|---|---|---|---|---|
| UC03 | Autenticar Usuário | Usuário (todos os perfis) | RF03, RF04, RF24 | Completo | `/login`, `/login/admin`, `/acesso-suspenso`, recuperação de senha no login | S5-02 ✓, S5-04 ✓ |
| UC38 | Aceitar Aviso de Privacidade | Usuário (todos os perfis) | RNF05 | Completo | `/aviso-de-privacidade` (IU29, primeiro acesso) | — |
| UC04 | Gerenciar Usuários da Escola | MASTER | RF07 | Completo | `/master/equipe` (SECRETARIA e FINANCEIRO) | — |
| UC16 | Visualizar Dashboard | Usuário da escola (MASTER, SECRETARIA, FINANCEIRO, PROFESSOR, ALUNO) | RF20, RF25 | Completo | `/<perfil>/inicio` | — |
| UC37 | Consultar Registro de Atividades | MASTER | RF22 | Completo | `/master/atividades` (IU28) | S5-30 ✓ |

### Gestão acadêmica ([diagrama](diagramas/png/03-gestao-academica.png))

| ID | Caso de uso | Ator primário | Requisitos | Situação | Tela | Sprint 5 |
|---|---|---|---|---|---|---|
| UC05 | Gerenciar Alunos | SECRETARIA, MASTER | RF08 | Completo | `/secretaria/alunos`, `/master/alunos` | S5-09 ✓ |
| UC29 | Gerenciar Responsáveis | SECRETARIA, MASTER | RF08, RF12 | Completo | No cadastro do aluno | — |
| UC06 | Gerenciar Professores | SECRETARIA, MASTER | RF09 | Parcial | `/secretaria/professores`, `/master/professores` | S5-10 |
| UC07 | Gerenciar Estrutura Acadêmica | SECRETARIA, MASTER | RF10 | Completo | `/secretaria/academico`, `/master/academico` | S5-31 |
| UC18 | Alocar Professor em Turmas e Disciplinas | SECRETARIA, MASTER | RF09, RF10 | Completo | `/secretaria/academico/alocacoes` e edição do professor | — |
| UC34 | Montar Horário das Turmas | SECRETARIA, MASTER | RF10 | Completo | `/secretaria/academico/alocacoes` (IU26) | — |
| UC08 | Realizar Matrícula de Aluno | SECRETARIA, MASTER | RF11 | Completo | `/secretaria/matriculas`, `/master/matriculas` | S5-31 |
| UC39 | Exportar e Anonimizar Dados Pessoais | MASTER, SECRETARIA (exportar); MASTER (anonimizar); Tempo (anonimização automática) | RNF05 | Completo | Detalhe do aluno; rotina da [política de retenção](../arquitetura/politica-de-retencao-lgpd.md) | S5-18 ✓ |

### Gestão financeira ([diagrama](diagramas/png/04-gestao-financeira.png))

| ID | Caso de uso | Ator primário | Requisitos | Situação | Tela | Sprint 5 |
|---|---|---|---|---|---|---|
| UC09 | Gerenciar Cobranças | FINANCEIRO, MASTER | RF12, RF15, RF22 | Completo | `/financeiro/cobrancas`, `/master/cobrancas` | — |
| UC11 | Consultar e Filtrar Cobranças | FINANCEIRO, MASTER | RF14 | Completo | `/financeiro/cobrancas`, `/master/cobrancas` (nome, situação, aluno, período letivo e vencimento) | S5-06 ✓ |
| UC12 | Registrar Pagamento Manual | FINANCEIRO, MASTER | RF16 | Completo | `/financeiro/cobrancas`, `/master/cobrancas` (valor, dia e forma) | S5-05 ✓ |
| UC13 | Consultar Histórico Financeiro do Aluno | ALUNO, FINANCEIRO, MASTER | RF17, RF25 | Completo | `/aluno/pagamentos` e Início do aluno; aba Financeiro em `/financeiro/alunos`, `/master/alunos` | — |
| UC14 | Gerenciar Despesas | FINANCEIRO, MASTER | RF18 | Parcial | `/financeiro/despesas`, `/master/despesas` | S5-07, S5-08 |
| UC15 | Consultar Relatórios Financeiros | FINANCEIRO, MASTER | RF19 | Completo | `/financeiro/relatorios`, `/master/relatorios` (CSV e planilha) | — |
| UC40 | Avaliar Risco de Atraso | FINANCEIRO, MASTER | — (tarefas IA) | Completo | Cobranças, Início do financeiro e aba Financeiro do aluno | — |
| UC33 | Ativar Recebimento Online | MASTER | RF16, RNF13 | Completo | `/master/recebimentos` | — |

### Portal do professor ([diagrama](diagramas/png/05-portal-do-professor.png))

| ID | Caso de uso | Ator primário | Requisitos | Situação | Tela | Sprint 5 |
|---|---|---|---|---|---|---|
| UC19 | Consultar Turmas e Disciplinas Alocadas | PROFESSOR | RF10 | Completo | `/professor/turmas` | — |
| UC20 | Cadastrar Avaliações | PROFESSOR | RF25 | Completo | `/professor/avaliacoes` | — |
| UC21 | Lançar Notas dos Alunos | PROFESSOR | RF25 | Completo | `/professor/avaliacoes` | — |
| UC35 | Registrar Aula e Chamada | PROFESSOR | RF25 | Completo | `/professor/chamada` (IU25) | — |
| UC42 | Visualizar Perfil do Professor | PROFESSOR | RF09, RF25 | Planejado | Meu perfil (hoje "Em breve" no menu) | S5-13 |

### Portal do aluno ([diagrama](diagramas/png/06-portal-do-aluno.png))

| ID | Caso de uso | Ator primário | Requisitos | Situação | Tela | Sprint 5 |
|---|---|---|---|---|---|---|
| UC22 | Visualizar Meu Perfil | ALUNO | RF08, RF25 | Completo | `/aluno/meus-dados` | — |
| UC36 | Solicitar Correção de Dados | ALUNO | RF08, RF25 | Completo | `/aluno/meus-dados`; atendimento no cadastro do aluno | — |
| UC23 | Consultar Boletim Acadêmico | ALUNO | RF25 | Completo | `/aluno/boletim`, por ano letivo e bimestre | — |
| UC24 | Consultar Disciplinas e Turmas | ALUNO | RF25 | Parcial | `/aluno/meus-dados` (resumo da turma atual) | S5-12 |
| UC25 | Consultar Agenda de Provas e Tarefas | ALUNO | RF25 | Completo | `/aluno/boletim?aba=provas`, com filtro por bimestre | — |
| UC26 | Pagar Cobrança Online | ALUNO | RF15, RF16, RF17 | Completo | `/aluno/pagamentos` | — |

### Rotinas e integrações ([diagrama](diagramas/png/07-rotinas-e-integracoes.png))

| ID | Caso de uso | Ator primário | Requisitos | Situação | Origem | Sprint 5 |
|---|---|---|---|---|---|---|
| UC30 | Confirmar Pagamento pelo Gateway | Gateway de Pagamento (Asaas) | RF15, RF16 | Completo | Webhook `/webhooks/asaas`, reprocessamento e reconciliação | S5-33 |
| UC31 | Marcar Cobranças e Faturas em Atraso | Tempo | RF15 | Completo | Rotina diária, às 00:05 | — |
| UC32 | Enviar Notificações por E-mail | Tempo | RF21 | Completo | Lembrete diário de vencimento e fila de envio | — |
| UC41 | Suspender Escola por Inadimplência | Tempo | RF24 | Completo | Rotina agendada (extensão de UC31) | S5-33 |

A [visão geral](diagramas/png/00-visao-geral.png) reúne todos os casos de uso, agrupados por quem os usa. Os requisitos seguem o Documento de Visão e, para as telas das Sprints 2 a 5, o Documento de Interface de Usuário.

## O que mudou nesta revisão (Sprint 5)

### Caso de uso novo

| ID | Origem |
|---|---|
| UC42 Visualizar Perfil do Professor | Tarefa S5-13: tela Meu perfil do professor, hoje marcada como "Em breve" no menu. Planejado. |

### Mudanças de situação

| Caso de uso | Antes | Agora | Motivo |
|---|---|---|---|
| UC01 | Parcial | Completo | O cadastro da escola cria o primeiro login MASTER e envia o e-mail de primeiro acesso (S5-01). |
| UC03 | Parcial | Completo | Recuperação de senha por e-mail com código de validade curta (S5-02) e renovação da sessão com o refresh token (S5-04). |
| UC04 | Parcial (só API) | Completo | Tela Equipe do MASTER: cadastrar, editar, reenviar acesso, inativar e reativar SECRETARIA e FINANCEIRO. |
| UC05 | Parcial | Completo | Inativar e reativar aluno sem anonimizar (S5-09). A data de nascimento aceita até a data de hoje. |
| UC11 | Parcial | Completo | Filtros por período letivo e por vencimento na tela (S5-06). |
| UC13 | Parcial | Completo | Resumo financeiro e próxima mensalidade no Início do aluno; o FINANCEIRO consulta pela tela Alunos. |
| UC15, UC38, UC40, UC41 | Planejado | Completo | Entregues no fim da Sprint 4: relatórios com CSV e planilha, aviso de privacidade, risco de atraso e suspensão automática. |
| UC02 | Parcial | Parcial | O uso de cada escola já aparece no painel. Limite de usuários e módulos ainda não restringem o uso (S5-11, decisão de escopo pendente). |

### Novos comportamentos em casos de uso existentes

- **UC03:** *Recuperar Senha por E-mail* e *Renovar Sessão* como extensões.
- **UC23 e UC25:** boletim por ano letivo e bimestre; agenda de provas e tarefas com filtro por bimestre.
- **UC16:** Início do aluno com provas e tarefas por dia, painel de desempenho e próxima mensalidade.
- **UC37:** a auditoria passa a registrar estrutura acadêmica, alocações e horário, aulas e chamada, responsáveis, categorias de despesa e pedidos de correção (S5-30).
- **UC39:** anonimização automática do aluno inativo há 5 anos, disparada pelo Tempo (S5-18).

## Lacunas que continuam abertas

| UC | Situação | Sprint 5 |
|---|---|---|
| UC02 | Limite de usuários e módulos contratados não restringem o uso. | S5-11 |
| UC06 | O reenvio do acesso ao professor existe só na API. | S5-10 |
| UC14 | Editar despesa só na API; a despesa não tem responsável (RF18). | S5-07, S5-08 |
| UC24 | A tela mostra só o resumo da turma atual. | S5-12 |
| UC42 | Tela Meu perfil do professor não existe. | S5-13 |

As telas *Usuários* e *Configurações* do Super Admin (S5-14, S5-15) aparecem no menu como "Em breve" e aguardam decisão de escopo. Por isso não entram nos diagramas.

## Tarefas da Sprint 5 sem caso de uso próprio

São requisitos não funcionais ou trabalho interno, sem ator nem objetivo de usuário: S5-16 (Dependabot), S5-19 (360 px), S5-20 a S5-24 (documentação e README de instalação), S5-25 (bugs da homologação), S5-26 (dados da escola de demonstração), S5-28 e S5-29 (vídeo, relatório e apresentação), S5-31 (paginação nas telas), S5-32 e S5-33 (testes).

## Identificadores sem descrição

| ID | Motivo |
|---|---|
| UC10 Anexar Boleto à Cobrança | Retirado do escopo em 15/10/2026. |
| UC17 Consultar Informações Acadêmicas e Financeiras Próprias | Consolidado em UC13 e UC22 a UC25, para evitar duplicidade. |

## Atores

| Ator | Papel |
|---|---|
| Usuário | Ator abstrato, especializado por *Usuário da escola* e SUPER_ADMIN. Inicia UC03 e UC38. |
| Usuário da escola | Ator abstrato, especializado por MASTER, SECRETARIA, FINANCEIRO, PROFESSOR e ALUNO. Inicia UC16. |
| SUPER_ADMIN | Administra a plataforma, as escolas e as faturas da assinatura. |
| MASTER | Gestor da escola. Herda da SECRETARIA e do FINANCEIRO (usa todas as telas dos dois) e tem também equipe, registro de atividades, recebimento online, plano e faturas e anonimização. |
| SECRETARIA | Alunos, responsáveis, professores, estrutura acadêmica, alocações, horários, matrículas e pedidos de correção. |
| FINANCEIRO | Cobranças, despesas, relatórios, risco de atraso e aba Financeiro do aluno. |
| PROFESSOR | Turmas alocadas, avaliações, notas, aulas e chamada. |
| ALUNO | Perfil, pedidos de correção, boletim com frequência, agenda, histórico financeiro e pagamento online próprios. |
| Gateway de Pagamento (Asaas) | Sistema externo. Gera cobranças e envia avisos. |
| Serviço de E-mail (Brevo) | Sistema externo. Entrega os e-mails. |
| Tempo | Dispara as rotinas agendadas. |

## Convenções dos diagramas

- **Generalização entre atores:** os perfis especializam *Usuário da escola* e *Usuário*; o MASTER especializa SECRETARIA (diagrama 03) e FINANCEIRO (diagrama 04). Assim, o MASTER não precisa de uma linha para cada caso de uso que também usa.
- **Generalização entre casos de uso:** *Pagar com PIX* especializa UC26.
- **`<<include>>`:** comportamento que faz parte de toda execução do caso de uso base.
- **`<<extend>>`:** comportamento opcional ou condicional, derivado de um fluxo alternativo. A seta sempre aponta para o caso de uso base.
- **Elipse amarela:** implementação parcial.
- **Elipse cinza tracejada:** planejado para a Sprint 5, ainda não existe no código.
- **Elipse sem `UCxx`:** fluxo alternativo da descrição, destacado para dar legibilidade. Não é um requisito novo.
- **Disposição:** atores à esquerda, casos de uso base no meio, extensões e incluídos à direita, sistemas externos na borda direita. As linhas seguem nesse sentido e não se cruzam.
- **Auditoria:** a gravação não aparece como caso de uso, por ser ação interna do sistema. A consulta do registro é o UC37.

## Diferenças em relação aos artefatos anteriores

Esta pasta substitui as antigas `casos-de-uso-descritivo` e `casos-de-uso-diagramas`, da Sprint 3, removidas do repositório (continuam no histórico do Git). Os identificadores foram preservados, mas o conteúdo foi corrigido onde divergia do código:

| Item anterior | Situação encontrada |
|---|---|
| Descritivo ia até UC17 | Há descrição para os casos de uso implementados até UC33. |
| *Estornar Pagamento* como extensão de UC12 | Não existe. Paga é situação final da cobrança. |
| *Inativar Aluno* em UC05 | Entregue na Sprint 5 (S5-09). |
| *Recuperar Senha* como extensão de UC03 | Entregue na Sprint 5 (S5-02). |
| *Registrar Operação na Auditoria* como `<<include>>` | Removido dos diagramas, por ser ação interna do sistema. |
| Dependências rotuladas `pré-condição` | Removidas, por não serem relacionamento UML. Constam nas precondições das descrições. |
| Boletim com média ponderada | O boletim mostra pontos obtidos, avaliados e distribuídos, por ano letivo e por bimestre. |

## Como renderizar os diagramas

Os `.puml` são a fonte. Para gerar os PNGs (requer Java):

```bash
# a partir de Artefatos/casos-de-uso/diagramas
java -jar plantuml.jar -Playout=smetana -tpng -charset UTF-8 -o png 0*.puml
```

Esse comando substitui os PNGs atuais. As imagens incorporadas no DOCX precisam ser trocadas também.

Para manter as linhas sem cruzar: escreva o `<<extend>>` a partir do caso base (`UC09 <.. UC09A : <<extend>>`, que desenha a mesma seta de `UC09A ..> UC09`) e use `..>`, nunca `.>`. Com generalização de atores, `.>` derruba o layout `smetana`.
