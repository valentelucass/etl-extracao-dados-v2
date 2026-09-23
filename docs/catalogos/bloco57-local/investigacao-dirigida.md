# D — Pergunta documental nova para Q-COT-01

Pergunta: a seção geral Data Export já obtida define, para 6906, tipos e
nullability dos nove campos, inclusão das bordas de `quotes.requested_at`,
timezone e se `per` limita raízes ou linhas físicas?

Foram consultadas somente a descrição da pasta DATA EXPORT e as descrições
“2) Consulta estrutura dos templates” e “3) Executa consulta relatório” da
coleção local `target/esl-documentacao-postman/public-collection.json`.
SHA-256: `2412415bc4387d720c95791bd61f0b8ba6e7ab64f1d482aca6903ffbfc5d23ac`.
Recibo de aquisição/verificação anterior presente e hashes conferidos. Não houve
download novo, reanálise das 191 entradas ou repetição de sonda da conta ESL.

Seções públicas correspondentes:
[estrutura](https://documenter.getpostman.com/view/20571375/2s9YXk2fj5#71544ee2-32d7-441f-8160-6f468327634c),
[consulta](https://documenter.getpostman.com/view/20571375/2s9YXk2fj5#50f26905-387b-4764-88f0-86c0205af9b6),
[observações Data Export](https://documenter.getpostman.com/view/20571375/2s9YXk2fj5#0ae0ce29-746a-4fd7-ba70-9f2d5ea432f0).
As duas descrições indicam atualização em 08/07/2024; isso não assegura revisão
atual do template personalizado.

Resultado: o texto confirma GET com template e tenant, intervalo mínimo de dois
segundos por IP e restrições adicionais para consultas históricas. Não declara
as garantias específicas perguntadas. Para o pacote futuro propõe-se uma janela
recente, sem reextração automática nem espera para renovar cota. O exemplo com
body não altera o transporte GET_WITH_QUERY já adotado pelo projeto. O termo
tenant da URL também não preenche automaticamente `tenant_scope` da identidade.

## Efeito sobre os holds

Essa informação não resolve raiz/parcela/rateio 8636, título/crosswalk/filhos 4924,
raiz/Frete/invoices 10633 ou raiz/ocorrência/minuta 6392. Nenhuma evidência nova
pertinente a essas identidades apareceu nesta consulta dirigida. P08–P11 e
V2-029–032 continuam bloqueadas pelo mesmo pacote de lacunas do B56; não foi
reiniciada sua investigação nem implementada vertical sem os pré-requisitos.
FAT-02 continua dependendo da decisão fiscal para coexistência CT-e/NFS-e.

O próximo pacote de Cotações declara os inputs ausentes em JSON. Escopos,
janela representativa, garantias temporais e adoção externa continuam pendentes.
V2-041 permanece aberta: não houve pergunta repetida ou rotação de credenciais.
