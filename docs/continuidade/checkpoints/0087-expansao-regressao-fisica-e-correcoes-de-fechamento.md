# Checkpoint0087 — regressão física e correções de fechamento

EM_EXECUCAO A–N. Predecessor0086 SHA256 3c96c34ee71e78197d99482b6331c906c31bf4b42795ab52b45ea43de405492a.
Mesmo pedido/inventário2549/universo45 e limites locais. V038–V051 imutáveis.

verify-02 executou1616 testes unitários (0falhas/erros,4skips históricos) e233
IT físicas (0falhas/erros/skips,95regressões anteriores+138novas). Agregados
antes/depois iguais. O build falhou depois por cobertura BRANCH<60% nos novos
pacotes (50–54%). Falha preservada; limite não relaxado. Novo teste percorre
151 campos, um shape inválido por vez, e os contratos de envelope/ordinal;
directed-field-rejections-01:159/0/0/0. Nenhuma alteração de domínio para
contornar cobertura. Nova contagem de fontes Java870.

Escalas de verify-02:16/64/256/64,5.450/12.077/87.150/13.148s aproximados;
valores exatos em scale-verification.json. 148planos inspecionados, sem spills
nem PlanAffectingConvert; páginas/lotes em voo1,retenção gerenciada0.
Medições preservadas em verify-02-measurements e actual-plan-inspection-final.json.
Sem platô/SLO. SQL062-01:179tabelas,143preexistentes preservadas e36novas sem
resíduos;read-only,sem freshDB. Os12validadores static-initial passaram.

Scanner inicial encontrou três referências sintéticas rotuladas payerToken;
11contraprovas passaram. Recursos/consumidores internos usam payerReference e
teste SQL passou a bindar o literal pelo parâmetro; regras/scanner inalterados.
Essas duas mudanças produtivas exigem nova regressão física na verify-03.

STATES recebeu evidência diretamente em V2-029–032/MAT03/04, sem checkbox novo.
Quadro45 preserva snapshot; corrige3unidades documentais nos dois lados:
26/45→32/45 com mecanismo local no subescopo explícito. Não é conclusão dos
pais, esforço/código/prazo;67/115 somenteaceites. V2-013 conserva preview mas
sem integração de mutação. Sucessor/gerador de entrega preparados, ainda sem
manifesto final: não declarar validadores de continuidade aprovados.

Nenhum processo físico ativo ao gravar; verify-03 será reservado900s/heap512.
Próximas ações: (1) verify-03 e correções necessárias; (2) JAR/scanner/fonte-JAR;
(3) Nrelatório/matriz/diff/manifesto e sucessão exata/continuidade. Preservar
tentativas falhas. Construção local não ratifica identidade, fiscal ou operação.
