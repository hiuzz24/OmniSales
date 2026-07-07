# Remove divider-line comments (// =====, // -----) and blank-line
# noise from Playwright spec/utility files under the test/ folder.
# Keeps ordinary `// ...` comments intact.

param(
    [string]$Root = (Join-Path $PSScriptRoot '..')
)

$targets = @('e2e', 'api', 'utils')

# Regex for lines that are *only* a divider comment, e.g.
#   // ============================
#   // ----------------------------
#   // ----------------------------------------------------------------------
# A divider line is whitespace, then '//', then whitespace, then >= 3 '=',
# then optional trailing whitespace.
$dividerRegex = '^\s*//\s*[=\-]{3,}\s*$'

# Also collapse runs of blank lines into a single blank line.
$blankRunRegex = '(?ms)(\r?\n){3,}'

foreach ($dir in $targets) {
    $fullDir = Join-Path $Root $dir
    if (-not (Test-Path $fullDir)) { continue }

    Get-ChildItem -Path $fullDir -Recurse -Filter '*.js' | ForEach-Object {
        $path = $_.FullName
        $raw = [System.IO.File]::ReadAllText($path)
        $original = $raw

        # Split on newlines keeping the line terminators so output is stable.
        $lines = $raw -split "(?<=[\r\n])"
        $kept = foreach ($line in $lines) {
            if ($line -match $dividerRegex) { '' } else { $line }
        }
        $raw = ($kept -join '')

        # Collapse runs of 3+ consecutive newlines into exactly 2 (one blank line).
        $raw = [regex]::Replace($raw, $blankRunRegex, "`r`n`r`n")

        if ($raw -ne $original) {
            [System.IO.File]::WriteAllText($path, $raw)
            $rel = $path.Substring($Root.Length).TrimStart('\','/')
            Write-Host "cleaned: $rel"
        }
    }
}