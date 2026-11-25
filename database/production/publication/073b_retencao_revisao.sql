-- rowversion exige conversão explícita para binário antes de formatar o estilo 2.
DECLARE @modulos TABLE(nome sysname PRIMARY KEY);
INSERT @modulos VALUES('ctl.usp_jujuba_retencao_catalogo'),('ctl.usp_jujuba_retencao_previa');
DECLARE @nome sysname,@ddl nvarchar(max),@origem nvarchar(100)=N'CONVERT(char(16),b.revisao,2)';
WHILE EXISTS(SELECT 1 FROM @modulos)
BEGIN
 SELECT TOP(1) @nome=nome FROM @modulos ORDER BY nome;
 SET @ddl=OBJECT_DEFINITION(OBJECT_ID(@nome,'P'));
 IF @ddl IS NULL OR CHARINDEX(@origem,@ddl)=0 THROW 52301,'Contrato de revisao inesperado.',1;
 SET @ddl=REPLACE(@ddl,@origem,N'CONVERT(char(16),CONVERT(binary(8),b.revisao),2)');
 SET @ddl=N'ALTER '+SUBSTRING(@ddl,CHARINDEX(N'PROCEDURE',@ddl),LEN(@ddl));
 EXEC sys.sp_executesql @ddl;
 DELETE FROM @modulos WHERE nome=@nome;
END;
GO
