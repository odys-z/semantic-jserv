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

[7] [Dotmim.Sync](https://github.com/Mimetis/Dotmim.Sync) at Github.

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

[8]  Ryan Kirkman, [Simple Relational Database Sync](http://ryankirkman.com/2013/02/03/simple-relational-database-sync.html)

Using sequence += 1 instead of timestamp.