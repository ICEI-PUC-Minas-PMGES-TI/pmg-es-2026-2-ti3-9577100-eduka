# Código do Projeto

Mantenha neste diretório todo o código do projeto.

## Estrutura

```
Codigo/
├── backend/            API REST em Java 21 + Spring Boot 4 (Maven Wrapper)
├── frontend/           SPA em React 19 + TypeScript + Vite
├── postgres/init/      Scripts executados na primeira subida do container Postgres
│                       (cria o role restrito DB_APP_USER usado pelo backend)
├── docker-compose.yml       Ambiente de desenvolvimento (Postgres + backend + frontend)
└── docker-compose.prod.yml  Ambiente de produção (Nginx na porta 80 como único ponto de entrada)
```

## Backend

Pacote raiz: `br.pucminas.eduka.backend`.

| Pacote | Conteúdo |
|---|---|
| `controller` | Endpoints REST. Hoje apenas `HealthController` (`GET /health`). |
| `security` | JWT (`JwtConfig`, `JwtService`, `JwtProperties`, `AccessTokenClaims`), hashing de senha (`PasswordEncoderConfig`) e o `SecurityFilterChain`. |
| `model` | Entidades JPA. `Usuario` mapeia a tabela `usuarios`; `PerfilUsuario` é o enum dos seis perfis do sistema. |
| `domains/model`, `domains/enums` | `Escola` (tabela `escolas`, o tenant do sistema) e `Status`. `Aluno` e `SuperAdm` ainda são esboços vazios. |
| `repository` | Interfaces Spring Data (`UsuarioRepository`). |
| `service` | Regras de negócio. `UsuarioService` (cadastro e troca de senha) e `AutenticacaoService` (login e emissão de tokens). |
| `dto` | Records de entrada e saída dos serviços (`CadastroUsuarioRequest`, `LoginRequest`, `LoginResponse`). |
| `exception` | Exceções de domínio (`CredenciaisInvalidasException`, `LoginJaCadastradoException`, `SenhaInvalidaException`). |

Convenções:

* **Schema do banco é versionado por Flyway** em `src/main/resources/db/migration/` (`V1__criar_estrutura_perfis.sql`, e assim por diante). O Hibernate roda com `ddl-auto=validate`: ele nunca cria nem altera tabelas, só confere se as entidades batem com o que a migration criou. Mudança de schema = nova migration + ajuste na entidade. Nunca edite uma migration já mergeada — o Flyway compara o checksum e recusa subir.
* **Convenções de schema** (a partir da V3): tabelas em `minusculo_plural` (`escolas`, `pessoas`, `usuarios`), chave primária `id_<singular>` em UUID, coluna de tenant `escola_id` em toda tabela de negócio, datas em `TIMESTAMPTZ` (mapeadas como `Instant` no Java). Tabelas que apontam para uma pessoa usam a FK composta `(pessoa_id, escola_id)`, de modo que o banco recusa vincular um usuário da escola A a uma pessoa da escola B.
* **Senha só existe em texto puro dentro de `UsuarioService` e `AutenticacaoService`.** Tudo que chega ao banco passa por `PasswordEncoder.encode`; toda verificação passa por `PasswordEncoder.matches`. Nunca compare a senha com a coluna diretamente.
* **Records e entidades que carregam senha sobrescrevem `toString()`** para omiti-la, evitando vazamento em log e stack trace.
* **Configuração vem de variáveis de ambiente** lidas em `application.properties` (`POSTGRES_DB`, `DB_APP_USER`, `DB_APP_PASSWORD`, `JWT_SECRET`, ...). O `.env.example` na raiz do repositório lista todas. Os defaults de credencial em `application.properties` valem só para dev; `application-prod.properties` (perfil `prod`) os remove, de modo que variável faltando derruba a subida. Ao adicionar um segredo novo, siga o mesmo padrão: sem default em prod, e validação explícita na subida quando houver requisito de formato (como o tamanho mínimo do `JWT_SECRET` em `JwtConfig`).
* Testes em `src/test/java` espelham os pacotes de `src/main/java`. Testes de serviço usam Mockito para o repository e o `PasswordEncoder` real, de modo a exercitar o hashing de verdade.

## Frontend

Ainda no template inicial do Vite (`src/App.tsx`). Em desenvolvimento roda em `http://localhost:5173`; em produção é compilado e servido como estático pelo Nginx (`frontend/Dockerfile.prod`).
