-- Ultimo login concluido de cada usuario (tokens emitidos), para o uso de
-- cada escola no Painel de Escolas (IU02). Nulo enquanto o usuario nao
-- entrou depois desta versao.
--
-- Nasceu como V40 e colidiu com V40__tipo_da_cobranca na developer; foi
-- renumerada para V42. IF NOT EXISTS: banco local que ja rodou a V40 antiga
-- tem a coluna.
ALTER TABLE USUARIO ADD COLUMN IF NOT EXISTS ULTIMO_ACESSO_EM TIMESTAMPTZ;
