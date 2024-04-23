CREATE USER readusertest WITH PASSWORD 'readusertest';
CREATE USER adminusertest WITH PASSWORD 'adminusertest';
CREATE USER writeusertest WITH PASSWORD 'writeusertest';

ALTER USER adminusertest CREATEDB;