# 0127 — Journal, extração e cancelamento JDBC dirigidos

EM_EXECUCAO A–N do pedido de qualificação/pacote adotado em13/09/2026.
Predecessor deste progresso:0126, SHA256e082431a041b1abcdbac2f9aeded99cb48eea3b50f319c321b2ef1aac3370e61.
Continuar até entrega única final, sem perguntas, agentes ou pedido de continue.

Provas novas observadas em target/macrobloco-qualificacao-pacote-20260913-01:

- journal-directed-01/result.json: Java17 offline,9testes dirigidos,0falhas/erros/
  skips. Cinco de contrato e quatro de journal/caminho. Não somar as cinco
  reexecutadas às cinco de contracts-directed-01 como casos únicos novos.
- package-guards-c384c4732d8143fca86d5ca274bdd0cf/result.json:25casos passaram
  em extração real de ZIP sintético, inclusive espaço/Unicode no destino. Não é
  smoke do payload real. package-guards-88d1dec579184b62a83835a2c2a5821d preserva
  erro do harness ao converter atributos de symlink para Int32; corrigido sem
  relaxar o verificador.
- cancellation-physical-01/result.json:3IT físicas passaram,0skip, rollback
  confirmado pelos agregados completos antes/depois. A sessão rastreia somente
  statements abertos e cancela o driver bloqueado. Mantém a instância concreta
  Microsoft exigida pelos18adapters TVP; nenhum adapter foi reconstruído.
- sbom-schema-directed-02-result.json: documento sintético validado e tipo
  inválido recusado contra CycloneDX1.6 oficial, com referências resolvidas
  estritamente locais. Tentativa01 recusou referência de assinatura ainda não
  mapeada; correção manteve a recusa de qualquer referência não declarada.

Dependências runtime preservadas: oito JARs e uma DLL já presentes no Maven
Central local, hashes/origem/POMs congelados em dependency-lock.json/third-party.
DLL12.8.1.x64 tem licença Microsoft Proprietary, distinta da MIT do driver JAR.
Somente schemas/licença públicos oficiais foram obtidos; feed/NVD NOT_EXECUTED.

Código adicional: QualifiedPackage, CampaignJournal, builder, módulo de extração,
launcher fixo e README do pacote. Builder/launcher ainda sem montagem/smoke real;
QualificationLaboratoryMain e oráculos outputs.synthetic.json não existem ainda.
Não declarar H/I/K concluídas. Configuração passou a query60/socket90000/case300
segundos para acomodar os timeouts compilados existentes, cujo máximo é60s;
travas e alvo mantidos. Prova física já usou a configuração nova. A unidade de
configuração precisa reexecutar a classe após essa alteração (verify final cobre).

Nenhum processo próprio ficou ativo ao fechar este checkpoint. Sem migration
ou DDL novo. Dados de runtime.start usados pela IT foram revertidos. Construção
37/45 e aceites67/115 inalterados; sucessão final/diffs ainda não emitidos.

Próximas ações:

1. Integrar executor/coordenador one-shot, journal/filhos/barreiras, planner
   consumido na fronteira SQL e gates tipados por responsabilidade/saída.
2. Construir oráculos independentes para673colunas/19saídas e integrar harness
   V2-012, metadata, chaves/multiplicidade, lineage, nulidade, SQL04 positiva.
3. Concluir pacote/SBOM e provas adversariais/concorrência/smoke/reprodutibilidade/
   escala/verify integral; preservar378IT e só então fechar N/sucessão/diffs.

O pedido integral e a matriz A–N continuam obrigatórios; este checkpoint não
reduz o escopo nem representa entrega final. Consultar WORKLOG privado ao retomar.
