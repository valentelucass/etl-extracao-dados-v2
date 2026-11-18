# 0745 — Manifestos iniciado; comparação completa em investigação

06/11/2026. O usuário selecionou Manifestos6399, pediu abrir a ESL e continuar
comparando e corrigindo o código, sem perguntas intermediárias. Autorizou também
um chat auxiliar de código. Coletas/Fretes permanecem encerrados na camada0744;
nenhuma execução antiga foi promovida a aceite operacional ou produtivo.

Executor principal real: `01a1b0da-5f23-7512-8ec2-92ab8fdac018`. Chat auxiliar
de código: `01a1b0ed-71e6-7171-8cfc-af67512642d3`. O auxiliar não executa
Original, Java, Maven, Source, SQL ou navegador. O principal continua o único
executor pesado; não houve outro agente.

## Observação nova da ESL

A sessão foi autenticada pelo usuário. O cadastro confirmou Relação de Manifestos
Detalhada6399, raiz Manifesto,91campos e18filtros. A posição exibida60 está em
duas colunas; títulos Coletas, Entregas, Retiradas e Despachos se repetem. Os
itemIds e as coordenadas físicas foram registrados separadamente do número/título.
Nenhuma definição, conta ou permissão foi salva ou alterada.

O calendário confirmou exclusivamente `manifests[service_date]` em17/10/2026,
com os outros17filtros vazios. Foi solicitada uma exportação UI independente,
arquivo1769668, gerado06/11/2026às10:04:21,315linhas/91colunas. O primeiro
downloadMedia excedeu o prazo e não devolveu caminho; a lista confirmou Baixado.
O controle normal de download devolveu depois o arquivo local. Não foi repetida
a exportação nem executada nova chamada API própria.

## Comparação efetivamente executada

Base deste chat:
`C:\Users\suporte\Documents\Codex\2026-11-06\etl-v2-proxima-entidade`.

Cinco Originals Source0 encerraram0, dentro de1GiB/300s, sem SQL, com estado
restaurado e slot vazio. Leituras de negócio ocorreram somente dentro desses
Originals. A fonte Main0645 conservou SHA256B5F2CAD5 e1.015.545bytes. O Excel
novo tem99.753bytes/SHA25685BA249950E5FB0802E71FEC803B5F9B715C0784D8925F71014D34272ECB09E6.
O XLSX foi percorrido atéEOF em todos os membros ZIP, com epoch1900, coordenadas,
tipos e estilos observados. Não houve edição dos arquivos de negócio.

- Fonte e Excel:315linhas/17chaves e multiplicidades iguais.
- Diagnóstico inicial:86/91colunas no modelo candidato; não foi aceite.
- O candidato inicial associou indevidamente Chegada/Fechamento: o sucessor
  usa `closed_at`/`finished_at`, respectivamente. O status `closed` aparece
  como Finalizado no Excel. Há omissão de comentários vazios e apresentação
  NULL→Não específica do comprovante mobile. São decisões de apresentação
  candidatas; os tipos/presenças RAW não foram reescritos.
- Sucessor:90/91colunas. A comparação conjunta dos primeiros90campos também
  coincidiu em todas as ocorrências, preservando associações porID e duplicatas.
- Restam2ocorrências de1raiz no comprovante mobile: Não na referência histórica,
  Sim no Excel novo. Diferença preservada; causa temporal ou erro ETL não provados.

Controles sanitizados: `work/manifestos-input-inspection0745-01/`,
`work/manifestos-workbook-inspection0745-01/`,
`work/manifestos-full91-diagnostic0745-01/`,
`work/manifestos-full91-diagnostic0745-02/` e
`work/manifestos-mobile-cohort0745-01/`. As diferenças com identificadores
permanecem somente no arquivo privado do último diretório. Os bytes dos candidatos
01/02 estão preservados em `work/manifestos-6399/diagnostic-input-snapshots/`;
os caminhos de trabalho iniciais foram atualizados no preparo do sucessor, sem
reescrever os recibos históricos, e os snapshots preservam seus hashes exatos.

## Código e próximos passos

O auxiliar implementou o planejamento explícito
`MANIFESTOS_6399_NATIVE91_V1`,18filtros,GET_WITH_QUERY e encaminhamento pelo
Main, com26testes novos escritos. Ainda não foi executado FORMAT/TEST.

Foi demonstrado outro limite: `readCommonRows` exige31colunas e sua máscara
marca apenas os primeiros63itens. Está em implementação uma leitura explícita
de91colunas com mapas de presença completos, seguida de adapter de conteúdo
com catálogo/políticas explícitos e contraprovas. Não há qualificação antecipada.

1. Concluir/revisar o código, incluindo as regras de apresentação sem apagar as
   duas diferenças, e executar FORMAT/TEST no Original próprio e limites atuais.
2. Executar o Java final sobre o par retido e confrontar o resultado completo com
   a referência independente; nenhum aceite por contagem ou esforço parcial.
3. Investigar as duas diferenças. Uma eventual captura nova deve ter contrato,
   autoridade, admissão e orçamento próprios antes do despacho; fixtures ou
   Source0 não liberam Source1, Main, SQL, produção ou corte.

CanônicoFB923CC52CBB6740386AB1D62D9ADD4663C04675E6FCE645EEB08B36EC6DA7E6 e
AUX28/49A25567 preservados. V1 permanece pausado e intocado. Entidades por Data
Export; GraphQL somente Usuários. Sem SQL, DDL, deploy, alteração de serviços,
contas, ACL, corte de fonte ou comprovação do dashboard.
