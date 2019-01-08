# semantic-jserv
sementic data service using protocol based on json 

## Troubleshootings
### Error: java.sql.SQLException: Name \[jdbc/inet-semantic\] is not bound in this Context. Unable to find \[jdbc\].
It's connection pool not cofigured correctly.

Here is an example (context.xml):

    <Context>
      <Resource name="jdbc/inet-semantic" global="jdbc/inet-semantic" 
  			auth="Container" type="javax.sql.DataSource"
  			maxActive="10" maxIdle="3" maxWait="10000"
  			username="user-name" password="*********" driverClassName="com.mysql.jdbc.Driver"
  			url="jdbc:mysql://...:3306/db-name?useSSL=true"
  			connectionProperties="useUnicode=yes;characterEncoding=utf8;autoReconnect=true;autoReconnectForPools=true" />
    </Context>
