# Correção offline B60

O verificador do bundle agora aceita nomes de classes Java internas contendo `$`,
mantendo as verificações de integridade e proteção. O teste de regressão e o JAR
real corrigido passaram; Maven verify offline: 1394/0/0/4.

[Checkpoint 0042](../../continuidade/checkpoints/0042-bloco60-correcao-offline-pacote-corretivo.md)
descreve a prova e o próximo passo. [Proposta física corretiva](../../../database/proposals/bloco60-correcao/README.md):
224 arquivos, 74 casos, SHA-256 cc4f84cb37f697248d8a096249480985b252a7303c2408fb4cc4614bc829a167.

Nenhuma campanha nova executada ou aprovada; nenhum aceite agregado novo.
A campanha anterior continua NOT_QUALIFIED e compensada. Seus artefatos e recibo
f2b35d102e8517b4fda6be67d2d84b8b1945077670470f77ef1d8f31307b605b permanecem verificáveis.

O manifesto desta fase registra a sucessão exata de seis arquivos e suas cópias
anteriores. Test-Bloco60CorrectiveClosure verifica os arquivos públicos; com
IncludePrivateEvidence verifica também os 3.600 hashes do recibo anterior, o build,
os bundles e o pacote. O PASS documental/offline não é qualificação física.

Recibo, relatório e diff finais: target/b60-correcao-20260910/final/.
