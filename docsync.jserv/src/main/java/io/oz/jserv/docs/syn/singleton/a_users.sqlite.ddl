-- drop table if exists a_users;

CREATE TABLE a_users(
  userId   TEXT(20) not null,
  userName TEXT(50) not null,
  roleId   TEXT(20),
  orgId    TEXT(20),
  counter  NUMBER,
  birthday DATE,
  pswd     TEXT DEFAULT '' NOT NULL,
  iv       TEXT(200),
  CONSTRAINT a_users_pk PRIMARY KEY (userId)
);
