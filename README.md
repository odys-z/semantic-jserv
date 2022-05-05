
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/io.github.odys-z/semantic.jserv/badge.svg)](https://maven-badges.herokuapp.com/maven-central/io.github.odys-z/semantic.jserv/)

# About semantic-jserv
A semantic data service web application using protocol based on json.

- semantic.jserv
The service lib, depends on servlet and semantic-DA.

- jserv-sample
A sample project (quick start project) showing how to import and using semantic.jserv.

For showing how to access the service, a client has also implemented with a sample project, see [Anclient/examples](https://github.com/odys-z/Anclient/tree/master/examples).

# Quick Start

pom.xml

~~~
    <dependency>
        <groupId>io.github.odys-z</groupId>
        <artifactId>semantic.jserv</artifactId>
        <version>[1.4.0,)</version>
    </dependency>
~~~

The jserv-sample project is used to illustrating how to use semantic.jserv.

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

## AnCleint

The client side communicating with semantic-jserv.

Currently a java client and a js client together with React is basically working. And a C# client project is also came with a lib in Nuget (stoped for a while). Java client to Android also tested.

The js client together with a vue client is also in tense developing.
