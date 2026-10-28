# Diagramas de Casos de Uso — Eduka

Diagramas UML de casos de uso equivalentes aos
[casos de uso descritivos](../casos-de-uso-descritivo/Casos_de_Uso_Descritivos_Eduka.pdf)
(UC01 a UC17), acrescidos dos casos de uso da Sprint 2 (UC18 a UC26: alocação de
professores, portal do professor, portal do aluno e pagamento online) e da Sprint 3 (UC27 a
UC33: faturas da plataforma, responsáveis, confirmação de pagamento pelo gateway, rotina de
atraso, notificações por e-mail e configuração do recebimento online). O escopo representa o
fim da Sprint 3, incluindo itens ainda em desenvolvimento (ver abaixo). Os fontes estão em
[PlantUML](https://plantuml.com) (`.puml`) e as imagens renderizadas em [`png/`](png/).

| Diagrama | Escopo | Casos de uso |
|---|---|---|
| [00-visao-geral.puml](00-visao-geral.puml) · [PNG](png/00-visao-geral.png) | Resumo: atores e casos de uso principais, sem include/extend (detalhes nos demais) | UC01–UC33 |
| [01-administracao-plataforma.puml](01-administracao-plataforma.puml) · [PNG](png/01-administracao-plataforma.png) | Administração da plataforma e faturas da assinatura | UC01, UC02, UC27, UC28 |
| [02-acesso-e-perfis.puml](02-acesso-e-perfis.puml) · [PNG](png/02-acesso-e-perfis.png) | Autenticação, gestão de usuários e dashboards por perfil | UC03, UC04, UC16 |
| [03-gestao-academica.puml](03-gestao-academica.puml) · [PNG](png/03-gestao-academica.png) | Alunos, responsáveis, professores, estrutura acadêmica, alocação e matrícula | UC05–UC08, UC18, UC29 |
| [04-gestao-financeira.puml](04-gestao-financeira.puml) · [PNG](png/04-gestao-financeira.png) | Cobranças, pagamento manual e estorno | UC09, UC11, UC12 (+ UC32) |
| [05-portal-do-aluno.puml](05-portal-do-aluno.puml) · [PNG](png/05-portal-do-aluno.png) | Consultas e pagamento do próprio aluno | UC17, UC22–UC26 (+ UC13) |
| [06-portal-do-professor.puml](06-portal-do-professor.puml) · [PNG](png/06-portal-do-professor.png) | Turmas alocadas, avaliações e notas | UC19–UC21 |
| [07-rotinas-e-integracoes.puml](07-rotinas-e-integracoes.puml) · [PNG](png/07-rotinas-e-integracoes.png) | Webhook do gateway, rotinas agendadas e e-mails | UC30–UC32 |
| [08-recebimento-online-e-relatorios.puml](08-recebimento-online-e-relatorios.puml) · [PNG](png/08-recebimento-online-e-relatorios.png) | Ativação e pagamento online, histórico financeiro e comprovante, despesas e relatórios | UC13–UC15, UC26, UC33 (+ UC30) |

O arquivo [`_estilo.puml`](_estilo.puml) contém apenas a configuração visual comum, incluída
pelos demais diagramas.

## Atores

Todos os perfis especializam o ator abstrato **Usuário**, que concentra os casos de uso comuns
(UC03 — Autenticar Usuário e UC16 — Visualizar Dashboard).

| Ator | Casos de uso como ator primário |
|---|---|
| SUPER_ADMIN | UC01, UC02, UC27 |
| MASTER | UC04, UC28, UC33; UC06, UC07, UC09, UC11, UC12, UC13, UC14, UC15 e UC29 (compartilhados); dashboard da escola |
| SECRETARIA | UC05, UC06, UC07, UC08, UC18, UC29; dashboard da secretaria |
| FINANCEIRO | UC09, UC11, UC12, UC14, UC15, UC29; secundário em UC13; dashboard do financeiro |
| PROFESSOR | UC19, UC20, UC21; dashboard do professor |
| ALUNO | UC13, UC17, UC22, UC23, UC24, UC25, UC26 |

Atores externos (secundários ou que disparam casos de uso sem um usuário logado):

| Ator | Papel |
|---|---|
| Gateway de Pagamento (Asaas) | Gera PIX e boleto em UC26 e UC28; ator primário de UC30 (avisos por webhook) |
| Serviço de E-mail (Brevo) | Entrega o código de login (UC03) e as notificações de UC32 |
| Tempo (rotina agendada) | Dispara UC31 (atraso diário), o lembrete de vencimento e o reenvio de notificações (UC32) |

## Casos de uso da Sprint 2

| UC | Caso de uso | Ator | Requisito |
|---|---|---|---|
| UC18 | Alocar Professor em Turmas e Disciplinas | SECRETARIA | RF10 |
| UC19 | Consultar Turmas e Disciplinas Alocadas | PROFESSOR | RF10 |
| UC20 | Cadastrar Avaliações | PROFESSOR | RF25 |
| UC21 | Lançar Notas dos Alunos | PROFESSOR | RF25 |
| UC22 | Visualizar Meu Perfil | ALUNO | RF08 |
| UC23 | Consultar Boletim Acadêmico | ALUNO | RF25 |
| UC24 | Consultar Disciplinas e Turmas | ALUNO | RF25 |
| UC25 | Consultar Agenda de Provas e Tarefas | ALUNO | RF25 |
| UC26 | Pagar Cobrança Online | ALUNO | RNF08.2 |

## Casos de uso da Sprint 3

| UC | Caso de uso | Ator | Requisito |
|---|---|---|---|
| UC27 | Gerenciar Faturas da Plataforma | SUPER_ADMIN | RF23 |
| UC28 | Pagar Fatura da Plataforma | MASTER (Asaas secundário) | RF23 |
| UC29 | Gerenciar Responsáveis | MASTER, SECRETARIA, FINANCEIRO | RF08, RF12 |
| UC30 | Confirmar Pagamento pelo Gateway | Gateway de Pagamento (Asaas) | RF15, RNF08.2 |
| UC31 | Marcar Cobranças e Faturas em Atraso | Tempo | RF15 |
| UC32 | Enviar Notificações por E-mail | Tempo (Brevo secundário) | RF21 |
| UC33 | Ativar Recebimento Online | MASTER (Asaas secundário) | RF13, RNF13 |

Ajustes da Sprint 3 em casos de uso existentes: UC03 passa a incluir *Verificar Código
Enviado por E-mail* (verificação em duas etapas), com as extensões *Reenviar Código* e
*Definir Senha no Primeiro Acesso*; UC09 inclui *Registrar Operação na Auditoria* (RF22) e o
aviso de cobrança criada (UC32); UC11 ganha listagem paginada e totais por situação; UC26
especializa-se em *Pagar com PIX* e *Pagar com Boleto*, com cópia do código e download do
boleto; UC13 ganha *Ver Comprovante de Pagamento*; e o responsável, antes fora do escopo,
virou o caso de uso UC29. Também entram, conforme o planejamento da sprint, o *Dashboard do
Financeiro* (especialização de UC16), *Estornar Pagamento* (extensão de UC12) e a dependência
de UC26 em relação a UC33.

Decisões de escopo refletidas nos diagramas:

- **Pagamento pelo gateway (15/10):** a escola recebe online pelo Asaas (UC26, UC30, UC33).
  O **boleto anexado sai do escopo**: UC10 (*Anexar Boleto à Cobrança*) e suas extensões foram
  retirados.
- **Pagamento manual mantido (21/10):** UC12 passa a ser *Registrar Pagamento Manual*, a baixa
  de um pagamento feito fora do Eduka (dinheiro, transferência direta etc.), ao lado do
  pagamento online. O Documento de Interface de Usuário (IU20, revisão de 15/10) ainda diz que
  não há pagamento manual e precisa ser ajustado.
- **Adiados para a Sprint 4 (Documento de Interface, revisão de 15/10):** UC15 *Consultar
  Relatórios Financeiros* e *Exportar Relatório em CSV* (IU19 e IU19-A). Continuam nos
  diagramas, marcados como Sprint 4.

Ainda em desenvolvimento no momento desta atualização (21/10/2026): UC33 (#209), estorno
(#199), despesas (#200), dashboard do financeiro (#212, #213) e comprovante do pagamento do
aluno.

Casos de uso existentes também foram ajustados na Sprint 2: UC03 passa a incluir *Redirecionar para o
Painel do Perfil*; UC05 e UC06 incluem a criação do acesso do aluno e do professor; UC07
passa a abranger períodos letivos; UC08 ganha a extensão *Atualizar Status da Matrícula*;
UC16 é especializado nos dashboards da escola, da secretaria e do professor; e o MASTER
passa a gerenciar apenas usuários SECRETARIA e FINANCEIRO. A associação de professores a
turmas, antes incluída em UC06, virou o caso de uso próprio UC18.

Todos os casos de uso respeitam o isolamento por escola: o JWT carrega a Role e o
`escola_id` do usuário, e toda consulta e gravação é limitada à escola dele (schema único
com `escola_id`).

Fora do escopo desta versão: diário de aula.

## Convenções de notação

- **Generalização de ator** (`<|--`): perfis que herdam os casos de uso comuns de *Usuário*.
- **Generalização de caso de uso**: variantes de um caso de uso genérico (ex.: os
  dashboards da escola, da secretaria e do professor especializam UC16).
- **`<<include>>`**: comportamento sempre executado como parte do caso de uso base
  (ex.: UC17 inclui UC13; UC06 inclui *Criar Acesso do Professor*).
- **`<<extend>>`**: comportamento opcional ou condicional derivado dos fluxos alternativos
  dos descritivos (ex.: *Recuperar Senha* estende UC03; *Cancelar Cobrança* estende UC09).
- **Ator `<<sistema externo>>` / `<<rotina agendada>>`**: sistemas de terceiros e o relógio
  do servidor. A seta do caso de uso para o ator indica que o Eduka o aciona (ator secundário).
- **Dependência rotulada `pré-condição`**: caso de uso cuja execução depende de dados
  produzidos por outro (ex.: UC08 depende de UC05 e UC07; UC19 depende de UC18). Não é um relacionamento UML
  normativo, e sim a representação gráfica das pré-condições descritas nos casos de uso.
- Casos de uso sem identificador `UCxx` representam fluxos alternativos dos descritivos
  promovidos a extensões, para tornar o diagrama legível sem inventar requisitos novos.

## Como renderizar

As imagens em `png/` já estão versionadas. Para regerá-las após editar um `.puml`:

```bash
# VS Code: extensão "PlantUML" (Alt+D para pré-visualizar)
# ou via jar oficial (requer Java 8+):
java -jar plantuml.jar -tpng -charset UTF-8 -o png *.puml
# sem Graphviz instalado, use o layout interno do PlantUML:
java -jar plantuml.jar -Playout=smetana -tpng -charset UTF-8 -o png *.puml
```

Os PNGs atuais usam o Graphviz (`dot`) nos diagramas 00, 04 e 08 e o layout `smetana` nos
demais: em cada caso, o que deixou o diagrama mais legível.
