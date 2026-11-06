-- Ultimo login concluido de cada usuario (tokens emitidos), para o uso de
-- cada escola no Painel de Escolas (IU02). Nulo enquanto o usuario nao
-- entrou depois desta versao.
ALTER TABLE USUARIO ADD COLUMN ULTIMO_ACESSO_EM TIMESTAMPTZ;
