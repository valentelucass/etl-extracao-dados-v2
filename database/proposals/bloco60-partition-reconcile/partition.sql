:On Error exit
SET NOCOUNT ON;
:r "../bloco60-retomada-r2/profile-after.sql"
DECLARE @old UNIQUEIDENTIFIER='da216432-3a76-48fb-8d64-a185c9e8debc',@new UNIQUEIDENTIFIER='b787bfd8-b157-4fb0-a4a8-e777945abed7',@before CHAR(64)='69f96ddcf5f665c7117c37dbf1b32f63888b7db2692120369de024dda3a2315f',@after CHAR(64)='9d45d8955599479be003c5ed63543594e11e80805f89524debc32c6696d2e4fa';
:r "partition-body.sql"
