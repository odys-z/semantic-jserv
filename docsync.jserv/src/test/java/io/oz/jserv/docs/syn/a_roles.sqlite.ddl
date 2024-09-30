CREATE TABLE a_roles(
roleId TEXT(20) not null, 
roleName TEXT(50), 
remarks TEXT(200),
orgId TEXT(20),
CONSTRAINT a_roles_pk PRIMARY KEY (roleId));