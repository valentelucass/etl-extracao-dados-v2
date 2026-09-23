# Checkpoint 0015 — regressões integrais e correção do isolamento B57

A–D seguem tratadas no escopo local; fechar somente após verify/estáticos finais.
O primeiro verify executou 1.150 testes: uma falha de diretório Q-FND-01 e cinco
erros do guard de heap (medição exige <=512 MiB), com quatro skips existentes.
Não houve defeito funcional adicional nessa execução. Log completo no diretório
verify-92da87e7cc3d45e08b001204ab24cec5 sob target/bloco57-local.

Q-FND-02 congela recursivamente os arquivos sob caracterizacao e resources/v2-012.
Os quatro fontes novos foram movidos para contratos/mapping e as três fixtures
novas para contracts/bloco57. Nenhum arquivo histórico foi movido ou alterado.
O loader UTF-8 público faz a pré-validação limitada; os bytes originais são então
relidos pelo parser produtivo. Essa segunda árvore limitada evita ampliar a API
package-private congelada do loader. A entrada String LOC mantém léxico original.

O wrapper Maven foi limitado a 512 MiB apenas no processo e passa a usar saída
nova build-final, preservando build anterior. O segundo verify parou no Spotless
antes de compilar: o filtro de path do formatador não selecionava nomes Windows.
O filtro por nomes foi corrigido; terceiro verify está em execução. Logs falhos
preservados, sem clean, alteração do POM canônico ou JAR protegido.

16 validators estáticos rodaram: 15 passaram, inclusive B55 privado completo,
continuidade, trilha e seis contraprovas de sucessão em cópia isolada. Q-FND-02
falhou apenas pela adição de arquivos em suas raízes; após a realocação passou
com lock43 íntegro (qfnd02-isolation-green.log). Scanner/self-test iniciados.

Zero API, SQL, campanha, instalação, orçamento, credencial ou efeito externo
sem confirmação. 67/115 e 48 pendentes. Próximas três ações: conferir terceiro
verify; fechar scanners/estáticos atuais; produzir diff/recibos e sincronizar.
Recuperação usa initial, pre-isolation e manifestos B57; não tocar históricos.
Anterior: 0014-bloco57-fretes-e-pacote.md; SHA-256 c29ba6c9ec9589786adbb407bf270f6e43c6c9f4717129a51a408ef96165e6e4.
UTC: 2026-09-09T03:22:25.0746737Z.
