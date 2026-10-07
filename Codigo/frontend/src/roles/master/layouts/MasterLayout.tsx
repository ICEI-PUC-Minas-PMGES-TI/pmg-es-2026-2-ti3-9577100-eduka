import type { ReactNode } from 'react'
import { PainelLayout } from '../../../components/PainelLayout/PainelLayout'

type MasterLayoutProps = {
  titulo: string
  subtitulo?: ReactNode
  children: ReactNode
}

// Sem menu flutuante por enquanto: o master só tem a tela de início.
export function MasterLayout(props: MasterLayoutProps) {
  return <PainelLayout contexto="Gestão da escola" {...props} />
}
