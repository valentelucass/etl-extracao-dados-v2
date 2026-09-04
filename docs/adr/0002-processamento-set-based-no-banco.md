# ADR 0002 — Processamento de massa no banco, orquestração no Java

- Status: Aceito e ratificado por V2-017
- Data: 2026-08-24
- Ratificação: 2026-08-30

## Contexto

As verticais possuem expansão raiz×filhos, late data, reprocessamento, relações e fatos analíticos. Materializar a travessia inteira ou executar joins, dedupe e agregações em memória aumenta o consumo, reduz a auditabilidade e repete riscos do legado. Ao mesmo tempo, persistir payload bruto por conveniência amplia desnecessariamente a superfície de dados pessoais, fiscais e financeiros.

## Decisão

O Java transporta, valida, transforma para registros tipados/minimizados, envia lotes com backpressure e orquestra estados. O SQL Server executa operações de conjunto.

- Memória produtiva é limitada a `O(maxResponseBytes + batchBytes)`, com no máximo uma resposta e um lote em voo. Nenhuma API produtiva retorna a coleção completa da execução.
- `stg` guarda campos tipados, presença e proveniência necessários ao contrato; payload integral/catch-all `metadata` não é o modelo de staging por default.
- Dedupe, frescor, joins, crosswalks, presença/sweep, current/history, DQ, reconciliação, `COUNT`, `SUM`, dimensões, fatos e promoção são SQL set-based, parametrizado e sargable.
- A promoção usa transação ou protocolo recuperável para domínio, auditoria, publication pointer e checkpoint da entidade/partição. Sweep possui execução/checkpoint separado.
- Java recebe apenas contadores `O(1)` e amostras sanitizadas/limitadas. DTO de fonte, registro de staging, domínio e saída publicada são tipos distintos.
- JPA/Hibernate pode servir cadastros pequenos; não gera DDL em runtime nem substitui operações set-based de massa.

## Consequências

- Ficam proibidos `.findAll()`, conjuntos globais de chaves e agregação JVM para volume de execução.
- V2-021 define staging/promoção; cada vertical prova platô de heap, backpressure, planos e índices em V2-050.
- Dados inválidos não desaparecem: entram em quarentena tipada com equação, threshold, owner, SLA e replay.
- A decisão não concede autorização de banco, DDL/DML produtivo ou leitura remota.
