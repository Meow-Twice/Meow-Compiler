package util;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

public class FileDealer {
    // private static final File inputFile = new File("testcase.sy");
    private static InputStream inputStream;
    private static BufferedInputStream bufferedInputStream;
    private static final FileDealer fileDealer = new FileDealer();
    private static ArrayList<String> outputStringList = new ArrayList<>();
    public static boolean ParserTryOut = false;
    private static ArrayList<String> tryOutputStringList = new ArrayList<>();
    private static ArrayList<String> mipsStringList = new ArrayList<>();
    private static ArrayList<String> ansTxtList = new ArrayList<>();

    private FileDealer(){
        // try {
        //     inputStream = new FileInputStream(inputFile);
        //     bufferedInputStream = new BufferedInputStream(inputStream);
        // } catch (FileNotFoundException e) {
        //     e.printStackTrace();
        // }

        // try {
        //     bufferedReader = new BufferedReader(new InputStreamReader(new FileInputStream("testfile.txt")));
        // } catch (FileNotFoundException e) {
        //     e.printStackTrace();
        // }
    }
    public static FileDealer getInstance(){
        return fileDealer;
    }

    // public static File getInputFile() {
    //     return inputFile;
    // }

    public static BufferedInputStream getNewBufferedInputStream(InputStream in){
        bufferedInputStream = new BufferedInputStream(in);
        return bufferedInputStream;
    }

    public static void tryClearOutputString(String s){
        tryOutputStringList.clear();
    }

    public static void tryAddOutputString(String s) {
        tryOutputStringList.add(s);
    }

    public static void outputToStream(StringBuilder strBD, OutputStream s) {
        try {
            s.write(strBD.toString().getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static void outputToFile(StringBuilder strBD, String s) {
        File f = new File(s);
        FileOutputStream fop = null;
        try {
            fop = new FileOutputStream(f);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }

        assert fop != null;
        OutputStreamWriter writer = new OutputStreamWriter(fop, StandardCharsets.UTF_8);
        try {
            writer.append(strBD.toString());
        } catch (IOException e) {
            e.printStackTrace();
        }
        try {
            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        try {
            fop.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void outputClear() {
        outputStringList.clear();
    }

    // public BufferedInputStream getBufferedInputStream(){
    //     return bufferedInputStream;
    // }

    public static void addOutputString(String s){
        outputStringList.add(s);
    }

    public static void addOutputMips(String s){
        mipsStringList.add(s);
    }

    public static void addOutputTxt(String s){
        ansTxtList.add(s);
    }

    // public  void output(String s){
    //
    // }
    public static void outputStringList(OutputStream out) {
        streamOutput(out, outputStringList);
    }

    private static void streamOutput(OutputStream fop, ArrayList<String> outputStringList) {
        OutputStreamWriter writer;
        writer = new OutputStreamWriter(fop, StandardCharsets.UTF_8);
        for (String t : outputStringList) {
            try {
                writer.append(t).append("\n");
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        try {
            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        try {
            fop.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    public static void outputMips(String str) {
        File f;
        f = new File(str);
        FileOutputStream fop = null;
        try {
            fop = new FileOutputStream(f);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }

        OutputStreamWriter writer = null;
        streamOutput(fop, mipsStringList);
    }


    public static void outputAnsTxt(String str) {
        File f;
        f = new File(str);
        FileOutputStream fop = null;
        try {
            fop = new FileOutputStream(f);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }

        OutputStreamWriter writer = null;
        streamOutput(fop, ansTxtList);
    }
}
