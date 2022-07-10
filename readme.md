# 分支规则：

**main** 分支用于比赛提交

**dev** 分支上整合 **frontend/stable** 和 **backend/stable** 的代码，随时准备后端对于前端优化不适配等造成的bug，由xry定期merge **backend/stable**到此分支。

**frontend/stable** 分支的代码为表示当前分支代码的前端生成的LLVM IR通过了本地公开点的（回归）测试。

**frontend/dev** 分支的代码为表示当前前端正在开发，优化效果等不确定。

**backend/stable** 分支表示基于该分支目前的前端代码所生成的优化后LLVM IR对应的arm汇编。xry负责将 **backend/dev** 分支通过（树莓派上运行的公开样例点的回归）测试时切换到 **backend/stable** 并merge此分支内容并push。

**backend/dev** 分支表示当前后端分支正在开发，效果不确定，无法保证通过稳定的后端。目前由yyf和xry共同开发。yyf与xry在此分支上进行开发。
