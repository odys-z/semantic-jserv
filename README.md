# About semantic-jserv
A semantic data service web application using protocol based on json.

- semantic.jserv
The service lib, depends on servlet and semantic-DA.

- jserv-sample
A sample project (quick start project) showing how to import and using semantic.jserv.

For showing how to access the service, a client has also implemented with a sample project, see jclient/js/test/vue/demo.app.vue.

# Quick Start

pom.xml

~~~
    <dependency>
        <groupId>io.github.odys-z</groupId>
        <artifactId>semantic.jserv</artifactId>
        <version>1.0.0-SNAPSHOT</version>
    </dependency>
~~~

For example, see [jserv-sample](https://github.com/odys-z/semantic-jserv/tree/master/jserv-sample).

The jserv-sample project is used to illustrating how to use semantic.jserv.

The jclient/js/test/index.html, with index.js is the starting point for understand how the jserv-sample web application serving the client with json data.

For tutorial, see [jserv-sample quick start](https://odys-z.github.io/Anclient/starter/jsample.html#jsample-quick-start).

The js test project (demo.app.vue) and the jserv-sample server should always working together as these two projects are used to test while implementing all depended modules.

# Why semantics-*
As all semantic-* modules and jclients are implemented based on an assumption that developers needing a framework to facilitating typical [CRUD](https://en.wikipedia.org/wiki/Create,_read,_update_and_delete) business handling, the jserv-sample Eclipse maven project implemented a typical web application structure, with session handling and protocols based on json, handling abstracted CRUD processing.

The use cases implemented in this sample project, like login, menu, list, master-details data relationship etc., are showing how semantic patterns are handled by semantic.jserv.

Further explanation about semantics patterns will be added in the future. Sorry about the inconvenient.

# About Semantics-*

## semantic-jserv

This is initially a web application lib. It should been deployed as WAR together with a web application lik jserv-sample.

See jserv-sample/pom.xml.

## semantic-transact

This is a basically sql builder. The semantic.jserv based on it to handling request, generating sqls that can be committed in a JDBC transact.

## semantic-DA

This module handling semantics configured in semantics.xml, like auto key, fk, operator-time finger print etc.

## jcleint

The client side communicating with semantic-jserv.

Currently a java client and a js client together with [easyUI](https://www.jeasyui.com) is basically working. And a C# client project is also started. We also planning porting the java client to Android.

The js client together with a vue client is also in tense developing.

#### TODO merge wiki with docs
