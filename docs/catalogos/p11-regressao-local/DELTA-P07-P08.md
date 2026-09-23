# Delta aplicável de P07/P08 após os pins P11

A mudança causal é pom.xml: quatro bibliotecas Jackson2.18.11, JDBC12.8.2.jre11
e pin nativo12.8.2.x64. Java, testes, SQL/schema e oráculos não foram alterados
nesta rodada. A suíte Surefire inteira foi reexecutada; ITs/cobertura não.

| Parcela | Evidência desta revisão | Saldo que exige execução própria |
| --- | --- | --- |
| P07 build/unidades | 2162 casos/247 classes, 4 skips históricos; Enforcer/formatter/lint/compilação PASS | VerifyPhysical com 492 ITs históricos aplicáveis, oráculos/cobertura e evidência do alvo antes/depois |
| A JDBC/nativo | Driver exercitado nas fronteiras sintéticas; pin alinhado no POM | DLL12.8.2 ausente no cache, licença nativa e autenticação Windows integrada no alvo exato, rollback; autorização específica ausente |
| P08 construção | JAR e oito libs atuais; hashes em resultado.json | Pacote canônico depende também da DLL, lock exato, licenças/POMs e SBOM |
| P08 execução M | Nenhuma nova execução de aplicação | Pacote extraído, supervisor, A/B e oito recusas; reserva anterior ao efeito |
| P08 entrega N | Sucessão documental atual, sem selo físico | Reprodução byte a byte, scan aplicável, diff/overlay, selo e readback dos bytes efetivamente executados |

O lock em docs/catalogos/macrobloco-qualificacao-pacote/dependency-lock.json
conserva a revisão histórica: Jackson2.17.2 (quatro entradas), JDBC12.8.1.jre11
e nativo12.8.1.x64. Não o substituir por hashes inventados nem usar a DLL velha
com o driver novo. As oito libs novas estão no build isolado; o pacote canônico
exige nove componentes. Seus três componentes restantes (Logback classic/core
e SLF4J) mantêm as versões. Os POMs/licenças anteriores continuam históricos.

A/B requerem autorização do usuário para JDBC/nativo e ordens físicas P07 e P08
com alvo localhost/ETL_SISTEMA_V2_SHADOW, vigência, teto total e por tentativa,
ledger novo e condição de parada. Confirmar alvo e agregados atuais antes/depois;
dados sintéticos, sem COMMIT/DDL, recuperação por ROLLBACK e conferência de
processos próprios. Não reler agregados nesta rodada nem herdar saldos antigos.
O aceite nominal FEED-BASELINE é requisito independente de Segurança.

As provas P07 de0207 e P08 de0213 são preservadas com seus hashes; não são
prova automática dos novos bytes. Atualizar lock/terceiros e montar o pacote
será parte da qualificação autorizada, com sucessão própria, sem editar selos antigos.
