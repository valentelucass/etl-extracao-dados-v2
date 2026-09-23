# Checkpoint 0241 — P07 integral conferido

UTC: 2026-09-22T06:13:25.725Z. Anterior: docs/continuidade/checkpoints/0240-unitarios-completos-integracao-em-curso.md, SHA-256 7e8f59ddd8d3d9cffb1181e326b76f9fb99a37e3dadda01028e99232d94478f5. Estado ACEITO_NO_ESCOPO técnico local P07 e PRONTO_PARA_EXECUTAR P08. Objetivo integral do usuário P09–P33 permanece; esta qualificação local não substitui seus critérios externos.

Autoridade: physical/authority.json, vigente até 2026-09-23T04:40:21.019Z; 15840/43200 segundos reservados. Somente localhost/ETL_SISTEMA_V2_SHADOW, autenticação integrada, sintéticos e rollback. Sem DDL, commit de domínio, fonte externa, produção, publicação, deploy ou cutover. Duas reservas FULL consumidas, incluindo falha do controlador antes de Maven. Nenhuma reserva ativa; nenhuma execução de resultado desconhecido.

Execução: wrapper p07-verify-02 e build pos0236-p07-verify-01. 2263 unitários, 4 skips históricos; 492 integrações/105 classes, zero falhas/erros. Build, cobertura e UTF-8 aprovados. Comparação exata dos relatórios0233/0236 preservou identidades e multiplicidades; 77 novos casos exigidos e não ignorados. 2096 arquivos de runtime conferidos entre snapshot, build e workspace. Readback físico posterior confirmou alvo e agregados anteriores; rollback confirmado. Pins em checkpoint-0241-pins.json, logs completos no build e physical/.

Segurança: PMD bruto mantém 36 alertas; disposição técnica específica revalidada com todos os XML unitários atuais. Nenhuma supressão, aceite nominal de SAST ou release. Scanner18 e contraprovas do verificador possuem evidência própria. Falhas RED, do formatter, do controlador e da fixture de revisão continuam preservadas.

P08 ainda não executado. Plano privado p08-plan.json: dois ZIPs, reprodução exata, SBOM, smoke do JAR extraído, guardas, duas sequências completas, oito variantes e readback. Cada efeito será reservado serialmente antes de iniciar. Recuperação: interromper dependentes no primeiro erro, preservar recibos e reconciliar efeitos antes de correção autorizada com novo ID, dentro dos limites existentes.

Próximas ações: (1) executar P08 serial, após estes gates P07; (2) conferir todos os recibos, selar resultado/matriz/sucessão e salvar checkpoint; (3) validar documentação, scanner e readback final, reconciliar processos e fechar ledger. Nenhum P externo será marcado concluído por fixture ou planejamento. 39/45 e 67/115 inalterados.
