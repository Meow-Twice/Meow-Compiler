# Compiler 2022

编译技术 2022 年 LLVM IR 代码生成例程，基于 2022 编译大赛作品 Meow-Compiler 修改，裁剪掉了整个后端，并支持了编译课程的 `printf` 语法。

## 使用方法

默认不带参数，源程序从 `testfile.txt` 读取，生成的 LLVM IR 文件为 `llvm_ir.txt` 。

也可以使用参数指定输入与输出的文件名：

```shell
java -jar compiler.jar -emit-llvm -o <llvm_ir_filename> <sysy_filename>
```