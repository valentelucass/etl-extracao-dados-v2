# 0162 — Cadeia integral local por contratos

## Resultado e alcance

**CADEIA_INTEGRAL_LOCAL_CONCLUIDA**, com autoridade de entrega M/N no selo externo
e readback da revisão final. Pedido A–N/P01–P24 integralmente adotado; predecessor
entregue0154 e correçãoV099 preservados. Construção **39/45**, aceites **67/115**,
zero aceite real novo, sem reclassificação ou dupla contagem.

Onze famílias declaradas chegam a cinco fatos e19 SQL pelo JAR. COL/FRE/MAN/COT/
LOC/USER usam os adaptadores existentes, integrados a CAP/FAT/INV/SIN e RAS.
Referências, relações, suplementos, revisões, scopes, janela e relógio explícitos;
modo completo recusa entradas faltantes. USER permanece snapshot. Sweep tem
quatro observações e33 previews; apply integral permanece recusado.

## Evidência fechada e limites

- verify-integral-02: exit0,49:56;2080 unitários/235classes,481IT/102classes,
  zero falhas/erros; quatro skips históricos, nenhum novo. Os2037unit/458IT
  anteriores preservados por identidade XML e multiplicidade;43unit/23IT novos.
  Formatter, estática, cobertura e VerifyPhysical passaram sem relaxar limites.
- A2/B24: IDs, datas, relações, valores e páginas distintos, oráculos autorados
  antes de SQL. JAR extraído confirmou cinco fatos,19SQL e33previews em ambos.
  AusênciaUSER: exit20 antes de SQL; valor errado de oráculo e alvoLOC inexistente:
  exit40, divergência SQL02,18 demais saídas e cinco fatos exatos, sem preview.
  No casoLOC a diferença é Kg Taxado, ordinal24; não é falha SQL07.
- artifact-campaign-final-01: campanha ARTIFACT distribuída executada pelo
  launcher extraído; inspect/plan/run/status/resume/compare passaram. Recibos e
  comparação exata estão no controle privado. Todos os processos reconciliados,
  contagens antes/depois iguais, rollback confirmado, nenhum DDL adicional.
- Replay/revisão, cancelamento/deadline/lease, isolamento e falhas de suporte,
  referência/materialização, valor/chave/cardinalidade/precisão/fato exercitados.
  Provas físicas com outer rollback e commit de domínio bloqueado; contagens
  agregadas complementam essas travas, não provam durabilidade de COMMIT.
- Onze entidades/2437 campos conferidos contra contratos. V1 somente lida para
  comparação.46 candidatos revisados; nove novos tipos têm consumidor; três
  suportes Raster movidos byte a byte para testes e ausentes do JAR. Nove remoções
  anteriores preservadas. Nenhuma quota de remoção ou regra nominal inventada.
- Schema102 e V100–V102 versionadas. V099 conserva16 regressões; readback dos16
  módulos V099–V102 passou. Não reaplicar migrations nem repetir efeitos desconhecidos.
- Preservação0154:409pins/115 membros anteriores intactos; índice Git real
  ba3a0ac43a72350689bf297f2b13a6fdc502e00eed40b90f83682688b9bcdac4 inalterado.

## Pins e seleção da entrega

Rodada: target/macrobloco-cadeia-integral-20260914-01.
Catálogo: docs/catalogos/cadeia-integral-por-contratos/RELATORIO.md.
Runtime JAR SHA256 cf9f8be56bb8a60f82f6443cb0c807a3114bf03b0169029c9c3fe199923a9489.
Runtime ZIP SHA256 1722dc94640c711df6865246574331f959fa5f7a3f8fe1592dd74e87fba977a0.
Exemplos index SHA256 ba813b6ec916efb06062c5a3c5032d3c939e35da71701567f98f1a19280d3af6.
Predecessor0154 SHA256 78dd87ed08657aa560431713310dc45cf7b6a966f0ccb509d37066ea001e5429.
Último progresso0161 SHA256 e9c7261a2e25d0e552c4f9b528c31bb4702726e4f5ae5f44676f492148731bc7.

O manifesto canônico enumera a sucessão/snapshots e este checkpoint. O selo
externo final seleciona os recibos PASS dos consumidores, diff/overlay realmente
aplicados em cópia3349, scanner canônico completo com índice privado, ZIP e
readback. Não confundir o ensaio diff-rehearsal-01 com a revisão final. Candidatos
e falhas anteriores permanecem preservados; nenhum deles qualifica a entrega.
Seleção vigente: FINAL-DELIVERY.json nesta rodada, seguido do selo e readback
que ele aponta. A ausência de selo/readback significa continuar o fechamento
nesta mesma execução, sem perguntar ou encerrar com trabalho local pendente.

## Continuidade — até três ações

1. Conferir FINAL-DELIVERY.json, selo/readback e pins; se ainda ausentes, concluir
   consumidores da sucessão, diff/scanner final e embalagem nesta execução.
2. Com PASS e todos os pins íntegros, considerar entregue o escopo local A–N;
   não repetir testes/DDL/efeitos sem nova causa ou reconciliação.
3. Manter somente parcelas comprovadamente externas G01–G08 conforme
   ENTRADAS-E-EFEITOS-EXTERNOS.md: credenciais; governança/CI; fontes reais;
   regras/referências; ambiente/provas materiais; ensaio de corte; corte; desativação.

V2-041 intacta: sem segredos, API autenticada, V1 executada, produção, deploy,
cutover, serviços ou agendamentos. SQL só localhost/ETL_SISTEMA_V2_SHADOW,
autenticação Windows existente e duas travas. Sem subagentes ou confirmações.
