# Casos de Uso do Eduka

Diagramas UML e descrições de cada caso de uso, conferidos com o código da branch `developer` (commit `25a92f8`) em 04/11/2026.

| Conteúdo | Arquivo |
|---|---|
| Descrições no template da disciplina, com os diagramas e o Anexo A | [Casos_de_Uso_Descritivos_Eduka.docx](descritivos/Casos_de_Uso_Descritivos_Eduka.docx) · [PDF](descritivos/Casos_de_Uso_Descritivos_Eduka.pdf) |
| Diagramas (imagem) | [diagramas/png/](diagramas/png/) |
| Diagramas (fonte PlantUML) | [diagramas/](diagramas/) |

São 30 casos de uso: 17 completos e 13 parciais. O Anexo A do documento detalha cada lacuna.

## Índice por módulo

**Situação:** *Completo* significa que o fluxo está disponível ao ator pela interface, ou por integração no caso de UC30 a UC32. *Parcial* significa que parte do comportamento previsto não existe ou só está disponível pela API. O acesso do MASTER somente pela API aos módulos acadêmico e financeiro é indicado na descrição e não torna, sozinho, um caso de uso parcial.

### Administração da plataforma ([diagrama](diagramas/png/01-administracao-plataforma.png))

| ID | Caso de uso | Ator primário | Requisitos | Situação | Tela |
|---|---|---|---|---|---|
| UC01 | Cadastrar Escola | SUPER_ADMIN | RF01 | Parcial | `/super-admin/escolas` |
| UC02 | Gerenciar Escolas Cadastradas | SUPER_ADMIN | RF02, RF23, RF24 | Parcial | `/super-admin/escolas` |
| UC27 | Gerenciar Faturas da Plataforma | SUPER_ADMIN | RF23, RF24 | Completo | `/super-admin/escolas/:id/faturas` |
| UC28 | Pagar Fatura da Plataforma | MASTER | RF23, RF24 | Completo | `/master/recebimentos` |

### Acesso e usuários ([diagrama](diagramas/png/02-acesso-e-usuarios.png))

| ID | Caso de uso | Ator primário | Requisitos | Situação | Tela |
|---|---|---|---|---|---|
| UC03 | Autenticar Usuário | Usuário (todos os perfis) | RF03, RF04 | Parcial | `/login`, `/login/admin` |
| UC04 | Gerenciar Usuários da Escola | MASTER | RF07 | Parcial | Sem tela (API `/usuarios/secretarias`) |
| UC16 | Visualizar Dashboard | MASTER, SECRETARIA, FINANCEIRO, PROFESSOR | RF20 | Completo | `/<perfil>/inicio` |

### Gestão acadêmica ([diagrama](diagramas/png/03-gestao-academica.png))

| ID | Caso de uso | Ator primário | Requisitos | Situação | Tela |
|---|---|---|---|---|---|
| UC05 | Gerenciar Alunos | SECRETARIA | RF08 | Parcial | `/secretaria/alunos` |
| UC29 | Gerenciar Responsáveis | SECRETARIA | RF08, RF12 | Parcial | No cadastro do aluno (API `/responsaveis`) |
| UC06 | Gerenciar Professores | SECRETARIA | RF09 | Parcial | `/secretaria/professores` |
| UC07 | Gerenciar Estrutura Acadêmica | SECRETARIA | RF10 | Completo | `/secretaria/academico` |
| UC18 | Alocar Professor em Turmas e Disciplinas | SECRETARIA | RF09, RF10 | Completo | `/secretaria/academico/alocacoes` |
| UC08 | Realizar Matrícula de Aluno | SECRETARIA | RF11 | Completo | `/secretaria/matriculas` |

### Gestão financeira ([diagrama](diagramas/png/04-gestao-financeira.png))

| ID | Caso de uso | Ator primário | Requisitos | Situação | Tela |
|---|---|---|---|---|---|
| UC09 | Gerenciar Cobranças | FINANCEIRO | RF12, RF15, RF22 | Parcial | `/financeiro/cobrancas/nova` |
| UC11 | Consultar e Filtrar Cobranças | FINANCEIRO | RF14 | Parcial | `/financeiro/cobrancas` |
| UC12 | Registrar Pagamento Manual | FINANCEIRO | RF16 | Parcial | Sem tela (API `/cobrancas/{id}/pagar`) |
| UC13 | Consultar Histórico Financeiro do Aluno | ALUNO | RF17, RF25 | Parcial | `/aluno/pagamentos` |
| UC14 | Gerenciar Despesas | FINANCEIRO | RF18 | Parcial | `/financeiro/despesas` |
| UC33 | Ativar Recebimento Online | MASTER | RF16, RNF13 | Completo | `/master/recebimentos` |

### Portal do professor ([diagrama](diagramas/png/05-portal-do-professor.png))

| ID | Caso de uso | Ator primário | Requisitos | Situação | Tela |
|---|---|---|---|---|---|
| UC19 | Consultar Turmas e Disciplinas Alocadas | PROFESSOR | RF10 | Completo | `/professor/turmas` |
| UC20 | Cadastrar Avaliações | PROFESSOR | RF25 | Completo | `/professor/notas` |
| UC21 | Lançar Notas dos Alunos | PROFESSOR | RF25 | Completo | `/professor/notas` |

### Portal do aluno ([diagrama](diagramas/png/06-portal-do-aluno.png))

| ID | Caso de uso | Ator primário | Requisitos | Situação | Tela |
|---|---|---|---|---|---|
| UC22 | Visualizar Meu Perfil | ALUNO | RF08, RF25 | Completo | `/aluno/meus-dados` |
| UC23 | Consultar Boletim Acadêmico | ALUNO | RF25 | Completo | `/aluno/boletim` |
| UC24 | Consultar Disciplinas e Turmas | ALUNO | RF25 | Parcial | `/aluno/meus-dados` (resumo) |
| UC25 | Consultar Agenda de Provas e Tarefas | ALUNO | RF25 | Completo | `/aluno/boletim?aba=provas` |
| UC26 | Pagar Cobrança Online | ALUNO | RF15, RF16, RF17 | Completo | `/aluno/pagamentos` |

### Rotinas e integrações ([diagrama](diagramas/png/07-rotinas-e-integracoes.png))

| ID | Caso de uso | Ator primário | Requisitos | Situação | Origem |
|---|---|---|---|---|---|
| UC30 | Confirmar Pagamento pelo Gateway | Gateway de Pagamento (Asaas) | RF15, RF16 | Completo | Webhook `/webhooks/asaas` |
| UC31 | Marcar Cobranças e Faturas em Atraso | Tempo | RF15 | Completo | Rotina diária, às 00:05 |
| UC32 | Enviar Notificações por E-mail | Tempo | RF21 | Completo | Rotina diária, às 08:00, e fila a cada 30 s |

A [visão geral](diagramas/png/00-visao-geral.png) reúne todos os casos de uso. Os requisitos seguem o Documento de Visão e, para as telas das Sprints 2 e 3, o Documento de Interface de Usuário.

## Identificadores sem descrição

| ID | Motivo |
|---|---|
| UC10 Anexar Boleto à Cobrança | Retirado do escopo em 15/10/2026. |
| UC15 Consultar Relatórios Financeiros | Especificado no RF19, mas não implementado e adiado para a Sprint 4. |
| UC17 Consultar Informações Acadêmicas e Financeiras Próprias | Consolidado em UC13 e UC22 a UC25, para evitar duplicidade. |

## Atores

| Ator | Papel |
|---|---|
| Usuário | Ator abstrato, especializado pelos seis perfis abaixo. Inicia UC03. |
| SUPER_ADMIN | Administra a plataforma, as escolas e as faturas da assinatura. |
| MASTER | Gestor da escola. Tem telas para dashboard, recebimento online e faturas do Eduka; nas demais áreas, atua somente pela API. |
| SECRETARIA | Alunos, responsáveis, professores, estrutura acadêmica, alocações e matrículas. |
| FINANCEIRO | Cobranças, despesas e dashboard financeiro. |
| PROFESSOR | Turmas alocadas, avaliações e notas. |
| ALUNO | Perfil, boletim, agenda, histórico financeiro e pagamento online próprios. |
| Gateway de Pagamento (Asaas) | Sistema externo. Gera cobranças e envia avisos. |
| Serviço de E-mail (Brevo) | Sistema externo. Entrega os e-mails. |
| Tempo | Dispara as rotinas agendadas. |

## Convenções dos diagramas

- **Generalização:** entre atores (perfis especializam *Usuário*) e entre casos de uso (*Pagar com PIX* especializa UC26).
- **`<<include>>`:** comportamento que faz parte de toda execução do caso de uso base.
- **`<<extend>>`:** comportamento opcional ou condicional, derivado de um fluxo alternativo.
- **Elipse amarela:** implementação parcial.
- **Linha cinza:** o perfil tem permissão na API, mas a interface web não oferece tela.
- **Elipse sem `UCxx`:** fluxo alternativo da descrição, destacado para dar legibilidade. Não é um requisito novo.
- **Auditoria:** não aparece como caso de uso por ser ação interna do sistema. É citada nos fluxos em que ocorre.

## Diferenças em relação aos artefatos anteriores

As pastas [`../casos-de-uso-descritivo`](../casos-de-uso-descritivo/) e [`../casos-de-uso-diagramas`](../casos-de-uso-diagramas/) foram mantidas sem alteração. Os identificadores foram preservados, mas o conteúdo foi corrigido onde divergia do código:

| Item anterior | Situação encontrada |
|---|---|
| Descritivo ia até UC17 | Agora há descrição para todos os casos de uso implementados, inclusive UC18 a UC33. |
| *Estornar Pagamento* como extensão de UC12 | Não existe. Paga é situação final da cobrança. |
| *Recuperar Senha* como extensão de UC03 | Não existe. A tela só orienta procurar a secretaria. |
| *Inativar Aluno* em UC05 | Não existe. |
| MASTER gerencia SECRETARIA e FINANCEIRO em UC04 | Só SECRETARIA, e somente pela API. |
| *Registrar Operação na Auditoria* como `<<include>>` | Removido dos diagramas, por ser ação interna do sistema. |
| Dependências rotuladas `pré-condição` | Removidas, por não serem relacionamento UML. Constam nas precondições das descrições. |
| Boletim com média ponderada | O boletim mostra pontos obtidos, avaliados e distribuídos. |

## Como renderizar os diagramas

Os arquivos `.puml` e `.png` foram gerados da mesma definição, mas os PNGs atuais **não** vêm do PlantUML: foram desenhados por script, porque não havia Java disponível na geração. Por isso, os `.puml` ainda não foram compilados, e o PlantUML produzirá outro layout, com os mesmos elementos e relações.

```bash
# a partir de Artefatos/casos-de-uso/diagramas
java -jar plantuml.jar -Playout=smetana -tpng -charset UTF-8 -o png *.puml
```

Esse comando substitui os PNGs atuais. Se o layout resultante for adotado, as imagens incorporadas no DOCX precisam ser trocadas também.
