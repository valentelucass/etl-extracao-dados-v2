# 0161 — Revisão de contratos e seleção da suíte integral

## Estado e autorização

**EM_EXECUCAO**, sem entrega parcial. Mantém integralmente A–N/P01–P24 adotados,
predecessor entregue 0154, V099, 39/45 e 67/115. Progresso anterior0160 SHA256
`4c089c5fdd5898c7fe65ddb42e26e5e2fe7bed45343d2c4da93c48b9370d350b`.
Sem perguntas, subagentes ou nova aprovação. Nenhum segredo, API, V1 executada,
produção, deploy, cutover, serviço ou índice Git real. SQL somente shadow local,
autenticação Windows, duas travas, outer rollback e commit de domínio bloqueado.

## Evidência reconciliada

- physical18 terminou: 2.072 unitários, zero falhas/erros, quatro skips históricos;
  seis IT sem falha/erro/skip, rollback confirmado. Replay de três ciclos com
  revisão posterior passou nos cinco fatos e 19 saídas; referências e suplementos
  também passaram. Exit1 decorreu exclusivamente de cobertura global insuficiente
  da seleção dirigida. Limites de cobertura preservados; tentativa não qualifica pacote.
- `verify-integral-01` iniciou em 2026-09-15T02:16:57Z, PID do runner44260,
  orçamento3.600s. Executa verify e receipt de medição no snapshot próprio. A
  conferência parcial dos XML registrou315IT concluídas sem falhas/erros/skips.
  **Não há resultado final conhecido neste checkpoint.** Reconciliar antes de repetir.
- A revisão de identidades revelou que o perfil shadow possuía includes explícitos
  sem `Integral*IT`. O POM canônico foi corrigido para incluir essa família. A
  tentativa01 conserva seu snapshot anterior e não comprova os novos IT na suíte
  completa. As provas dirigidas anteriores permanecem válidas no alcance registrado.
  É necessária a execução completa seguinte com o perfil corrigido.
- `SyntheticSourceScopeTest` acrescentou oito casos de fronteira da origem/tenant;
  está incluído em01. Nenhum skip foi acrescentado para ocultar falha.
- Catálogo ganhou `CONTRATO.md`, revisão11 entidades e revisão de46 candidatos de
  classes/9 tipos novos. As três classes de transporte Raster apenas de teste foram
  movidas byte a byte para src/test; regressão e ausência no JAR final ainda precisam
  de conferência final. As nove remoções anteriores permanecem preservadas.
- Revisão das regras V1 feita somente por leitura; decisões V2 de identidade,
  USERS_SNAPSHOT, precisão temporal, omissão terminal e série fiscal não inferida
  permanecem. V099 regression03 conserva16PASS e rollback, sem reaplicação.
- Sucessor IntegralChainSuccession e consumidor EntityAlignmentSuccession passaram
  análise sintática. Ainda não há manifesto final. Gerador privado
  `Write-IntegralManifest.cjs` preparado, não executado; exige checkpoint0162 e
  preserva candidatos anteriores. Base3.349 arquivos e manifestos históricos intactos.
- Scripts privados de exemplos/JAR/reconciliação de IDs passaram análise sintática.
  **Nenhum JAR extraído foi executado ainda.** O roteiro agora prevê A2/B24 completos,
  ausênciaUSER, valor de oráculo errado e alvo de vínculoLOC inexistente. Todos terão
  recibo próprio, processo limitado, hashes e reconciliação SQL. Não são PASS antecipado.

## Recuperação e próximos passos — até três

1. Reconciliar verify-integral-01, executar verify-integral-02 com includes corrigidos
   e corrigir qualquer falha/cobertura sem reduzir gates; reconciliar IDs e quatro skips.
2. Gerar exemplos/pacote do build qualificado; executar os cinco casos previstos no
   JAR extraído e o consumidor de campanha existente aplicável, com rollback.
3. Concluir matrizes A–N/45/P01–P24/G01–G08, segunda revisão, sucessão/consumidores,
   scanner canônico completo, diff/overlay aplicados em cópia, selo/readback e entrega.

V100–V102 já instaladas: não reaplicar/regravar. Próxima versão livreV103 apenas se
defeito demonstrado exigir. Não repetir efeito desconhecido. Toda parcela local
autorizada ainda pendente deve ser concluída antes de finalizar.
