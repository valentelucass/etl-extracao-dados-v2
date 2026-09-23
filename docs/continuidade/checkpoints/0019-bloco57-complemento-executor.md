# Checkpoint 0019 — complemento B57: executor COT provado offline

Data: 2026-09-09. Anterior: docs/continuidade/checkpoints/0018-bloco57-complemento-localizacao.md; SHA-256 874e13df917a5662ffc8c302a83507dccfb1fa9dacd6548b0d9bbad7c25c319e.
Objetivo mantido: fechar F1/F2/F3 da auditoria sob o prompt B57 já adotado.
Instrução efetiva: usuário autorizou finalizar o bloco e buscar as informações.
Escopo desta unidade: código local e documentação já obtida; zero API real,
SQL, instalação, credencial, campanha física, commit/push ou renovação de teto.

## Implementação e decisão

F3 TESTADO_NA_CAMADA: entrada fechada e vinculada ao oráculo/decisão, executor
serial 6906 → consumidor parser/mapper, transporte curl isolado e comando
PowerShell com preflight padrão. Aquisição exige flag explícita, bindings e
vigência. Tetos: 1 info + até 3 páginas, 3 linhas/página, 9 totais, 64 KiB/resposta,
256 KiB totais, 120 segundos, timeout até30, intervalo2, zero retry/redirect.
Reserva antes do efeito e diretório de uso único impedem repetir com outro ID.
Recibos só contêm status, contagens, paths, motivos e hash de artefato de oráculo.
A fonte real continua bloqueada pelos inputs ausentes; não foi chamada.

Consulta documental dirigida: apenas DATA EXPORT e descrições de info/dados da
coleção já obtida; intervalos2s por IP e restrições de idade estão documentados.
Não fornecem escopo da conta, oráculo ou garantias temporais específicas 6906.
P08–P11/FAT-02/V2-041 continuam com as dependências anteriores, sem nova sondagem.

## Execução e evidência

Primeiro test-f3-01: 31 testes, duas falhas locais preservadas. Corrigidas a
leitura da seção presenceSemantics.requiredFields da decisão e uma comparação
de tipos IntNode/LongNode no round-trip do recibo (agora compara os bytes exatos).
Segundo test-f3-02: 31 testes, zero falhas/erros/skips, Java17/heap512 offline.
Inclui 12 testes runner/input, cinco curl com processo injetado (sem curl real),
dois LOC, três entidades do consumidor e nove guards de limites.
Logs/exit em target/bloco57-complemento; FORMAT e gates focados passaram.

F2: snapshots de sete deltas, mais STATES observado, foram conservados. Novo
manifesto ancora o B57 imutável; primeiro Test-Bloco57Complemento passou.
Guard suite, cadeia privada e verify global ainda pendentes. Novo catálogo
docs/catalogos/bloco57-complemento registra implementação, comandos e limites.
Inventário/recovery: initial com 1.463 cópias. Nenhum manifest histórico editado.
Aceites novos: zero; 67/115, 48 pendentes e 191 rotas abertas.
Processos próprios de testes encerrados; nenhum efeito externo desconhecido.

## Retomada — até três ações

1. Exercitar o comando público somente em preflight, com fixture privada sintética
   válida e pacote público pendente recusado; não fornecer credencial nem Execute.
2. Rodar guards da sucessão atual/histórica e verify Maven offline isolado completo.
3. Sincronizar STATES/trilha/manifests atuais, scanner/checks finais, diff próprio,
   recuperação e recibo de fechamento. Não avançar ao Bloco58 antes do fechamento.

Bloqueios externos: bindings host/scopes, janela e garantias, oráculo independente
representativo e adoção específica da futura entrada. Nenhum input foi inventado
no pacote público. Parar apenas após trabalho local elegível tratado.
