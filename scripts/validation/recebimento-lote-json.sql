DECLARE @def nvarchar(max)=OBJECT_DEFINITION(OBJECT_ID('stg.usp_production_ingestion_store'));
IF @def IS NULL OR CHARINDEX(N'CREATE TABLE #recebimento_linhas',@def)=0
    OR (LEN(@def)-LEN(REPLACE(@def,N'OPENJSON(@rows)',N'')))/LEN(N'OPENJSON(@rows)')<>1
    THROW 52112,'RECEIVING_SINGLE_PASS_REQUIRED',1;
SELECT 1 AS leituraDoPacote FOR JSON PATH,WITHOUT_ARRAY_WRAPPER;
