# Retomada — B62 investigou a fonte; caracterização pendente

SOURCE_INVESTIGATION_COMPLETE_CHARACTERIZATION_PENDING. B62 adotado neste chat;
o usuário depois autorizou procurar inputs ou usar .env para consultar APIs.
STATES conserva a autoridade. Nenhum checkbox ou rota nova.

[Checkpoint0055](checkpoints/0055-bloco62-investigacao-real-limitada.md),
SHA-256 fa6a45095acf90e0fc42df12b1eba0991a7ec383232e234c4af5c793e0b2fadd.
[Relatório](../catalogos/bloco62-investigacao/RELATORIO.md) e
[perfil sanitizado](../catalogos/bloco62-investigacao/source-summary.json).

.env legado localizado/validado sem exibir valores. Quatro chamadas seriais
HTTP200/curl0: schema/amostra Users e info/amostra Coletas. Users: cinco IDs/name
STRING, mais páginas; input updatedAt String, sem semântica temporal comprovada.
Coletas: ROOT_ARRAY, cinco linhas para dois IDs INTEGER; status_updated_at ausente.
A forma diverge do envelope data exigido na composição operacional/B58. O
normalizer aceita a forma, mas isso não autoriza promoção ou novo release.

Dados só em memória; quatro reservas e quatro resultados, orçamento4/4 encerrado.
Dia09/09 exploratório, Users first5, Coletas per2, timeout30s, duração180s,
metadados1MiB/dados64KiB, sem retry/redirect. Sem SQL, runtime operacional, UAC,
escrita remota/produtiva, rotação, bootstrap ou cutover. V2-041 continua aberto;
a autorização posterior desta investigação não comprova invalidação de segredo.

Sonda: dois positivos/oito recusas offline; tentativa de repetição bloqueada
antes de ler credencial ou fazer HTTP. Verify offline isolado Java17/heap512MiB:
1405 testes, zero falhas/erros, quatro skips, gates Maven verdes; cobertura
91,53% linhas/76,43% branches. Dois testes novos sintéticos registram a
expansão no normalizer e a recusa de forma antes de mapper/staging no B58.
Skips: três symlinks condicionais Windows e comando Cotações opt-in desabilitado.

Evidências: target/b62-source-20260910-161039/, incluindo inventory.json, before/,
request, ledger, source-summary, logs, java-verification, diffs e receipt.
Conferir final-checks.json/delivery-checks.json antes de presumir gate de entrega.
Primeiros autoteste e comando Maven falhos preservados. Intake anterior
em target/b62-intake-20260910-155925/ não foi reescrito após a nova instrução.

Sucessão: docs/catalogos/bloco62-investigacao/manifesto.json e
scripts/validation/Test-Bloco62Investigation.ps1 -IncludePrivateEvidence -SelfTest.
Quatro snapshots preservam STATE/trilha/RETOMADA/validator B61. B61/B60 e seus
recibos físicos continuam históricos; novo acesso à fonte não renova B60.

Próximas ações:
1. Conferir checkpoint, sucessão e registros finais; não repetir a campanha4/4.
2. Revisar binding/release de forma/path Coletas e ligação test-only da fonte
   com base na divergência registrada, sem ampliar o contrato por hipótese.
3. Preparar comparação independente/representativa por rota e tratar V2-041
   com seus responsáveis. Users e Coletas continuam independentes e candidatas.

Roadmap67/115,48 pendentes,191 rotas,zero AGORA. V2-012a/b/c seguem abertos;
Q-MAN-01 conserva EXTERNAL_HOLD. Users SHADOW_UPSERT_ONLY não prova snapshot,
completude, ausência ou exclusão. Sem efeito desconhecido ou processo operacional ativo.
