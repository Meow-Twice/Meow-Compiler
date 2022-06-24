package test;

import sun.misc.Unsafe;

import java.lang.reflect.Field;

public class Parse {

        public static void main(String[] args) throws NoSuchFieldException, IllegalAccessException {
            // String s = "0x.AP-3";
            //
            // try {
            //     float d = Float.parseFloat(s);
            //     System.out.println(d);                // 1.1
            // } catch (NumberFormatException e) {
            //     // 字符串不能被解析为双精度
            // }
            System.out.printf("%d",(int)((2 * 1.5 + 1.5) / 3) );
            // Field f = Unsafe.class.getDeclaredField("theUnsafe");
            // f.setAccessible(true);
            // Unsafe unsafe = (Unsafe) f.get(null);
            // var handle = unsafe.allocateMemory(4); // 申请 8 字节内存
            //
            // unsafe.putFloat(handle, 4.0F); // 往该内存当中写入 1024 这个 double
            // System.out.println(unsafe.getInt(handle)); // 从该内存当中读取一个 double 出来
            //
            // unsafe.freeMemory(handle); // 释放这块内存
        }

}
