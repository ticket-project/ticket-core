@echo off
REM 스킬 본문은 .agents\skills\에 있다. .claude\skills는 거기를 가리키는 로컬 정션이며
REM git에 커밋되지 않는다.
REM
REM 클론 직후, 또는 스킬 업데이트 CLI가 .claude\skills를 실체 디렉터리로 되돌려 놓았을 때
REM 한 번 실행한다.
REM
REM   scripts\link-agent-skills.cmd

setlocal
cd /d "%~dp0.."

if exist ".claude\skills\NUL" (
  fsutil reparsepoint query ".claude\skills" >nul 2>&1
  if errorlevel 1 (
    echo 경고: .claude\skills가 실체 디렉터리다. 내용을 확인하고 지운 뒤 다시 실행한다.
    echo   ^(스킬 업데이트 CLI가 링크를 덮어썼을 수 있다. .agents\skills와 비교한다^)
    exit /b 1
  )
  rmdir ".claude\skills"
)

mklink /J ".claude\skills" ".agents\skills"
echo 완료: .claude\skills -^> .agents\skills
