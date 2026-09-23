import { Campo } from '../../../../components/Campo/Campo'
import { mascararCep } from '../../../../utils/formatters'
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
      <Campo label="Logradouro" erro={erros.logradouro} className={styles.principal}>
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
      <Campo label="Número" erro={erros.numero}>
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
      <Campo label="Bairro" erro={erros.bairro}>
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
      <Campo label="Cidade" erro={erros.cidade} className={styles.principal}>
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
      <Campo label="Estado" erro={erros.estado}>
        {(props) => (
          <select
            {...props}
            name="estado"
            autoComplete="address-level1"
            value={valores.estado}
            onChange={(e) => onAlterar('estado', e.target.value)}
          >
            <option value="">UF</option>
            {UFS.map((uf) => (
              <option key={uf} value={uf}>
                {uf}
              </option>
            ))}
          </select>
        )}
      </Campo>
      <Campo label="CEP" erro={erros.cep}>
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
