import { Campo } from '../../../../components/Campo/Campo'
import { Switch } from '../../../../components/Switch/Switch'
import { mascararCpf, mascararTelefone } from '../../../../utils/formatters'
import type { CamposResponsavel, ErrosResponsavel } from './camposResponsavel'
import styles from './ResponsaveisDoAluno.module.css'

// Limites do ResponsavelRequest no backend.
const MAX_NOME = 150
const MAX_EMAIL = 150
const MAX_PARENTESCO = 30

type CamposDoResponsavelProps = {
  valores: CamposResponsavel
  erros: ErrosResponsavel
  /** Prefixo do `name` de cada campo, para o formulário focar o inválido. */
  prefixo: string
  /** Dica do CPF quando o responsável não é o financeiro. */
  dicaCpf?: string
  onAlterar: <K extends keyof CamposResponsavel>(campo: K, valor: CamposResponsavel[K]) => void
}

/** Campos de um responsável, iguais no cadastro do aluno e na edição. */
export function CamposDoResponsavel({ valores, erros, prefixo, dicaCpf, onAlterar }: CamposDoResponsavelProps) {
  const nome = (campo: keyof CamposResponsavel) => `${prefixo}-${campo}`
  return (
    <div className={styles.grid}>
      <Campo label="Nome" erro={erros.nome} obrigatorio className={styles.span4}>
        {(props) => (
          <input
            {...props}
            name={nome('nome')}
            maxLength={MAX_NOME}
            value={valores.nome}
            onChange={(e) => onAlterar('nome', e.target.value)}
          />
        )}
      </Campo>
      <Campo label="Parentesco" erro={erros.parentesco} className={styles.span2}>
        {(props) => (
          <input
            {...props}
            name={nome('parentesco')}
            maxLength={MAX_PARENTESCO}
            placeholder="Ex.: Mãe"
            value={valores.parentesco}
            onChange={(e) => onAlterar('parentesco', e.target.value)}
          />
        )}
      </Campo>
      <Campo
        label="CPF"
        erro={erros.cpf}
        obrigatorio={valores.financeiro}
        dica={valores.financeiro ? 'Vai no PIX e no boleto, em nome de quem paga.' : dicaCpf}
        className={styles.span2}
      >
        {(props) => (
          <input
            {...props}
            name={nome('cpf')}
            inputMode="numeric"
            placeholder="000.000.000-00"
            value={valores.cpf}
            onChange={(e) => onAlterar('cpf', mascararCpf(e.target.value))}
          />
        )}
      </Campo>
      <Campo label="E-mail" erro={erros.email} className={styles.span2}>
        {(props) => (
          <input
            {...props}
            name={nome('email')}
            type="email"
            maxLength={MAX_EMAIL}
            value={valores.email}
            onChange={(e) => onAlterar('email', e.target.value)}
          />
        )}
      </Campo>
      <Campo label="Telefone" erro={erros.telefone} className={styles.span2}>
        {(props) => (
          <input
            {...props}
            name={nome('telefone')}
            type="tel"
            placeholder="(00) 00000-0000"
            value={valores.telefone}
            onChange={(e) => onAlterar('telefone', mascararTelefone(e.target.value))}
          />
        )}
      </Campo>
      <div className={styles.linhaInteira}>
        <Switch
          name={nome('financeiro')}
          checked={valores.financeiro}
          onChange={(financeiro) => onAlterar('financeiro', financeiro)}
          rotuloLigado="Responsável financeiro: paga as cobranças deste aluno"
          rotuloDesligado="Não é o responsável financeiro"
        />
      </div>
    </div>
  )
}
