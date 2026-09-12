# ==============================================================================
# export_codebase.ps1 - Xuat Toan Bo Ma Nguon Du An Ra FULL_CODEBASE.md
# ==============================================================================

$outputFile = "FULL_CODEBASE.md"
$projectRoot = $PSScriptRoot
if (-not $projectRoot) { $projectRoot = Get-Location }

Write-Host "Dang gom toan bo ma nguon du an ra file $outputFile..."

# Danh sach cac file cau hinh goc can lay
$rootFiles = @(
    "pom.xml",
    "Dockerfile",
    ".dockerignore",
    ".env.example",
    "README.md",
    "PROJECT_STATE.md"
)

# Danh sach cac thu muc source code
$sourceDirs = @(
    "src\main\resources\db",
    "src\main\java\com\lms\model",
    "src\main\java\com\lms\util",
    "src\main\java\com\lms\dao",
    "src\main\java\com\lms\service",
    "src\main\java\com\lms\filter",
    "src\main\java\com\lms\servlet",
    "src\main\webapp\css",
    "src\main\webapp\js",
    "src\main\webapp"
)

$includedFiles = New-Object System.Collections.Generic.List[string]

# 1. Thu thap root files
foreach ($rf in $rootFiles) {
    $fullPath = Join-Path $projectRoot $rf
    if (Test-Path $fullPath) {
        $includedFiles.Add($rf)
    }
}

# 2. Thu thap file tu source dirs
foreach ($dir in $sourceDirs) {
    $fullDirPath = Join-Path $projectRoot $dir
    if (Test-Path $fullDirPath) {
        if ($dir -eq "src\main\webapp") {
            Get-ChildItem -Path $fullDirPath -File -Filter "*.html" | ForEach-Object {
                $rel = Resolve-Path -Path $_.FullName -Relative
                $rel = $rel.TrimStart(".\")
                if (-not $includedFiles.Contains($rel)) {
                    $includedFiles.Add($rel)
                }
            }
        } else {
            Get-ChildItem -Path $fullDirPath -File | ForEach-Object {
                $rel = Resolve-Path -Path $_.FullName -Relative
                $rel = $rel.TrimStart(".\")
                if (-not $includedFiles.Contains($rel)) {
                    $includedFiles.Add($rel)
                }
            }
        }
    }
}

function Get-CodeLang($ext) {
    switch ($ext.ToLower()) {
        ".java" { return "java" }
        ".js" { return "javascript" }
        ".html" { return "html" }
        ".css" { return "css" }
        ".sql" { return "sql" }
        ".xml" { return "xml" }
        ".md" { return "markdown" }
        ".ps1" { return "powershell" }
        default { return "text" }
    }
}

$sb = New-Object System.Text.StringBuilder

$sb.AppendLine("# TOAN BO MA NGUON DU AN - HE THONG HOC TAP THONG MINH (INTELLIGENT LMS)") | Out-Null
$sb.AppendLine("") | Out-Null
$dateStr = Get-Date -Format 'yyyy-MM-dd HH:mm:ss'
$sb.AppendLine("> **Thoi gian tao file:** $dateStr") | Out-Null
$sb.AppendLine("> **Tong so file:** $($includedFiles.Count)") | Out-Null
$sb.AppendLine("> **Muc dich:** Gom toan bo source code thanh 1 file duy nhat de gui cho ben thu ba xem xet, danh gia va gop y.") | Out-Null
$sb.AppendLine("") | Out-Null
$sb.AppendLine("---") | Out-Null
$sb.AppendLine("") | Out-Null
$sb.AppendLine("## MUC LUC CAC FILE") | Out-Null
$sb.AppendLine("") | Out-Null

$idx = 1
foreach ($file in $includedFiles) {
    $cleanAnchor = $file.ToLower() -replace '[^a-z0-9]', '-'
    $sb.AppendLine("$idx. [$file](#$cleanAnchor)") | Out-Null
    $idx++
}

$sb.AppendLine("") | Out-Null
$sb.AppendLine("---") | Out-Null
$sb.AppendLine("") | Out-Null

$count = 0
foreach ($file in $includedFiles) {
    $fullPath = Join-Path $projectRoot $file
    $ext = [System.IO.Path]::GetExtension($file)
    $lang = Get-CodeLang $ext
    $cleanAnchor = $file.ToLower() -replace '[^a-z0-9]', '-'

    Write-Host " -> Gom file: $file"

    $sb.AppendLine("## $file") | Out-Null
    $sb.AppendLine("<a id='$cleanAnchor'></a>") | Out-Null
    $sb.AppendLine("") | Out-Null
    $sb.AppendLine("````$lang") | Out-Null

    try {
        $content = [System.IO.File]::ReadAllText($fullPath, [System.Text.Encoding]::UTF8)
        $sb.AppendLine($content) | Out-Null
    } catch {
        $sb.AppendLine("// Loi doc file: $_") | Out-Null
    }

    $sb.AppendLine("````") | Out-Null
    $sb.AppendLine("") | Out-Null
    $sb.AppendLine("---") | Out-Null
    $sb.AppendLine("") | Out-Null
    $count++
}

$destination = Join-Path $projectRoot $outputFile
[System.IO.File]::WriteAllText($destination, $sb.ToString(), [System.Text.Encoding]::UTF8)

Write-Host "Da xuat thanh cong $count file ma nguon vao $destination"
