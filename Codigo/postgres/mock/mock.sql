-- Dados mockados para desenvolvimento/testes locais. NUNCA rodar em producao.
--
-- Nao fica em postgres/init/ de proposito: aqueles scripts rodam na criacao do
-- container, antes do Flyway criar as tabelas. No docker-compose.yml de dev o
-- servico "mock" (load-mock.sh) espera o backend aplicar as migrations e roda
-- este arquivo sozinho a cada "up". Para rodar na mao (a partir da raiz do repo):
--
--   docker compose -f Codigo/docker-compose.yml --env-file .env exec -T postgres psql -U eduka -d eduka < Codigo/postgres/mock/mock.sql
--
-- Pode rodar mais de uma vez: nada e duplicado.
--
-- Super admin (admin@eduka.com.br / Eduka-admin123) e criado pelo
-- DevDataSeeder (perfil "dev" do backend), nao por este script: PESSOA
-- agora e cifrada pela aplicacao, e SQL puro nao produz um valor cifravel
-- do jeito que o PessoaCryptoConverter espera (nonce aleatorio, chave so
-- existe na JVM).

BEGIN;

-- Escolas ---------------------------------------------------------------------
-- CNPJ so com digitos e com digitos verificadores validos (o front valida).
-- Endereco quebrado, vigencia, limite e modulos precisam da V7 aplicada.
CREATE TEMP TABLE mock_escola (
    nome TEXT, cnpj TEXT, logradouro TEXT, numero TEXT, bairro TEXT, cidade TEXT, estado TEXT, cep TEXT,
    email TEXT, telefone TEXT, plano TEXT, ativo BOOLEAN,
    inicio_vigencia DATE, fim_vigencia DATE, limite_usuarios INTEGER, modulos TEXT[]
) ON COMMIT DROP;

INSERT INTO mock_escola VALUES
    ('Colegio Horizonte',        '11222333000181', 'Rua da Bahia',              '1200', 'Centro',         'Belo Horizonte', 'MG', '30160011',
     'contato@colegiohorizonte.com.br', '(31) 3222-1000',  'ENTERPRISE', TRUE,  '2024-01-15', '2026-12-15', 1500,
     ARRAY['PORTAL_ALUNO', 'PORTAL_PROFESSOR', 'GESTAO_FINANCEIRA', 'SECRETARIA_VIRTUAL', 'CONTROLE_FREQUENCIA']),
    ('Escola Sabia',             '44555666000181', 'Av. Amazonas',              '450',  'Barro Preto',    'Belo Horizonte', 'MG', '30180001',
     'secretaria@escolasabia.com.br',   '(31) 3333-2000',  'AVANCADO',   TRUE,  '2025-02-01', '2027-02-01', 600,
     ARRAY['PORTAL_ALUNO', 'PORTAL_PROFESSOR', 'SECRETARIA_VIRTUAL']),
    ('Instituto Nova Geracao',   '77888999000181', 'Rua Padre Eustaquio',       '88',   'Carlos Prates',  'Belo Horizonte', 'MG', '30710580',
     'contato@novageracao.edu.br',      '(31) 3444-3000',  'BASICO',     TRUE,  '2025-08-01', '2026-08-01', 200,
     ARRAY['PORTAL_ALUNO']),
    ('Colegio Monte Verde',      '12345678000195', 'Av. Joao Cesar de Oliveira', '900', 'Eldorado',       'Contagem',       'MG', '32310000',
     'contato@monteverde.com.br',       '(31) 3555-4000',  'AVANCADO',   TRUE,  '2024-06-10', '2026-06-10', 800,
     ARRAY['PORTAL_ALUNO', 'PORTAL_PROFESSOR', 'GESTAO_FINANCEIRA', 'BIBLIOTECA_DIGITAL']),
    ('Escola Pequeno Principe',  '98765432000198', 'Rua Sergipe',               '310',  'Funcionarios',   'Belo Horizonte', 'MG', '30130170',
     'contato@pequenoprincipe.com.br',  '(31) 98888-5000', 'BASICO',     FALSE, '2023-03-01', '2025-03-01', 150,
     ARRAY['PORTAL_ALUNO']),
    ('Centro Educacional Aurora','24681357000140', 'Av. Brasil',                '75',   'Centro',         'Betim',          'MG', '32600000',
     NULL,                              NULL,              'BASICO',     TRUE,  '2025-03-10', '2027-03-10', NULL,
     ARRAY[]::TEXT[]);

INSERT INTO ESCOLA (NOME, CNPJ_CPF, LOGRADOURO, NUMERO, BAIRRO, CIDADE, ESTADO, CEP, EMAIL, TELEFONE, PLANO, ATIVO,
                    INICIO_VIGENCIA, FIM_VIGENCIA, LIMITE_USUARIOS)
SELECT m.nome, m.cnpj, m.logradouro, m.numero, m.bairro, m.cidade, m.estado, m.cep, m.email, m.telefone, m.plano,
       m.ativo, m.inicio_vigencia, m.fim_vigencia, m.limite_usuarios
FROM mock_escola m
WHERE NOT EXISTS (SELECT 1 FROM ESCOLA e WHERE e.CNPJ_CPF = m.cnpj);

-- Escolas mockadas antes da V6/V7 tem plano nulo e o endereco inteiro em
-- LOGRADOURO; completa so o que ainda nao foi preenchido (nem editado pela tela).
UPDATE ESCOLA e SET PLANO = m.plano
FROM mock_escola m
WHERE e.CNPJ_CPF = m.cnpj AND e.PLANO IS NULL;

UPDATE ESCOLA e
SET LOGRADOURO = m.logradouro, NUMERO = m.numero, BAIRRO = m.bairro, CIDADE = m.cidade, ESTADO = m.estado,
    CEP = m.cep
FROM mock_escola m
WHERE e.CNPJ_CPF = m.cnpj AND e.CIDADE IS NULL;

-- Toda escola mockada tem vigencia. O inicio nao muda depois do cadastro, entao
-- so preenche onde ainda esta vazio; o termino e editavel e nao e sobrescrito.
UPDATE ESCOLA e
SET INICIO_VIGENCIA = m.inicio_vigencia,
    FIM_VIGENCIA = COALESCE(e.FIM_VIGENCIA, m.fim_vigencia),
    LIMITE_USUARIOS = COALESCE(e.LIMITE_USUARIOS, m.limite_usuarios)
FROM mock_escola m
WHERE e.CNPJ_CPF = m.cnpj AND e.INICIO_VIGENCIA IS NULL;

-- Modulos so para escolas que ainda nao tem nenhum.
INSERT INTO ESCOLA_MODULO (ESCOLA_ID, MODULO)
SELECT e.ID_ESCOLA, modulo
FROM mock_escola m
JOIN ESCOLA e ON e.CNPJ_CPF = m.cnpj
CROSS JOIN LATERAL unnest(m.modulos) AS modulo
WHERE NOT EXISTS (SELECT 1 FROM ESCOLA_MODULO em WHERE em.ESCOLA_ID = e.ID_ESCOLA);

COMMIT;
