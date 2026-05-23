param(
    [string]$BaseUrl = "http://localhost:8080",
    [string]$ResumePath = "samples/java-backend-resume.txt",
    [string]$JobDescriptionPath = "samples/java-ai-agent-jd.txt",
    [string]$AnswersPath = "samples/interview-answers-demo.json",
    [int]$TopK = 5
)

$ErrorActionPreference = "Stop"

# 统一解析相对路径，保证脚本从项目根目录或 scripts 目录执行都能找到样例文件。
function Resolve-DemoPath {
    param([string]$Path)

    $root = Split-Path -Parent $PSScriptRoot
    $candidate = Join-Path $root $Path
    if (Test-Path $candidate) {
        return (Resolve-Path $candidate).Path
    }
    if (Test-Path $Path) {
        return (Resolve-Path $Path).Path
    }
    throw "文件不存在：$Path"
}

# 发送 JSON 请求并返回反序列化结果，减少演示脚本中的重复请求代码。
function Invoke-DemoJsonPost {
    param(
        [string]$Url,
        [object]$Body
    )

    $json = $Body | ConvertTo-Json -Depth 8
    return Invoke-RestMethod -Uri $Url -Method Post -ContentType "application/json; charset=utf-8" -Body $json
}

# 输出当前演示步骤，让终端日志可以对应到页面流程。
function Write-DemoStep {
    param([string]$Text)

    Write-Host ""
    Write-Host "==> $Text"
}

$resumeFile = Resolve-DemoPath $ResumePath
$jdFile = Resolve-DemoPath $JobDescriptionPath
$answersFile = Resolve-DemoPath $AnswersPath
$jobDescription = Get-Content -Path $jdFile -Raw -Encoding UTF8
$answersJson = Get-Content -Path $answersFile -Raw -Encoding UTF8

Write-DemoStep "上传简历并生成分析"
$uploadRaw = & curl.exe -s -X POST "$BaseUrl/api/resume/upload" -H "X-Trace-Id: demo-resume-upload-001" -F "file=@$resumeFile"
if ($LASTEXITCODE -ne 0) {
    throw "curl 上传失败，退出码：$LASTEXITCODE"
}
$upload = $uploadRaw | ConvertFrom-Json
if (-not $upload.resumeId) {
    throw "上传接口未返回 resumeId：$uploadRaw"
}
$resumeId = $upload.resumeId
Write-Host "resumeId: $resumeId"
Write-Host "简历评分: $($upload.scoreResult.overallScore)"

Write-DemoStep "执行岗位匹配"
$match = Invoke-DemoJsonPost "$BaseUrl/api/jd/$resumeId/match" @{
    jobDescription = $jobDescription
}
Write-Host "匹配分: $($match.overallScore)"
Write-Host "匹配等级: $($match.matchLevel)"

Write-DemoStep "生成岗位定制面试题"
$questions = Invoke-DemoJsonPost "$BaseUrl/api/interview/$resumeId/rag-questions" @{
    jobDescription = $jobDescription
    topK = $TopK
}
Write-Host "题目数量: $($questions.questions.Count)"

Write-DemoStep "提交固定答案并生成复盘"
$evaluation = Invoke-RestMethod -Uri "$BaseUrl/api/interview/$resumeId/submit" -Method Post -ContentType "application/json; charset=utf-8" -Body $answersJson
Write-Host "复盘评分: $($evaluation.overallScore)"

Write-DemoStep "运行 RAG 召回评估"
$ragEvaluation = Invoke-RestMethod -Uri "$BaseUrl/api/evaluation/rag-recall?topK=$TopK" -Method Get
Write-Host "TopK 命中率: $($ragEvaluation.hitRate)"
Write-Host "样例数量: $($ragEvaluation.totalCases)"

Write-DemoStep "演示页面"
Write-Host "首页: $BaseUrl/"
Write-Host "简历分析: $BaseUrl/analysis/$resumeId"
Write-Host "岗位匹配: $BaseUrl/match/$resumeId"
Write-Host "模拟面试: $BaseUrl/interview/$resumeId"
Write-Host "复盘结果: $BaseUrl/result/$resumeId"
Write-Host "AI 求职顾问: $BaseUrl/assistant/$resumeId"
Write-Host "运营看板: $BaseUrl/audit/prompt-dashboard"
