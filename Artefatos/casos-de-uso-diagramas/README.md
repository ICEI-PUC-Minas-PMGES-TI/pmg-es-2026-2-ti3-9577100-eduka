# Diagramas de Casos de Uso — Eduka

Diagramas UML de casos de uso equivalentes aos
[casos de uso descritivos](../casos-de-uso-descritivo/Casos_de_Uso_Descritivos_Eduka.pdf)
(UC01 a UC17), acrescidos dos casos de uso da Sprint 2 (UC18 a UC26: alocação de
professores, portal do professor, portal do aluno e pagamento online). Os fontes estão em
[PlantUML](https://plantuml.com) (`.puml`) e as imagens renderizadas em [`png/`](png/).

| Diagrama | Escopo | Casos de uso |
|---|---|---|
| [00-visao-geral.puml](00-visao-geral.puml) · [PNG](png/00-visao-geral.png) | Todos os atores e casos de uso do sistema | UC01–UC26 |
| [01-administracao-plataforma.puml](01-administracao-plataforma.puml) · [PNG](png/01-administracao-plataforma.png) | Administração da plataforma (SUPER_ADMIN) | UC01, UC02 |
| [02-acesso-e-perfis.puml](02-acesso-e-perfis.puml) · [PNG](png/02-acesso-e-perfis.png) | Autenticação, gestão de usuários e dashboards por perfil | UC03, UC04, UC16 |
| [03-gestao-academica.puml](03-gestao-academica.puml) · [PNG](png/03-gestao-academica.png) | Alunos, professores, estrutura acadêmica, alocação e matrícula | UC05–UC08, UC18 |
| [04-gestao-financeira.puml](04-gestao-financeira.puml) · [PNG](png/04-gestao-financeira.png) | Cobranças, boletos, pagamentos, despesas e relatórios | UC09–UC15, UC26 |
| [05-portal-do-aluno.puml](05-portal-do-aluno.puml) · [PNG](png/05-portal-do-aluno.png) | Consultas e pagamento do próprio aluno | UC17, UC22–UC26 (+ UC13) |
| [06-portal-do-professor.puml](06-portal-do-professor.puml) · [PNG](png/06-portal-do-professor.png) | Turmas alocadas, avaliações e notas | UC19–UC21 |

O arquivo [`_estilo.puml`](_estilo.puml) contém apenas a configuração visual comum, incluída
pelos demais diagramas.

## Atores

Todos os perfis especializam o ator abstrato **Usuário**, que concentra os casos de uso comuns
(UC03 — Autenticar Usuário e UC16 — Visualizar Dashboard).

| Ator | Casos de uso como ator primário |
|---|---|
| SUPER_ADMIN | UC01, UC02 |
| MASTER | UC04; UC06, UC07 e UC15 (compartilhados); dashboard da escola |
| SECRETARIA | UC05, UC06, UC07, UC08, UC18; dashboard da secretaria |
| FINANCEIRO | UC09, UC10, UC11, UC12, UC14, UC15; secundário em UC13 |
| PROFESSOR | UC19, UC20, UC21; dashboard do professor |
| ALUNO | UC13, UC17, UC22, UC23, UC24, UC25, UC26 |

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

Casos de uso existentes também foram ajustados: UC03 passa a incluir *Redirecionar para o
Painel do Perfil*; UC05 e UC06 incluem a criação do acesso do aluno e do professor; UC07
passa a abranger períodos letivos; UC08 ganha a extensão *Atualizar Status da Matrícula*;
UC16 é especializado nos dashboards da escola, da secretaria e do professor; e o MASTER
passa a gerenciar apenas usuários SECRETARIA e FINANCEIRO. A associação de professores a
turmas, antes incluída em UC06, virou o caso de uso próprio UC18.

Todos os casos de uso respeitam o isolamento por escola: o JWT carrega a Role e o
`escola_id` do usuário, e toda consulta e gravação é limitada à escola dele (schema único
com `escola_id`).

Fora do escopo desta versão: CRUD de Responsável e diário de aula.

## Convenções de notação

- **Generalização de ator** (`<|--`): perfis que herdam os casos de uso comuns de *Usuário*.
- **Generalização de caso de uso**: variantes de um caso de uso genérico (ex.: os
  dashboards da escola, da secretaria e do professor especializam UC16).
- **`<<include>>`**: comportamento sempre executado como parte do caso de uso base
  (ex.: UC17 inclui UC13; UC06 inclui *Criar Acesso do Professor*).
- **`<<extend>>`**: comportamento opcional ou condicional derivado dos fluxos alternativos
  dos descritivos (ex.: *Recuperar Senha* estende UC03; *Cancelar Cobrança* estende UC09).
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
