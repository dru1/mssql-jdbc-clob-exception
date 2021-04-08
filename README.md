# Clob Exception Test Case for the Microsoft JDBC Driver for SQL Server

1. Build the JAR with `mvn clean compile assembly:single`
2. Create a new database or use an existing one
3. Create a new table or use an existing one, which contains a `NVARCHAR(MAX)` column
```sql
CREATE TABLE [dbo].[content](
    [id] [bigint] IDENTITY(1,1) NOT NULL,
    [content] [nvarchar](max) NOT NULL,
    CONSTRAINT [PK_content] PRIMARY KEY CLUSTERED ([id] ASC)
);
INSERT INTO [dbo].[content](content) VALUES('hello world');
```
4. Run the test with `java -jar target/mssql-jdbc-clob-exception-1.0-SNAPSHOT-jar-with-dependencies.jar`
    1. `--help` shows the help
    2. To connect to localhost on port 1933 with user `hello`, password `world` and database `example` containing the table `content` use the following arguments: `-h localhost:1933  -u hello -p world -d example -q "select top 5 [id], [content] from [content]"`
5. The test executes the given query, which processes the `[content]` column by using `java.sql.ResultSet.getClob(int)`
6. The mssql-jdbc driver version `9.2.1.jre8` throws an `IOException` (Stream Closed) which will be wrapped into a `com.microsoft.sqlserver.jdbc.SQLServerException`
7. The exception is then suppressed in the method `com.microsoft.sqlserver.jdbc.SQLServerResultSet.fillLOBs`. 
8. You should be able to see this behavior in the logs containing a line with `SQLServerResultSet:1Filling Lobs before closing: The stream is closed.`: 
```sql
2021-04-08 10:39:39.028 [main] INFO  o.e.ClobExceptionTester - Connecting to localhost:1933
2021-04-08 10:39:39.669 [main] INFO  o.e.ClobExceptionTester - Connection to localhost:1933 established
2021-04-08 10:39:39.669 [main] INFO  o.e.ClobExceptionTester - Executing query select top 5 [id], [content] from [content]
2021-04-08 10:39:39.720 [main] DEBUG c.m.s.j.SQLServerResultSet - SQLServerResultSet:1 created by (SQLServerStatement:1)
2021-04-08 10:39:39.720 [main] DEBUG c.m.s.j.SQLServerResultSet - SQLServerResultSet:1 currentRow:0 numFetchedRows:0 rowCount:-3
2021-04-08 10:39:39.732 [main] DEBUG c.m.s.j.SQLServerResultSet - SQLServerResultSet:1 Getting Column:1
2021-04-08 10:39:39.732 [main] DEBUG c.m.s.j.SQLServerResultSet - SQLServerResultSet:1 Getting Column:2
2021-04-08 10:39:39.732 [main] INFO  o.e.ClobExceptionTester - Row: [1, hello world]
2021-04-08 10:39:39.732 [main] DEBUG c.m.s.j.SQLServerResultSet - SQLServerResultSet:1 currentRow:1 numFetchedRows:1 rowCount:-3
2021-04-08 10:39:39.732 [main] DEBUG c.m.s.j.SQLServerResultSet - SQLServerResultSet:1Filling Lobs before closing: The stream is closed.
2021-04-08 10:39:39.732 [main] INFO  o.e.ClobExceptionTester - Connection to localhost:1933 closed
```
9. If you build the project with the mssql-jdbc driver version `6.2.2`, the exception is not thrown. To verify this, rebuild the JAR
with version `6.2.2` (pom.xml) and execute the test again.



