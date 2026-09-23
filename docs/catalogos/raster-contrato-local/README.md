# Raster mantido — contrato local e limite da implementação

O owner delegou a decisão de manter/retirar Raster. A decisão é **MANTER**, para
preservar viagens/paradas e a responsabilidade de Transit Time SM declarada no
legado. [Decisão](decisao.json) e [ADR 0039](../../adr/0039-raster-mantido-com-contrato-local-e-identidade-pendente.md).
Isso autoriza a preparação local desta frente; não inicia integração remota ou
campanha física. O módulo V2 continua desabilitado por padrão.

## Entrega e critérios

V2-034a mapeia necessidade, owner da decisão, consumidores declarados e impactos,
decide manter e dispara V2-025c sob a delegação do usuário. Nenhum aprovador
produtivo nominal ou uso atual de produção foi inventado.

V2-025c entrega [contrato.json](contrato.json): 18 aspectos explícitos, cobrindo
endpoint/transporte, autenticação, metadata, shape, filtros, bordas temporais,
ordenação, per, identidade pai/filho, paginação/cap, timezone, erros/limites,
frescor/snapshot, sentinela/duração, atomicidade e completude. O contrato fica
`TRANSITIONAL`; `PROVEN` limita-se a requisitos locais nomeados. Ausência ou
semântica pendente são classificadas, sem converter formalização em prova remota.

[campos.json](campos.json) cataloga 51 declarações: 29 da viagem, 20 da parada e
duas da rota. Tipos Java e aliases são evidência estática; coerção do Jackson não
prova wire type. [evidencias/legado.json](evidencias/legado.json) fixa 14 arquivos
locais de origem. Não foram lidos `.env`, payloads reais nem credenciais.

As fixtures são artificiais. O validator interpreta apenas a política do catálogo:
oito envelopes/erros, sete limites e um contraexemplo de reordenação. Nenhum parser,
mapper, transporte HTTP ou persistência de Raster foi ligado ao runtime por isso.
Doze contraprovas adulteram contratos/expectativas e atualizam seus hashes para
testar a recusa semântica, incluindo identidade inventada e cap como completude.

## Identidade investigada e implementação bloqueada

[identidade-pendente.json](identidade-pendente.json) registra V2-009c:

- Raiz: `CodSolicitacao/codSolicitacao`, candidato distinto de `Sequencial` e
  `CodFilial`. Faltam wire type sem coerção, escopo da origem/tenant/filial,
  estabilidade e não reutilização da chave.
- Parada: `ColetasEntregas[].Ordem/ordem`, candidata composta com a raiz. Faltam
  garantia de estabilidade, cardinalidade e comportamento sob inserção,
  reordenação, remoção/retorno e ordens repetidas. A PK legada não prova a fonte.
- O mapper legado substitui Ordem ausente pela posição. O contraexemplo sintético
  mostra por que esse fallback é inadequado, sem alegar colisão real observada.

Entregar declaração versionada do fornecedor ou oráculo independente aprovado,
com responsável pela semântica, paths, tipos, escopo, garantias/limites e casos
sanitizados de colisão, rekey, reordenação e retorno. Não basta outra fixture ou
uma amostra sem repetições. V2-034b permanece sem implementação enquanto faltar
esse vínculo; RAS-01–05 e PUB-08 continuam os critérios originais.

Depois da identidade, preparar componentes e testes de pai/filhos atômicos,
replay, nulo/colisão, zero órfão, frescor, sentinela/tempo e cap. Não há DDL pronto
para aplicar, pois preencher chaves/constraints agora inventaria o contrato.

## Verificação, continuidade e recuperação

```powershell
.\scripts\validation\Test-RasterContractCatalog.ps1 -IncludeLegacyEvidence
.\scripts\validation\Test-RasterContractGuards.ps1
.\scripts\validation\Test-RasterLocalContinuation.ps1 -IncludePrivateEvidence
```

São verificações offline. Resultados executados, logs e diff próprio ficam em
`target/bloco56-raster`; o receipt final, quando presente, identifica os testes
concluídos. Os 1.138 testes Java anteriores continuam históricos B55.

O [manifesto de sucessão](manifesto.json) preserva B56 anterior e registra deltas
exatos. As revisões anteriores ficam em `docs/continuidade/historico/bloco56-continuacao`;
o inventário/cópia inicial de 1.396 arquivos está em `target/bloco56-raster/initial`.
Nenhum manifest histórico, ledger, migration aplicada, Java ou dado foi substituído.
Recuperar somente deltas próprios após comparar hashes/edições posteriores, sem
limpar target nem apagar evidências. Nenhum rollback de banco se aplica.

Qualquer consulta Raster futura precisa de request congelado com alvo, documento,
janela/teto, credencial fora do Git e observação do resultado. SQL/runtime precisa
de pacote próprio de aplicação, verificação, recuperação e limites, após identidade
e adoção específica. Esta entrega não renova autorizações, saldo ou vigência.
