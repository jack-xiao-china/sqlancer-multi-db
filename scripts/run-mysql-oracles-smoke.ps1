param(
  [string]$JarPath = "target\sqlancer-2.0.0.jar",
  [string]$LogBaseDir = "target\logs",
  [string]$DbHost = "localhost",
  [int]$Port = 3306,
  [string]$Username = "root",
  [string]$Password = "password",
  [int]$TimeoutSeconds = 60,
  [int]$NumQueries = 5000,
  [int]$NumThreads = 1
)

$ErrorActionPreference = "Continue"
$ProgressPreference = "SilentlyContinue"

function Classify-RunResult([string]$out, [int]$exitCode) {
  # Tool-illegal errors: generator/serializer/internal crash or invalid SQL.
  $toolIllegalPatterns = @(
    "java\.lang\.NullPointerException",
    "java\.lang\.ClassCastException",
    "java\.lang\.ArrayIndexOutOfBoundsException",
    "java\.lang\.AssertionError:\s*DATE",
    "java\.sql\.SQLSyntaxErrorException",
    "ORACLE_ALIAS\(",
    "SELECT .* FROM\s*$"
  )
  foreach ($p in $toolIllegalPatterns) {
    if ($out -match $p) { return "TOOL_ILLEGAL" }
  }

  # Allowed: SQLancer finds DB logic bugs -> AssertionError.
  if ($out -match "java\.lang\.AssertionError") {
    return "DB_BUG_FOUND"
  }

  # DB/environment failures (allowed for this smoke, but reported): connection reset, communications link, etc.
  $dbFailurePatterns = @(
    "Communications link failure",
    "No operations allowed after connection closed",
    "ConnectionIsClosedException",
    "java\.net\.SocketException"
  )
  foreach ($p in $dbFailurePatterns) {
    if ($out -match $p) { return "DB_OR_ENV_FAILURE" }
  }

  if ($exitCode -eq 0) { return "OK" }

  # Unknown non-zero exit: treat as tool-illegal to be safe.
  return "TOOL_ILLEGAL"
}

$oracles = @(
  "AGGREGATE",
  "HAVING",
  "GROUP_BY",
  "DISTINCT",
  "NOREC",
  "TLP_WHERE",
  "PQS",
  "CERT",
  "FUZZER",
  "DQP",
  "DQE",
  "EET",
  "CODDTEST",
  "QUERY_PARTITIONING"
)

$jarFull = Resolve-Path $JarPath
New-Item -ItemType Directory -Force -Path (Split-Path $LogBaseDir) | Out-Null
New-Item -ItemType Directory -Force -Path $LogBaseDir | Out-Null

$results = @()
$summaryPath = Join-Path $LogBaseDir "mysql-oracles-smoke-summary.txt"
Remove-Item -Force -ErrorAction SilentlyContinue $summaryPath

foreach ($o in $oracles) {
  Write-Host "`n==== Running MySQL oracle $o ===="
  $args = @(
    "-jar", $jarFull,
    "--log-dir", (Resolve-Path $LogBaseDir),
    "--host", $DbHost,
    "--port", "$Port",
    "--username", $Username,
    "--password", $Password,
    "--num-tries", "1",
    "--timeout-seconds", "$TimeoutSeconds",
    "--num-queries", "$NumQueries",
    "--log-each-select", "true",
    "--num-threads", "$NumThreads",
    "mysql",
    "--oracle", $o
  )

  $out = (& java @args 2>&1 | Out-String)
  $exit = $LASTEXITCODE
  $class = Classify-RunResult -out $out -exitCode $exit

  $results += [pscustomobject]@{
    oracle = $o
    exit   = $exit
    status = $class
  }

  if ($class -ne "OK") {
    "===== ORACLE $o status=$class exit=$exit =====" | Out-File -FilePath $summaryPath -Append -Encoding utf8
    $out | Out-File -FilePath $summaryPath -Append -Encoding utf8
  }
}

$results | Format-Table -AutoSize
$results | Export-Csv -NoTypeInformation -Encoding UTF8 (Join-Path $LogBaseDir "mysql-oracles-smoke-results.csv")

$toolIllegal = $results | Where-Object { $_.status -eq "TOOL_ILLEGAL" }
if ( ($toolIllegal | Measure-Object).Count -gt 0 ) {
  Write-Host "`nTOOL_ILLEGAL detected in: $($toolIllegal.oracle -join ', ')"
  exit 2
}

Write-Host "`nSmoke passed (no tool-illegal errors)."
exit 0

