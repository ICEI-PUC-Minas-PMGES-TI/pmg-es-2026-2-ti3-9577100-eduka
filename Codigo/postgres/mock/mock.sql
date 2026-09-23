-- Dados mockados para desenvolvimento/testes locais. NUNCA rodar em producao.
--
-- Nao fica em postgres/init/ de proposito: aqueles scripts rodam na criacao do
-- container, antes do Flyway criar as tabelas. Rode este DEPOIS que o backend
-- subir pelo menos uma vez (a partir de Codigo/):
--
--   docker compose exec -T postgres psql -U eduka -d eduka < postgres/mock/mock.sql
--
-- Pode rodar mais de uma vez: nada e duplicado.
--
-- Login do super admin:  admin@eduka.com.br / Eduka-admin123

BEGIN;

-- Super admin -----------------------------------------------------------------
-- SUPER_ADMIN nao pertence a nenhuma escola (ESCOLA_ID nulo).
-- SENHA_HASH precisa do prefixo {bcrypt}: e o formato esperado pelo
-- DelegatingPasswordEncoder (PasswordEncoderConfig). crypt/gen_salt vem do
-- pgcrypto (habilitado na V1) e geram um hash $2a$ compativel com o BCrypt do Spring.
-- LOGIN em minusculas, como o UsuarioService.normalizarLogin grava.
WITH nova_pessoa AS (
    INSERT INTO PESSOA (NOME, EMAIL)
    SELECT convert_to('Super Admin', 'UTF8'), convert_to('admin@eduka.com.br', 'UTF8')
    WHERE NOT EXISTS (SELECT 1 FROM USUARIO WHERE LOGIN = 'admin@eduka.com.br')
    RETURNING ID_PESSOA
)
INSERT INTO USUARIO (PESSOA_ID, LOGIN, SENHA_HASH, PERFIL)
SELECT ID_PESSOA,
       'admin@eduka.com.br',
       '{bcrypt}' || crypt('Eduka-admin123', gen_salt('bf', 12)),
       'SUPER_ADMIN'
FROM nova_pessoa;

-- Escolas ---------------------------------------------------------------------
-- CNPJ so com digitos e com digitos verificadores validos (o front valida).
-- PLANO precisa da V6 aplicada (backend atualizado).
CREATE TEMP TABLE mock_escola (nome TEXT, cnpj TEXT, endereco TEXT, email TEXT, telefone TEXT, plano TEXT, ativo BOOLEAN)
    ON COMMIT DROP;

INSERT INTO mock_escola VALUES
    ('Colegio Horizonte',        '11222333000181', 'Rua da Bahia, 1200 - Centro, Belo Horizonte/MG',            'contato@colegiohorizonte.com.br', '(31) 3222-1000',  'ENTERPRISE', TRUE),
    ('Escola Sabia',             '44555666000181', 'Av. Amazonas, 450 - Barro Preto, Belo Horizonte/MG',         'secretaria@escolasabia.com.br',   '(31) 3333-2000',  'AVANCADO',   TRUE),
    ('Instituto Nova Geracao',   '77888999000181', 'Rua Padre Eustaquio, 88 - Carlos Prates, Belo Horizonte/MG', 'contato@novageracao.edu.br',      '(31) 3444-3000',  'BASICO',     TRUE),
    ('Colegio Monte Verde',      '12345678000195', 'Av. Joao Cesar de Oliveira, 900 - Eldorado, Contagem/MG',    'contato@monteverde.com.br',       '(31) 3555-4000',  'AVANCADO',   TRUE),
    ('Escola Pequeno Principe',  '98765432000198', 'Rua Sergipe, 310 - Funcionarios, Belo Horizonte/MG',         'contato@pequenoprincipe.com.br',  '(31) 98888-5000', 'BASICO',     FALSE),
    ('Centro Educacional Aurora','24681357000140', 'Av. Brasil, 75 - Centro, Betim/MG',                          NULL,                              NULL,              'BASICO',     TRUE);

INSERT INTO ESCOLA (NOME, CNPJ_CPF, ENDERECO, EMAIL, TELEFONE, PLANO, ATIVO)
SELECT m.nome, m.cnpj, m.endereco, m.email, m.telefone, m.plano, m.ativo
FROM mock_escola m
WHERE NOT EXISTS (SELECT 1 FROM ESCOLA e WHERE e.CNPJ_CPF = m.cnpj);

-- Escolas mockadas antes do plano existir ficam sem plano; completa.
UPDATE ESCOLA e SET PLANO = m.plano
FROM mock_escola m
WHERE e.CNPJ_CPF = m.cnpj AND e.PLANO IS NULL;

COMMIT;
