@echo off
set PR_BRANCH_NAME=pr%1
set TARGET_BRANCH=master

set APACHE_GIT=https://gitbox.apache.org/repos/asf/ignite-teamcity-bot.git
set GITHUB_MIRROR=https://github.com/apache/ignite-teamcity-bot

git fetch %GITHUB_MIRROR% pull/%1/head:%PR_BRANCH_NAME%

rem jump to branch to show data 
git checkout %PR_BRANCH_NAME%

rem show author 
git --no-pager show -s 
rem removed from command --format="%%aN <%%aE>" HEAD

rem show message
echo "
git log -1 --pretty=%%B
echo  - Fixes #%1."
rem removed from command --pretty=%%B

git checkout %TARGET_BRANCH%
git merge --squash %PR_BRANCH_NAME%
