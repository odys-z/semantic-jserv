# semantic-jserv
A sementic data service web application using protocol based on json.

## semantic.jserv
The service lib, depends on servlet and semantic-DA.

## jserv-sample
A sample project (quick start project) showing how to import and using semantic.jserv.

For showing how to access the service, a client has also implemented with a sample project, see jclient/js/vue/demo.app.vue.


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

#### SEVERE: Servlet.service() for servlet [io.odysz.semantic.jserv.R.SQuery] in context with path [/semantic.jserv] threw exception [Servlet execution threw an exception] with root cause
    java.lang.ClassNotFoundException: io.odysz.transact.sql.parts.select.JoinTabl$1
	at org.apache.catalina.loader.WebappClassLoaderBase.loadClass(WebappClassLoaderBase.java:1343)
	at org.apache.catalina.loader.WebappClassLoaderBase.loadClass(WebappClassLoaderBase.java:1173)
	at io.odysz.transact.sql.parts.select.JoinTabl.sql(JoinTabl.java:60)
Try 
- mvn clean package -U
and
- eclipse -> right click project -> maven -> update project ... -> force update snapshot / release

    
    
