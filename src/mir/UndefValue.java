package mir;

public class UndefValue extends Value {

    private static int undefValueCnt = 0;

    private String label;
    private String name;

    public UndefValue() {

    }


    @Override
    public String getName() {
        return "undef";
    }

    public String getLabel() {
        return label;
    }
}
