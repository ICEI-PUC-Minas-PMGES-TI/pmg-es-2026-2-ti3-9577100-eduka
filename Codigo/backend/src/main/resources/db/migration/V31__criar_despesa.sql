-- ---------------------------------------------------------------------------
-- Despesas da escola (RF18) e as categorias em que sao lancadas.
-- ---------------------------------------------------------------------------
-- Cada escola tem as proprias categorias. As padrao (Salarios, Contas,
-- Materiais...) sao criadas pela aplicacao na primeira consulta da escola
-- (ver CategoriaDespesaService), entao escola nova nao precisa de seed. O nome
-- nao se repete na escola, sem diferenciar maiusculas.
CREATE TABLE CATEGORIA_DESPESA (
    ID_CATEGORIA_DESPESA  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    ESCOLA_ID             UUID NOT NULL REFERENCES ESCOLA(ID_ESCOLA),
    NOME                  VARCHAR(80) NOT NULL,
    CRIADO_EM             TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX categoria_despesa_escola_nome_uk ON CATEGORIA_DESPESA (ESCOLA_ID, LOWER(NOME));

-- Despesa e registro interno da escola: nao passa pelo Asaas. Que a categoria
-- e da mesma escola e conferido em DespesaService.
--
-- Cancelar muda a SITUACAO, nao apaga: a despesa continua no historico.
-- PAGA_EM guarda quando foi dada como paga; voltar para A_PAGAR zera.
--
-- Nada cascateia: categoria com despesa nao some sem decisao explicita.
CREATE TABLE DESPESA (
    ID_DESPESA       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    ESCOLA_ID        UUID NOT NULL REFERENCES ESCOLA(ID_ESCOLA),
    CATEGORIA_ID     UUID NOT NULL REFERENCES CATEGORIA_DESPESA(ID_CATEGORIA_DESPESA),
    DESCRICAO        VARCHAR(255) NOT NULL,
    VALOR            NUMERIC(10, 2) NOT NULL,
    DATA_VENCIMENTO  DATE NOT NULL,
    SITUACAO         VARCHAR(20) NOT NULL DEFAULT 'A_PAGAR',
    PAGA_EM          TIMESTAMPTZ,
    CANCELADA_EM     TIMESTAMPTZ,
    CRIADO_EM        TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ATUALIZADO_EM    TIMESTAMPTZ,
    CONSTRAINT ck_despesa_situacao CHECK (SITUACAO IN ('A_PAGAR', 'PAGA', 'CANCELADA')),
    CONSTRAINT ck_despesa_valor CHECK (VALOR > 0)
);

-- Consulta tipica: as despesas do mes da escola, pelo vencimento.
CREATE INDEX despesa_vencimento_idx ON DESPESA (ESCOLA_ID, DATA_VENCIMENTO);
CREATE INDEX despesa_categoria_id_idx ON DESPESA (CATEGORIA_ID);
