# Meow-Once

编译实验 2022 年 LLVM IR 代码生成适配编译器，基于 2022 编译大赛作品 [Meow-Compiler](https://github.com/Meow-Twice/Meow-Compiler) 修改，裁剪掉了整个后端，并支持了课内的 `printf` 函数。

## 使用方法

默认不带参数，源程序从 `testfile.txt` 读取，生成的 LLVM IR 文件为 `llvm_ir.txt` 。

也可以使用参数指定输入与输出的文件名：

```shell
java -jar compiler.jar -emit-llvm -o <llvm_ir_filename> <sysy_filename>
```

## 功能限制

源程序中不要自定义名称与编译大赛链接库名称相同的函数，如 `getint`, `putint`, `getarray`, `putarray`, `getch`, `putch` 等。