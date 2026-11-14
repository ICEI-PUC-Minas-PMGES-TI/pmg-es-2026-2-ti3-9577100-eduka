import { useState, type InputHTMLAttributes } from 'react'
import { Campo, type PropsControle } from '../../../../components/formulario/Campo/Campo'
import { ModalCadastro } from '../../../../components/modal/ModalCadastro/ModalCadastro'
import { Select } from '../../../../components/formulario/Select/Select'
import { ApiError } from '../../../../services/api'
import {
  ROTULOS_TIPO_EMPRESA,
  type AtivarRecebimentosRequest,
  type ContaRecebimento,
  type TipoContaBancaria,
  type TipoEmpresa,
} from '../../../../types/financeiro/contaRecebimento'
import { formatarCnpjCpf, mascararCep, mascararTelefone, somenteDigitos } from '../../../../utils/formatters'

type Campos = {
  razaoSocial: string
  emailFinanceiro: string
  telefoneCelular: string
  tipoEmpresa: TipoEmpresa | ''
  faturamentoMensal: string
  logradouro: string
  numero: string
  complemento: string
  bairro: string
  cep: string
  codigoBanco: string
  agencia: string
  conta: string
  digitoConta: string
  tipoConta: TipoContaBancaria | ''
}
type Erros = Partial<Record<keyof Campos, string>>
type CampoTexto = Exclude<keyof Campos, 'tipoEmpresa' | 'tipoConta'>
type AtributosTexto = Pick<InputHTMLAttributes<HTMLInputElement>, 'placeholder' | 'maxLength' | 'inputMode'>

const TIPOS_EMPRESA = (Object.keys(ROTULOS_TIPO_EMPRESA) as TipoEmpresa[]).map((t) => ({
  valor: t,
  rotulo: ROTULOS_TIPO_EMPRESA[t],
}))
const TIPOS_CONTA: { valor: TipoContaBancaria; rotulo: string }[] = [
  { valor: 'CORRENTE', rotulo: 'Corrente' },
  { valor: 'POUPANCA', rotulo: 'Poupança' },
]

/** "1.234,56" ou "1234.56" -> 1234.56; NaN se não for número. */
function lerValor(texto: string): number {
  const limpo = texto.trim().replace(/\s/g, '')
  const normalizado = limpo.includes(',') ? limpo.replace(/\./g, '').replace(',', '.') : limpo
  return normalizado === '' ? Number.NaN : Number(normalizado)
}

function traduzirErro(erro: unknown): string {
  if (erro instanceof ApiError && erro.codigo === 'PROVEDOR_RECUSOU') {
    return `O provedor de pagamento recusou o cadastro: ${erro.detalhe ?? erro.message}`
  }
  if (erro instanceof ApiError && erro.codigo === 'ATIVACAO_EM_ANDAMENTO') {
    return 'A conta já está sendo criada. Aguarde alguns segundos e atualize a página.'
  }
  if (erro instanceof ApiError) return erro.detalhe ?? erro.message
  return 'Erro inesperado ao ativar os recebimentos.'
}

/** Num reenvio (erro na criação), o cadastro volta preenchido; os dados bancários, nunca. */
function camposIniciais(conta: ContaRecebimento): Campos {
  return {
    razaoSocial: conta.razaoSocial ?? '',
    emailFinanceiro: conta.emailFinanceiro ?? '',
    telefoneCelular: conta.telefoneCelular ? mascararTelefone(conta.telefoneCelular) : '',
    tipoEmpresa: conta.tipoEmpresa ?? '',
    faturamentoMensal: conta.faturamentoMensal != null ? String(conta.faturamentoMensal).replace('.', ',') : '',
    logradouro: conta.logradouro ?? '',
    numero: conta.numero ?? '',
    complemento: conta.complemento ?? '',
    bairro: conta.bairro ?? '',
    cep: conta.cep ? mascararCep(conta.cep) : '',
    codigoBanco: '',
    agencia: '',
    conta: '',
    digitoConta: '',
    tipoConta: '',
  }
}

type ModalAtivarRecebimentosProps = {
  conta: ContaRecebimento
  onFechar: () => void
  /** Envia; deve lançar ApiError em caso de falha. */
  onAtivar: (dados: AtivarRecebimentosRequest) => Promise<void>
}

/**
 * Cadastro da conta de recebimento (spec §8.1). O CNPJ vem do cadastro da
 * escola. Os dados bancários vão para o provedor e não ficam no Edúka.
 */
export function ModalAtivarRecebimentos({ conta, onFechar, onAtivar }: ModalAtivarRecebimentosProps) {
  const [campos, setCampos] = useState<Campos>(() => camposIniciais(conta))
  const [erros, setErros] = useState<Erros>({})

  function alterar<K extends keyof Campos>(campo: K, valor: Campos[K]) {
    setCampos((c) => ({ ...c, [campo]: valor }))
    setErros((e) => ({ ...e, [campo]: undefined }))
  }

  function validar(): boolean {
    const novos: Erros = {}
    if (campos.razaoSocial.trim().length < 3) novos.razaoSocial = 'Informe a razão social.'
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(campos.emailFinanceiro.trim())) novos.emailFinanceiro = 'E-mail inválido.'
    if (!/^\d{10,11}$/.test(somenteDigitos(campos.telefoneCelular))) novos.telefoneCelular = 'Telefone inválido.'
    if (!campos.tipoEmpresa) novos.tipoEmpresa = 'Escolha o tipo de empresa.'
    if (!(lerValor(campos.faturamentoMensal) > 0)) novos.faturamentoMensal = 'Informe um valor maior que zero.'
    if (!campos.logradouro.trim()) novos.logradouro = 'Informe o logradouro.'
    if (!campos.numero.trim()) novos.numero = 'Informe o número.'
    if (!campos.bairro.trim()) novos.bairro = 'Informe o bairro.'
    if (!/^\d{8}$/.test(somenteDigitos(campos.cep))) novos.cep = 'CEP inválido.'
    if (!/^\d{3}$/.test(campos.codigoBanco)) novos.codigoBanco = 'Código do banco com 3 dígitos.'
    if (!/^\d{1,5}$/.test(campos.agencia)) novos.agencia = 'Agência com até 5 dígitos.'
    if (!/^\d{1,12}$/.test(campos.conta)) novos.conta = 'Conta com até 12 dígitos.'
    if (!/^[0-9Xx]{1,2}$/.test(campos.digitoConta)) novos.digitoConta = 'Dígito inválido.'
    if (!campos.tipoConta) novos.tipoConta = 'Escolha o tipo de conta.'
    setErros(novos)
    return Object.keys(novos).length === 0
  }

  function enviar() {
    return onAtivar({
      razaoSocial: campos.razaoSocial.trim(),
      emailFinanceiro: campos.emailFinanceiro.trim(),
      telefoneCelular: somenteDigitos(campos.telefoneCelular),
      tipoEmpresa: campos.tipoEmpresa as TipoEmpresa,
      faturamentoMensal: Math.round(lerValor(campos.faturamentoMensal) * 100) / 100,
      logradouro: campos.logradouro.trim(),
      numero: campos.numero.trim(),
      complemento: campos.complemento.trim() || null,
      bairro: campos.bairro.trim(),
      cep: somenteDigitos(campos.cep),
      dadosBancarios: {
        codigoBanco: campos.codigoBanco,
        agencia: campos.agencia,
        conta: campos.conta,
        digitoConta: campos.digitoConta,
        tipo: campos.tipoConta as TipoContaBancaria,
      },
    })
  }

  /** Input de texto de um campo, com máscara opcional aplicada ao digitar. */
  function texto(campo: CampoTexto, extra: AtributosTexto & { mascara?: (v: string) => string } = {}) {
    const { mascara, ...atributos } = extra
    return (props: PropsControle) => (
      <input
        {...props}
        {...atributos}
        name={campo}
        value={campos[campo]}
        onChange={(e) => alterar(campo, mascara ? mascara(e.target.value) : e.target.value)}
      />
    )
  }

  const soDigitos = (max: number) => (v: string) => somenteDigitos(v).slice(0, max)

  return (
    <ModalCadastro
      titulo="Ativar recebimentos online"
      rotuloSalvar="Enviar cadastro"
      onFechar={onFechar}
      validar={validar}
      traduzirErro={traduzirErro}
      onEnviar={enviar}
    >
      <Campo
        label="CNPJ"
        dica={conta.cnpj ? 'Vem do cadastro da escola.' : 'Informe o CNPJ no cadastro da escola antes de ativar.'}
        largo
      >
        {(props) => (
          <input
            {...props}
            name="cnpj"
            value={conta.cnpj ? formatarCnpjCpf(conta.cnpj) : 'Não informado'}
            readOnly
            disabled
          />
        )}
      </Campo>
      <Campo label="Razão social" erro={erros.razaoSocial} obrigatorio largo>
        {texto('razaoSocial', { maxLength: 150 })}
      </Campo>
      <Campo label="E-mail financeiro" erro={erros.emailFinanceiro} obrigatorio>
        {texto('emailFinanceiro', { maxLength: 150, inputMode: 'email', placeholder: 'financeiro@escola.com.br' })}
      </Campo>
      <Campo label="Celular" erro={erros.telefoneCelular} obrigatorio>
        {texto('telefoneCelular', { inputMode: 'tel', mascara: mascararTelefone, placeholder: '(31) 99999-8888' })}
      </Campo>
      <Campo label="Tipo de empresa" erro={erros.tipoEmpresa} obrigatorio>
        {(props) => (
          <Select<TipoEmpresa | ''>
            {...props}
            name="tipoEmpresa"
            opcoes={TIPOS_EMPRESA}
            valor={campos.tipoEmpresa}
            onChange={(v) => alterar('tipoEmpresa', v)}
          />
        )}
      </Campo>
      <Campo label="Faturamento mensal (R$)" erro={erros.faturamentoMensal} obrigatorio>
        {texto('faturamentoMensal', { inputMode: 'decimal', placeholder: 'Ex.: 50.000,00' })}
      </Campo>
      <Campo label="Logradouro" erro={erros.logradouro} obrigatorio largo>
        {texto('logradouro', { maxLength: 200 })}
      </Campo>
      <Campo label="Número" erro={erros.numero} obrigatorio>
        {texto('numero', { maxLength: 10 })}
      </Campo>
      <Campo label="Complemento" erro={erros.complemento}>
        {texto('complemento', { maxLength: 100 })}
      </Campo>
      <Campo label="Bairro" erro={erros.bairro} obrigatorio>
        {texto('bairro', { maxLength: 100 })}
      </Campo>
      <Campo label="CEP" erro={erros.cep} obrigatorio>
        {texto('cep', { inputMode: 'numeric', mascara: mascararCep, placeholder: '30140-071' })}
      </Campo>
      <Campo
        label="Banco (código)"
        erro={erros.codigoBanco}
        dica="Os dados bancários vão direto para o provedor de pagamento e não ficam no Edúka."
        obrigatorio
        largo
      >
        {texto('codigoBanco', { inputMode: 'numeric', mascara: soDigitos(3), placeholder: 'Ex.: 341' })}
      </Campo>
      <Campo label="Agência" erro={erros.agencia} obrigatorio>
        {texto('agencia', { inputMode: 'numeric', mascara: soDigitos(5) })}
      </Campo>
      <Campo label="Conta" erro={erros.conta} obrigatorio>
        {texto('conta', { inputMode: 'numeric', mascara: soDigitos(12) })}
      </Campo>
      <Campo label="Dígito" erro={erros.digitoConta} obrigatorio>
        {texto('digitoConta', { maxLength: 2, mascara: (v) => v.replace(/[^0-9Xx]/g, '').slice(0, 2) })}
      </Campo>
      <Campo label="Tipo de conta" erro={erros.tipoConta} obrigatorio>
        {(props) => (
          <Select<TipoContaBancaria | ''>
            {...props}
            name="tipoConta"
            opcoes={TIPOS_CONTA}
            valor={campos.tipoConta}
            onChange={(v) => alterar('tipoConta', v)}
          />
        )}
      </Campo>
    </ModalCadastro>
  )
}
