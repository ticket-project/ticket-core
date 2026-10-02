@echo off
REM Skill sources live in .agents\skills\. .claude\skills is a local junction to it and is not committed.
REM Run once after cloning, or when a skill update tool has replaced .claude\skills with a real directory.
REM ASCII only: cmd.exe misreads UTF-8 text in batch files.
REM
REM   scripts\link-agent-skills.cmd

setlocal
cd /d "%~dp0.."

REM rmdir without /s removes only the junction, never the files in .agents\skills.
fsutil reparsepoint query ".claude\skills" >nul 2>&1 && rmdir ".claude\skills"
if exist ".claude\skills" (
  echo WARNING: .claude\skills is a real directory. Compare it with .agents\skills, delete it, and run again.
  exit /b 1
)

mklink /J ".claude\skills" ".agents\skills" >nul || exit /b 1
echo done: .claude\skills -^> .agents\skills
