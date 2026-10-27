# 0640 — Publicação por página e ligação Main validadas localmente

Continuação de27/10/2026. O usuário ativou o V1 para a atualização diária.
Preservá-lo e continuar as tarefas locais do V2. Nenhuma nova Source ou conexão
SQL ocorreu neste checkpoint. A extração integral e a comparação externa das
onze entidades não estão encerradas.

## Resultados executados

| Alteração | Resultado real | Original | Tempo e pico |
|---|---|---|---|
| Publisher privado de Usuários, propostaV3 | 12 testes, zero falhas;39 classes | 0, sem timeout | 13.946ms /513.339.392bytes |
| Ligação da Main ao CoreV6, propostaV2 | 8 testes novos e89 regressões em grupos81/4/4; seis filhos fechados0 | 0, sem timeout | 31.912ms /361.844.736bytes |
| Entrada própria de Usuários sobre o publisher final | 3 testes, zero falhas; 14 classes | 0, sem timeout | 10.193ms /512.876.544bytes |
| Modelo puro dos helpers SourceMain | 3 grupos, zero falhas; somente PowerShell | 0, sem timeout | 1.678ms /69.185.536bytes |

São qualificações locais, com transportes e banco sintéticos. Não comprovam nova
consulta externa, fim real de Usuários, gravação permanente ou paridade externa.
As provas de API/banco anteriores continuam com seus escopos originais.

## Usuários: fechamento corrigido e doze testes aprovados

A proposta anterior6160 preservou o resultado9PASS/3FAIL e o diagnóstico real
das três falhas em finish. O caso mínimo7ECE confirmou que o recibo serializado
voltava com o mesmo número representado por outra classe numérica Java; a
igualdade direta das árvores recusava o fechamento. A proposta nova normaliza
somente a árvore técnica esperada pelo mesmo parser estrito, sobre os mesmos
bytes serializados, e compara a árvore inteira ao readback. Não mudou os campos
originais, o transporte, o parser de negócio ou os sete arquivos emprestados.

As nove regressões anteriores e os três grupos novos passaram. Os grupos
cobrem publicação antes da próxima consulta, conservação dos campos originais
recusados, falha antes da renomeação, limite de volume, fechamento incerto e
Capture estrangeira. A verificação adicional do recibo usa valores inteiros
lidos efetivamente e mantém os limites e a indicação de parada.

ManifestoF98FC898AB61FD2252CD410E9C18FCD6111A96AAE212A41D19267CBD72D1ACBC;
revisãoE011C33A0A52968355B6F671497B42FBABCDDD10AED5380422D97E17A35251BD;
caller046EA251C5B464041B56AF70D2A5E77BC4801187EDCB0208BF86A6F46AEEA37F.
Qualificação77814D07EEEE177DAD1192B248DDC0CC97F4C3E92973772B6ADA4E6B22617009;
Original79DF1E8B00526D5856C5C157096453552A48F507DAF49EB61BAD518D365956F0;
JUnit83FB292A4F14CDFB8DC1C5443693CCDC0E41227ADEFC8AA93C517B1106906882.

O publisher permanece auxiliar. Salva páginas privadas com criação nova,
force/readback e renomeação atômica; fechamento do prefixo é separado de fim da
Source e Original0. Teto privado128MiB, incluindo cauda parcial e fechamento;
retenção global64MiB mantida. O teto100000 linhas não é capacidade garantida.
Não há retomada do V3 antigo ou cursor persistido. Nenhuma nova leitura real de
Usuários começou, e o produto canônico não ganha conservação integral por essa
qualificação auxiliar.

## Usuários: entrada própria aprovada

A entrada foi compilada contra as 39 classes do publisher já qualificado,
sem repetir os doze testes. Os três grupos passaram pelo fluxo real de
observação, parser, mapper e gravação privada, usando transporte sintético.
Cobrem duas páginas até o término, conservação da primeira página quando a
seguinte retorna 429 sem repetição e limpeza recusada com fechamento do executor.
O retorno da entrada usa o mesmo cálculo de código de saída testado.

Manifesto81D30314CCD8843EAC31D61C60183173E401A4AE30954ED4E95DA72E6E15361C;
revisão927403352939AF7182168305A00CE2BAFE0375E66805BB248828ADA949742582;
qualificação8FDD9C7983FBCE63843417DE64F4B595A09D6B08DBC1429E306738DE272C698B;
OriginalD1901912A66FBC444A80A93FED757192C058CD22D02D2ACAEE1CC766FC2C9B8C;
JUnit1F33F549947D02896F5766BA82296C11D4EB62BA0BEDB08A60E47444B972C599.
Não houve consulta externa ou fim real de Usuários. O controller completo
continua exigindo revisão e qualificação próprias.

## Main: oito testes e regressões aprovados

A única correção entre as propostas884574 eEE1E foi no teste que confundia o
domínio Main com a versão do encoder. A versão continua
source-contract-semantics-v1; o domínio entra no SHA. O novo teste confere três
goldens e recusa domínio alterado. As cinco fontes de runtime, o CoreSeal44AE,
o JAR formatadoA98F e o runner ficaram iguais. A falha anterior7PASS/1FAIL e
Original1 não foi reescrita.

Os três casos Main.run genuína→perfil fixo→recusa por CODE passaram. Outros
casos distinguem API direta, origem de classes/JAR, semântica, contexto e
retorno físico sem System.exit interno. As89 regressões foram executadas em
JVMs separadas contra JAR153, nos grupos81/4/4; não herdam322/3 anteriores.
Origens das nove classes e inventários foram verificados antes e depois.

ManifestoEE1E0113541779A20BCE2AD0D5E4E65E671A3C015AE51CB991545DFE48143102;
executor5583C8A62CA94144F419E75A8443416AA6F48CD038A2C837AA61E95C1F7485BC;
revisõesAB8EC3B52046E914D6C98A7C7DD587ED2E204D1421F53BEE1A865FFCEC3C1761
e2025CC134897A901C010160F8FCB43A81D08775A43399531AD0D11F455D32F77.
QualificaçãoBEF2BA3CA01B0EAA3E5C02E425FB3C5339376CF5312B3F97A075044A759E0A9E;
OriginalE057002728579191EDE388BB17647D19A7BB79433EBA8FCB6118C059322D816A;
JUnit669F73381C535A27F91324643E7CF4DD3B76BB866960C5E20FCBE2D9C48D5E8D.

Isso qualifica a composição local. Não emite CODE, reserva, janela, nova coorte
nativa ou admissão física. A futura unidade SourceMain MFT deve declarar teto8,
que é o limite síncrono do Budget existente. O históricoMFT315 usou3 chamadas;
esse número observado não representa teto3 no novo caminho.

## Publicação e continuação

Main.java e MainEntityRollbackCommand.java, com quatro documentos, estão
publicados em2444f621fa0f91d6445eba47c680d9639f90c98f, push e árvore limpa
confirmados. Os blobs Java no índice e no commit são exatamente os bytes
formatados e qualificados. Scan integral4759 candidatos/4755 textos/4 binários,
zero ocorrências e Original0. ProvaFAB3DB3E573827A123448314D24FF78330A7BB99B699D55D917F92504AF6A1C1.
AST atualizadoA200:46158 nós/120665 relações, sem LLM/clustering.

A entrada de Usuários passou os três testes próprios sobre o publisher final.
O controller anterior6A9 foi bloqueado por dois erros: ausência de recibo
virava contagens zero; revisão apenas pinada não era associada ao manifesto
correto. O sucessor deve manter contagens desconhecidas como null e vincular
recibo/revisão ao mesmo escopo. Nenhum desses preparos libera Source.

O modelo puro dos helpers SourceMain passou três grupos locais: argumentos
Main17 exatos; resumo Main separado das provas físicas; projeção da última
Source Raster0637 com os recibos reais e os contadores globais não finais.
Qualificação924F7DF2F477C2150189D41676053148FA18E6FA81546AC9CA6E2581C96FD61E;
Original2D147E019CCCE779994FCC8B25598BF10443DCE3F06A43827B1F022932C1972E;
resultado3EEF2F04EEB6BB426C9A5F206A7F32F271986BB508FD214B319A75808216262F.
Foi usado snapshot imutável do ledger anterior ao claim. A preparaçãoE0DA
continua recusando a execução operacional. O sucessor que implementa os
leitores operacionais recebe revisão separada; os três grupos puros não o
qualificam. CODE, reserva, janela e qualificação nativa futura ficam ausentes
e devem recusar. V1 ativo permanece uma dependência externa; não renovar
orçamento pelo tempo ou presumir que terminou.

Permanecem aguardando resposta a exceção específica para18 SQL/quatro grupos,
a exceção Raster separada7 DDL/5 alterações e a autenticação no site ESL.
AGENTS continua proibindo DDL/grants/Flyway. Não houve alteração estrutural,
conta, permissão, versãoSQL16.0.1000.6, V1, PM2 ou produção. Coletas1,
Manifestos315 e Cotações287 mantêm as provas de leitura no banco com rollback;
Raster108/355 mantém somente a comparação parcial já comprovada com Excel.
