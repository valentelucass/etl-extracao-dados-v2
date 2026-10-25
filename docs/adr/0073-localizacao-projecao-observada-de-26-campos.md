# ADR 0073 — Projeção observada de26 campos em Localização

24/10/2026. RG-LOC-019,owner técnico Root;owner de negócio não declarado.
Duas leituras terminais de8656/dia17 encontraram37 linhas iguais,26 campos,com
37 recusas UNAPPROVED_SOURCE_PATH pelo mapper estrito dos17 paths anteriores.

Factory explícita observed20261024 conserva o perfil default17 e acrescenta
somente9 paths observados. Preserva todo o conteúdo desses paths em payload e
presença/proveniência,com matriz própria. invoices_mapping é lista limitada a1000
strings de até1024 unidades;objetos/listas inesperados e paths desconhecidos continuam
recusados. Campos extras não recebem interpretação fiscal,identidade de componente
ou relação por inferência. Tipos não ratificados permanecem marcados como bruto.

A entrada String mantém os tokens numéricos capturados pelo leitor existente.
O perfil não admite automaticamente o Main,gate de contrato,persistência,promoção
ou publicação. A matriz17 e demais perfis não são reclassificados. Exemplo sintético:
lista de invoices e finished_at nulo são preservados no perfil26,continuam recusados
no default17;unknown_path permanece recusado em ambos. RED causal de factory
ausente e GREEN/pacote/prova real constam em STATES. Nenhum SQL/DDL é aplicado.
