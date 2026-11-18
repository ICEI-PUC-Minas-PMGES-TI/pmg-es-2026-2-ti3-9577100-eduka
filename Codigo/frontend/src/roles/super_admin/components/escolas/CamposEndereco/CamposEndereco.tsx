import { Campo } from '../../../../../components/formulario/Campo/Campo'
import { Select, type OpcaoSelect } from '../../../../../components/formulario/Select/Select'
import { mascararCep } from '../../../../../utils/formatters'
import {
  MAX_BAIRRO,
  MAX_CIDADE,
  MAX_LOGRADOURO,
  MAX_NUMERO,
  UFS,
  type ErrosEndereco,
  type ValoresEndereco,
} from './endereco'
import styles from './CamposEndereco.module.css'

// "UF" é a opção vazia (sem estado), como no <select> antigo.
const OPCOES_UF: OpcaoSelect<string>[] = [
  { valor: '', rotulo: 'UF' },
  ...UFS.map((uf) => ({ valor: uf, rotulo: uf })),
]

type CamposEnderecoProps = {
  valores: ValoresEndereco
  erros: ErrosEndereco
  onAlterar: (campo: keyof ValoresEndereco, valor: string) => void
}

/**
 * Endereço da escola quebrado em campos (IU03). Ocupa a linha inteira do grid
 * do formulário e tem o próprio grid: logradouro/número/bairro e
 * cidade/estado/CEP.
 */
export function CamposEndereco({ valores, erros, onAlterar }: CamposEnderecoProps) {
  return (
    <div className={styles.grid}>
      <Campo label="Logradouro" erro={erros.logradouro} obrigatorio className={styles.principal}>
        {(props) => (
          <input
            {...props}
            name="logradouro"
            maxLength={MAX_LOGRADOURO}
            autoComplete="address-line1"
            placeholder="Rua, avenida…"
            value={valores.logradouro}
            onChange={(e) => onAlterar('logradouro', e.target.value)}
          />
        )}
      </Campo>
      <Campo label="Número" erro={erros.numero} obrigatorio>
        {(props) => (
          <input
            {...props}
            name="numero"
            maxLength={MAX_NUMERO}
            placeholder="Ex.: 268"
            value={valores.numero}
            onChange={(e) => onAlterar('numero', e.target.value)}
          />
        )}
      </Campo>
      <Campo label="Bairro" erro={erros.bairro} obrigatorio>
        {(props) => (
          <input
            {...props}
            name="bairro"
            maxLength={MAX_BAIRRO}
            autoComplete="address-level3"
            placeholder="Ex.: Centro"
            value={valores.bairro}
            onChange={(e) => onAlterar('bairro', e.target.value)}
          />
        )}
      </Campo>
      <Campo label="Cidade" erro={erros.cidade} obrigatorio className={styles.principal}>
        {(props) => (
          <input
            {...props}
            name="cidade"
            maxLength={MAX_CIDADE}
            autoComplete="address-level2"
            placeholder="Ex.: Belo Horizonte"
            value={valores.cidade}
            onChange={(e) => onAlterar('cidade', e.target.value)}
          />
        )}
      </Campo>
      <Campo label="Estado" erro={erros.estado} obrigatorio>
        {(props) => (
          <Select
            {...props}
            name="estado"
            placeholder="UF"
            opcoes={OPCOES_UF}
            valor={valores.estado}
            onChange={(uf) => onAlterar('estado', uf)}
          />
        )}
      </Campo>
      <Campo label="CEP" erro={erros.cep} obrigatorio>
        {(props) => (
          <input
            {...props}
            name="cep"
            inputMode="numeric"
            autoComplete="postal-code"
            placeholder="00000-000"
            value={valores.cep}
            onChange={(e) => onAlterar('cep', mascararCep(e.target.value))}
          />
        )}
      </Campo>
    </div>
  )
}
