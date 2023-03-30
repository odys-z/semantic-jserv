# About

File synchronization with only the latest docs available. No modification history
or confliction resolving.

Modified files can be upload again.

[CVS, SVN patterns](https://stackoverflow.com/a/36028146) or
[Operational Transform](https://en.wikipedia.org/wiki/Operational_transformation)
shouldn't be the case.

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

![sym_data pattern](https://www.symmetricds.org/images/how-symmetricds-works/change-data-capture-small.png)
<br>Image Copyright © JumpMind, Inc

- Eric Long, [*How SymmetricDS Works*](https://www.symmetricds.org/docs/how-to/how-symmetricds-works), 15 September 2012, is a brief explaiation of Change Capture, Route and Push / Pull.
