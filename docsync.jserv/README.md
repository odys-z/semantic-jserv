# About

Currently experimenting database synchronization (replication) for synchronizing files
with Semantic.DA's [Ext-filev2](https://odys-z.github.io/javadoc/semantic.DA/io/odysz/semantic/DASemantics.smtype.html#extFilev2) & its [handler](https://odys-z.github.io/javadoc/semantic.DA/io/odysz/semantic/DASemantics.ShExtFilev2.html).

Some provisional policies:

- File synchronization with only the latest docs available. No modification history
or confliction resolving.

- Modified files can be upload again.

[CVS, SVN patterns](https://stackoverflow.com/a/36028146) or
[Operational Transform](https://en.wikipedia.org/wiki/Operational_transformation)
shouldn't be the case.

- User / Owner Account vs. Synchronizing Policies

User Accounts management for cloud applications is a wild world, of which the
patterns can be vary in a wide range of schemas [x.5, x.6, x.7, x.8].

[refact-docsync: cfec2ea65f4b4ba4271c49f0bcdef9fb1d385c70] The first version will
experment an architecture based upon a centrialized users management platform, for
downloading user information to synodes, supposing happend before synchronizations
begin.

# A comparison to Postgres Extension, Spock

[1] About Spock and pgEdge

See [What is Multi-Master Distributed Postgres, and Do You Need It?](https://www.pgedge.com/solutions/benefit/multi-master)

and [a conference presentation by Jan Weick, April 20, 2023, Postgres Conference Silicon Valley](https://www.pgedge.com/presentations/postgres-conference-preso-video-multi-master-replication).

### Summary:

At its core, multi-master distributed PostgreSQL allows you to have multiple master databases spread across different locations (multiple nodes), each capable of handling read and write traffic simultaneously, allowing for better performance and high-availability of your applications. It also ensures data consistency (i.e. eventual data consistency) and improved data access times for your applications by using bi-directional replication and conflict resolution.

[2] How it works

Google AI [12, 13]:

```
    - Foundational mechanism: Logical decoding
    Spock's core functionality is built on PostgreSQL's native logical decoding feature. Here's a breakdown of the process: 
    -- WAL capture:
    When data changes occur (INSERT, UPDATE, DELETE), PostgreSQL writes low-level, binary records to its Write-Ahead Log (WAL). The WAL acts as a permanent, sequential record of all changes to the database.
    -- Decoding:
    Spock uses logical replication slots to consume these WAL records. It decodes the binary data from the WAL into a logical, readable format, including table and column names.
    -- Transaction assembly:
    Changes are read and assembled into complete transactions in the order they were committed. Only after a transaction is fully committed is it passed on for replication.
    -- Output plugin:
    The structured change data is delivered to Spock's output plugin, which then transmits it to other nodes in the cluster. 

    - The multi-master replication flow
    Spock uses a publish/subscribe model to manage the replication process between nodes. 
    1. Nodes and connections:
       1. Nodes: Each PostgreSQL database instance in the cluster is considered a node.
       2. Providers: The node from which changes are replicated.
       3. Subscribers: The node to which changes are applied. In a multi-master setup, every node is both a provider and a subscriber to other nodes.
    2. Replication sets:
    ...
    3. Bidirectional data flow:
    ...

    - Advanced conflict resolution
    -- Last Update Wins (LUA):
    ...
    -- Conflict-Free Delta-Apply Columns: *
    This innovative mechanism is particularly useful for numerical data, like account balances, where concurrent increments or decrements would otherwise be lost with a "last update wins" policy.
       - Instead of replicating the final value, Spock captures and applies the change (the "delta").
       - For example, if node A adds 5 to a balance and node B adds 10 to the same balance, Spock ensures the final value on all nodes is the original value plus 15, preventing data loss.
    -- Conflict tracking:
    ...
    -- Apply-Replay for exception handling:
    ...

```

  ** This section explains the webinar's declaration why the *bal = 315*.

# References

[1] Mopati Bernerdict Kekgathetse, Keletso Letsholo,
A survey on database synchronization algorithms for mobile device,
April 2016Journal of Theoretical and Applied Information Technology 86(1):1,
[download at ResearchGate](https://www.researchgate.net/publication/300187546_A_survey_on_database_synchronization_algorithms_for_mobile_device)

[2] Eugene Ilyichev, Data Syncing in Core Data Based iOS Apps, Posted on 03.04.2014,
http://blog.denivip.ru/index.php/2014/04/data-syncing-in-core-data-based-ios-apps/?lang=en

[3] Süleyman Eken et. al., Analyzing Distributed File Synchronization Techniques for
Educational Data, Computer Engineering Department, Kocaeli University, İzmit, Turkey, [researchgate](https://www.researchgate.net/publication/260336042_Analyzing_distributed_file_synchronization_techniques_for_educational_data)

[4] Github/bcpierce00: [Unison File Synchronizer](https://github.com/bcpierce00/unison)

[5] Jens Alfke, [Replication Algorithm](https://github.com/couchbase/couchbase-lite-ios/wiki/Replication-Algorithm) of Couchbase, Feb 14, 2019

[6] Gergely Kalapos, [Database synchronization between mobile devices and classical relational database management systems](https://epub.jku.at/download/pdf/383708), MASTER'S THESIS, JKU, Linz, Jan 2015

In the chapter 4 of the thesis, the process and change-tracking schema is detailed in a few use cases.

[7] Retired Microsoft Technical Documents

- [Master-master Row-Level Synchronization](https://learn.microsoft.com/en-us/previous-versions/msp-n-p/ff650702(v=pandp.10)) &
[the example based on SQL Server](https://learn.microsoft.com/en-us/previous-versions/msp-n-p/ff649591(v=pandp.10))

    Which detailed conflict resolving policy.

    The tracking facility in database design:

    ![database design](https://learn.microsoft.com/en-us/previous-versions/msp-n-p/images/ff650702.des_synchronization_fig02(en-us,pandp.10).gif)

    Two triggers of data table

```
    CREATE TRIGGER authors_inserted ON dbo.authors AFTER INSERT, UPDATE AS
    UPDATE dbo.authors
    SET last_changed = getdate()
    WHERE au_id = inserted.au_id;

    CREATE TRIGGER authors_deleted ON dbo.authors BEFORE DELETE AS
    UPDATE dbo.authors
    SET last_changed = getdate()
    WHERE au_id = deleted.au_id;
```

&emsp;Then the synchronization is based upon SQL Server Publication & Subscription.

[8] [Dotmim.Sync](https://github.com/Mimetis/Dotmim.Sync) at Github.

- Main process [SyncAgent.SynchronizeAsync(...)](https://github.com/Mimetis/Dotmim.Sync/blob/2f77ac3c1bdec414125943ed6c16c35a98c734e4/Projects/Dotmim.Sync.Core/SyncAgent.cs#L323)

```
    // Get operation from server
    SyncOperation operation;
    (context, operation) = this.RemoteOrchestrator.InternalGetOperationAsync (
            sScopeInfo, cScopeInfo, cScopeInfoClient, context,
            default, default, cancellationToken, progress);
```
- (Base)Orchestractor:

    Compose all args to an executabl DbCommand object.

- [BaseOrchestractor.InternalSetCommandParametersValues(SyncContext context, DbCommand command, ...)](https://github.com/Mimetis/Dotmim.Sync/blob/2f77ac3c1bdec414125943ed6c16c35a98c734e4/Projects/Dotmim.Sync.Core/Orchestrators/Commands/BaseOrchestrator.Commands.cs#L117-L118)

```
    foreach (DbParameter parameter in command.Parameters)
    {
        var column = schemaTable.Columns[parameter.SourceColumn];

        object value = row[column] ?? DBNull.Value;
        syncAdapter.AddCommandParameterValue(parameter, value, command, commandType);
    }
```
Design Pattern: Interceptors

Questions: how the changes is tracked?

[9]  Ryan Kirkman, [Simple Relational Database Sync](http://ryankirkman.com/2013/02/03/simple-relational-database-sync.html)

Using sequence += 1 instead of timestamp.

[10] [Daffodil Replicator](https://web.archive.org/web/20110314142602/http://opensource.replicator.daffodilsw.com/) hosted by Internet Archive
&emsp;

&emsp;Which also uses a Publition-subscription pattern.

&emsp;
Appendix A is a useful table for mapping data types across RDBMS.

- [User's Guid](https://web.archive.org/web/20090823192326/http://opensource.replicator.daffodilsw.com/system/modules/com.daffodil.replicator/resources/Replicator/opensource/pdf/Replicator_Developers_Guide.pdf) [Backup](docsphinx/design/Daffodil_Replicator_Developers_Guide.pdf), Copyright 2005 Daffodil Software Ltd.

- open source but has downloading error

[11] [SymmetricDS](https://github.com/JumpMind/symmetric-ds), GPLv3

&emsp;A lot of router rules need to be configured, and synchroniztion is fixed according to group settings.

>    ![sym_data pattern](https://symmetricds.org/wp-content/uploads/2012/09/change-data-capture.png)
<br>Image Copyright © JumpMind, Inc

- Eric Long, [*How SymmetricDS Works*](https://www.symmetricds.org/docs/how-to/how-symmetricds-works), 15 September 2012, is a brief explaiation of Change Capture, Route and Push / Pull.

[12] pgEdge/spock, [Spock Multi-Master Replication for PostgreSQL](https://github.com/pgEdge/spock), Github

[13] pgsql-io/spock, [spock](https://github.com/pgsql-io/spock), Github

  The spock-pglogical extension provides logical streaming replication for PostgreSQL, using a publish/subscribe model. 

[x.1] [*Introducing eShopOnContainers reference app*](https://learn.microsoft.com/en-us/dotnet/architecture/cloud-native/introduce-eshoponcontainers-reference-app), Architecting Cloud Native .NET Applications for Azure, MS Documentation, 04/07/2022

[x.2] [*Relational vs. NoSQL data*](https://learn.microsoft.com/en-us/dotnet/architecture/cloud-native/relational-vs-nosql-data), Architecting Cloud Native .NET Applications for Azure, MS Documentation, 04/07/2022

[x.3] [*Cloud-native data patterns*](https://learn.microsoft.com/en-us/dotnet/architecture/cloud-native/distributed-data), Architecting Cloud Native .NET Applications for Azure, MS Documentation, 04/07/2022

>Patterns like Materialized View (local read model) & Saga (distitributed transaction), CQRS (like result-cache routine) etc. are explained here with examples.

>Discussion:<br>
  As cloud (NoSql) applications are so expensive, is a samentics supported distributed architecture worth to try?

[x.4] [Azure SQL Edge Deployment Models](https://learn.microsoft.com/en-us/azure/azure-sql-edge/overview#deployment-models)

>    [deploy model](https://learn.microsoft.com/en-us/azure/azure-sql-edge/media/overview/overview.png)

[x.5] Gilad David Maayan, [The Importance of User Management for Your Applications: Technologies and Best Practices](https://www.codemotion.com/magazine/backend/the-importance-of-user-management-for-your-applications-technologies-and-best-practices/), April 11, 2024, Code Motion, Retrieved on Sep 11, 2024.

[x.6] [How can you manage user accounts and permissions in cloud computing?](https://www.linkedin.com/advice/0/how-can-you-manage-user-accounts-permissions-wzy4f), All/IT/Services/Network Administration, LinkedIn, Retrieved on Sep 11, 2024.

[x.7] [What is the best practice for managing Application Specific Users in GCP](https://serverfault.com/questions/1038774/what-is-the-best-practice-for-managing-application-specific-users-in-gcp), serverfault, StackExchange, Retrieved on Sep 11, 2024.

[x.8] [9 User Access Management Best Practices](https://www.cloudeagle.ai/blogs/user-access-management-best-practices), CloudEagle.ai, June 24, 2024, Retrieved on Sep 11, 2024.