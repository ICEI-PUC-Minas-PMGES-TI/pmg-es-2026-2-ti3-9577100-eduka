# Código do Projeto

Mantenha neste diretório todo o código do projeto.

## Estrutura

```text
Codigo/
├── backend/            API REST em Java 21 + Spring Boot 4 (Maven Wrapper)
├── frontend/           SPA em React 19 + TypeScript + Vite
├── nginx/              nginx.conf do ponto único de entrada em produção
├── postgres/init/      Scripts executados na primeira subida do container Postgres
│                       (cria o role restrito DB_APP_USER usado pelo backend)
├── postgres/mock/      mock.sql com escolas de exemplo, carregado pelo serviço mock (só dev)
├── pgadmin/            servers.json com o Postgres de dev pré-cadastrado no pgAdmin
├── docker-compose.yml       Ambiente de desenvolvimento (Postgres + pgAdmin + mock + backend + frontend)
└── docker-compose.prod.yml  Ambiente de produção (Nginx na porta 80 como único ponto de entrada)
```

## Backend

Pacote raiz: `br.pucminas.eduka.backend`.

| Pacote | Conteúdo |
|---|---|
| `controller` | Endpoints REST: `HealthController` (`GET /health`), `AuthController` (`POST /login`), `EscolaController` (`/escolas`, só `SUPER_ADMIN`) `AlunoController` (`/alunos`) e `ProfessorController` (`/professores`). |
| `security` | JWT (`JwtConfig`, `JwtService`, `JwtProperties`, `AccessTokenClaims`), hashing de senha (`PasswordEncoderConfig`), o `SecurityFilterChain` com CORS, `AccessTokenValidator` (só access token abre rota), `PerfilAuthenticationConverter` (perfil → `ROLE_*`, principal `UsuarioAutenticado`), a matriz de acesso por módulo (`RotasPorPerfil`), as expressões de `@PreAuthorize` (`Perfis`), quem pode criar qual perfil (`HierarquiaPerfis`), o isolamento entre escolas (`IsolamentoEscola`, `EscolaTenantIdentifierResolver`, `TenantConfig`) e a criptografia de dados pessoais (`PiiCryptoService`). |
| `domain` | Entidades JPA e enums: `Escola` (o tenant do sistema), `Usuario`, `Pessoa` (dados pessoais cifrados), `PessoaPapel`, `Aluno`, `AlunoResponsavel`, `Endereco`, `PerfilUsuario`, `PapelPessoa`, `PlanoEscola`, `ModuloEscola`. `PessoaCryptoConverter` cifra os campos de `Pessoa` ao gravar. |
| `repository` | Interfaces Spring Data (`EscolaRepository`, `UsuarioRepository`, `PessoaRepository`, `PessoaPapelRepository`, `AlunoRepository`, `AlunoResponsavelRepository`). |
| `service` | Regras de negócio: `AuthService` (login e emissão de tokens), `UsuarioService` (cadastro e troca de senha), `EscolaService` (cadastro, edição, ativação e suspensão de escolas) `AlunoService` (cadastro e consulta de alunos e responsáveis) e `ProfessorService` (cadastro, edição e inativação de professores). |
| `dto` | Records de entrada e saída da API (`LoginRequest`/`LoginResponse`, `EscolaRequest`/`EscolaUpdateRequest`/`EscolaResponse`, `AlunoRequest`/`AlunoResponse`, `ResponsavelRequest`/`ResponsavelResponse`, endereço e cadastro de usuário). |
| `exception` | Exceções de domínio (`CredenciaisInvalidasException`, `LoginJaCadastradoException`, `CnpjJaCadastradoException`, `RecursoNaoEncontradoException`, `SenhaInvalidaException`) e o `ApiExceptionHandler`, que traduz tudo — inclusive 401/403 vindos dos filtros de segurança — para `ProblemDetail`. |

`DevDataSeeder`, na raiz do pacote, cria o super admin de desenvolvimento quando o perfil `dev` está ativo.

Convenções:

* **Schema do banco é versionado por Flyway** em `src/main/resources/db/migration/` (`V1__criar_estrutura_perfis.sql` até `V11__criar_professor.sql`). O Hibernate roda com `ddl-auto=validate`: ele nunca cria nem altera tabelas, só confere se as entidades batem com o que a migration criou. Mudança de schema = nova migration + ajuste na entidade. Nunca edite uma migration já mergeada — o Flyway compara o checksum e recusa subir.
* **Convenções de schema:** chave primária `ID_<TABELA>` em UUID (`ID_ESCOLA`, `ID_PESSOA`, `ID_USUARIO`) e coluna de tenant `ESCOLA_ID` nas tabelas de negócio. As tabelas mais recentes usam datas em `TIMESTAMPTZ` (mapeadas como `Instant` no Java). A tabela `alunos` (V5) é a exceção de nome, no plural.
* **Isolamento entre escolas em duas camadas.** Nos services, `IsolamentoEscola.escolaIdAtual()` filtra consultas e `exigirAcesso(...)` barra recurso de outra escola. Na persistência, entidades com `@TenantId` (hoje `Aluno`) recebem o filtro `escola_id` automaticamente do Hibernate, via `EscolaTenantIdentifierResolver`. `Pessoa` não usa `@TenantId` (o `SUPER_ADMIN` cria pessoas em nome de outras escolas), então toda leitura dela passa pelos métodos escopados por escola do `PessoaRepository`, nunca por `findById` puro. Nunca aceite um `escolaId` vindo do cliente.
* **Dados pessoais são cifrados na aplicação.** Nome, CPF, data de nascimento, e-mail, telefone e endereço de `Pessoa` são gravados em `BYTEA`, cifrados com AES-256-GCM por `PessoaCryptoConverter`/`PiiCryptoService`. A chave (`PII_ENCRYPTION_KEY`) nunca chega ao banco. Dado pessoal novo deve morar em `Pessoa`, não em colunas de texto de outras tabelas.
* **Senha só existe em texto puro dentro de `UsuarioService` e `AuthService`.** Tudo que chega ao banco passa por `PasswordEncoder.encode`; toda verificação passa por `PasswordEncoder.matches`. Nunca compare a senha com a coluna diretamente.
* **Records e entidades que carregam senha sobrescrevem `toString()`** para omiti-la, evitando vazamento em log e stack trace.
* **Configuração vem de variáveis de ambiente** lidas em `application.properties` (`POSTGRES_DB`, `DB_APP_USER`, `DB_APP_PASSWORD`, `JWT_SECRET`, `PII_ENCRYPTION_KEY`, ...). O `.env.example` na raiz do repositório lista todas. Os defaults de credencial em `application.properties` valem só para dev; `application-prod.properties` (perfil `prod`) os remove, de modo que variável faltando derruba a subida. Ao adicionar um segredo novo, siga o mesmo padrão: sem default em prod, e validação explícita na subida quando houver requisito de formato (como o tamanho mínimo do `JWT_SECRET` em `JwtConfig`).
* **Autorização em duas camadas.** `RotasPorPerfil` define quais perfis entram em cada módulo (prefixo de rota) já no filter chain; módulos sem controller já estão listados, para que o endpoint nasça protegido. Dentro do módulo, o controller refina por ação com `@PreAuthorize` e uma constante de `Perfis` — nunca com strings de perfil soltas. `AutorizacaoTestController` (em `src/test`) é o exemplo mínimo e a prova de que o filter chain, o `@PreAuthorize` e o isolamento funcionam de ponta a ponta.
* Testes em `src/test/java` espelham os pacotes de `src/main/java`. Testes de serviço usam Mockito para o repository e o `PasswordEncoder` real, de modo a exercitar o hashing de verdade.

## Frontend

SPA em React 19 + TypeScript, com Vite e React Router. Detalhes de estrutura e convenções em [`frontend/README.md`](frontend/README.md). Em desenvolvimento roda em `http://localhost:5173`; em produção é compilado e servido como estático pelo Nginx (`frontend/Dockerfile.prod`).
