param (
    [string]$Profile = "postgresql"
)

$ErrorActionPreference = "Stop"

Write-Host "1. Building Project..." -ForegroundColor Cyan
.\mvnw.cmd clean install -DskipTests
if ($LASTEXITCODE -ne 0) { Write-Error "Build failed"; exit 1 }

Write-Host "`n2. Running Flyway Migration ($Profile)..." -ForegroundColor Cyan
# Run the Spring Boot app with the specified profile
.\mvnw.cmd -pl flyway-extended-demo spring-boot:run "-Dspring-boot.run.arguments=--logging.level.org.flywaydb=INFO" "-Dspring-boot.run.profiles=$Profile"

Write-Host "`n3. Verifying Database State via Docker..." -ForegroundColor Cyan

if ($Profile -eq "postgresql") {
    $containerName = "some-postgres"
    if (!(docker ps -q -f name=$containerName)) { Write-Error "Container '$containerName' is not running."; exit 1 }

    Write-Host "Checking for successful execution of V1__Initial_Schema.sql..." -ForegroundColor Yellow
    $migration = docker exec $containerName psql -U postgres -d postgres -t -c "SELECT success FROM flyway_schema_history WHERE version = '1' AND script = 'V1__Initial_Schema.sql';"
    if ($migration.Trim() -eq "t") { Write-Host "SUCCESS: V1 verified." -ForegroundColor Green } else { Write-Error "FAILURE: V1 not found." }

    Write-Host "`nChecking 'test_table'..." -ForegroundColor Yellow
    docker exec $containerName psql -U postgres -d postgres -c "\d test_table"

} elseif ($Profile -eq "oracle") {
    $containerName = "oracle21c"
    if (!(docker ps -q -f name=$containerName)) { Write-Error "Container '$containerName' is not running."; exit 1 }

    # Oracle verification is harder to script via CLI one-liners without sqlplus formatting, 
    # but the Java Callback already verified it!
    # We will just do a simple check here.
    Write-Host "Checking 'TEST_TABLE' via SQL*Plus..." -ForegroundColor Yellow
    
    # We use a heredoc passed to bash execution of sqlplus
    # Note: Requires ORACLE_SHELL or similar. simpler to rely on app logs, but here is a try:
    docker exec $containerName bash -c "export ORACLE_SID=XE; echo 'SELECT count(*) FROM user_tables WHERE table_name = ''TEST_TABLE'';' | sqlplus -S system/mysecretpassword"
    
    Write-Host "`n(Note: If 1 is returned above, table exists.)"
}

Write-Host "`nVerification Complete!" -ForegroundColor Green
