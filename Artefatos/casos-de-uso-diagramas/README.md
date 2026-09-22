# Diagramas de Casos de Uso — Eduka

Diagramas UML de casos de uso equivalentes aos
[casos de uso descritivos](../casos-de-uso-descritivo/Casos_de_Uso_Descritivos_Eduka.pdf)
(UC01 a UC17). Os fontes estão em [PlantUML](https://plantuml.com) (`.puml`) e as imagens
renderizadas em [`png/`](png/).

| Diagrama | Escopo | Casos de uso |
|---|---|---|
| [00-visao-geral.puml](00-visao-geral.puml) · [PNG](png/00-visao-geral.png) | Todos os atores e casos de uso do sistema | UC01–UC17 |
| [01-administracao-plataforma.puml](01-administracao-plataforma.puml) · [PNG](png/01-administracao-plataforma.png) | Administração da plataforma (SUPER_ADMIN) | UC01, UC02 |
| [02-acesso-e-perfis.puml](02-acesso-e-perfis.puml) · [PNG](png/02-acesso-e-perfis.png) | Autenticação, gestão de usuários e dashboard | UC03, UC04, UC16 |
| [03-gestao-academica.puml](03-gestao-academica.puml) · [PNG](png/03-gestao-academica.png) | Alunos, professores, estrutura acadêmica e matrícula | UC05–UC08 |
| [04-gestao-financeira.puml](04-gestao-financeira.puml) · [PNG](png/04-gestao-financeira.png) | Cobranças, boletos, pagamentos, despesas e relatórios | UC09–UC15 |
| [05-portal-do-aluno.puml](05-portal-do-aluno.puml) · [PNG](png/05-portal-do-aluno.png) | Consultas do próprio aluno | UC17 (+ UC13) |

O arquivo [`_estilo.puml`](_estilo.puml) contém apenas a configuração visual comum, incluída
pelos demais diagramas.

## Atores

Todos os perfis especializam o ator abstrato **Usuário**, que concentra os casos de uso comuns
(UC03 — Autenticar Usuário e UC16 — Visualizar Dashboard).

| Ator | Casos de uso como ator primário |
|---|---|
| SUPER_ADMIN | UC01, UC02 |
| MASTER | UC04; UC06, UC07 e UC15 (compartilhados) |
| SECRETARIA | UC05, UC06, UC07, UC08 |
| FINANCEIRO | UC09, UC10, UC11, UC12, UC14, UC15; secundário em UC13 |
| ALUNO | UC13, UC17 |
| PROFESSOR | apenas os casos de uso herdados de Usuário |

> O perfil PROFESSOR não possui casos de uso específicos nos descritivos desta versão;
> ele aparece no diagrama para manter a hierarquia de perfis coerente com UC04.

## Convenções de notação

- **Generalização de ator** (`<|--`): perfis que herdam os casos de uso comuns de *Usuário*.
- **`<<include>>`**: comportamento sempre executado como parte do caso de uso base
  (ex.: UC17 inclui UC13).
- **`<<extend>>`**: comportamento opcional ou condicional derivado dos fluxos alternativos
  dos descritivos (ex.: *Recuperar Senha* estende UC03; *Cancelar Cobrança* estende UC09).
- **Dependência rotulada `pré-condição`**: caso de uso cuja execução depende de dados
  produzidos por outro (ex.: UC08 depende de UC05 e UC07). Não é um relacionamento UML
  normativo, e sim a representação gráfica das pré-condições descritas nos casos de uso.
- Casos de uso sem identificador `UCxx` representam fluxos alternativos dos descritivos
  promovidos a extensões, para tornar o diagrama legível sem inventar requisitos novos.

## Como renderizar

As imagens em `png/` já estão versionadas. Para regerá-las após editar um `.puml`:

```bash
# VS Code: extensão "PlantUML" (Alt+D para pré-visualizar)
# ou via jar oficial (requer Java 8+):
java -jar plantuml.jar -tpng -charset UTF-8 -o png *.puml
```
