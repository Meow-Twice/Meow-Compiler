package ir.type;

public enum DataType{
    I32("i32"), F32("float"), I1("i1");

    private String dataTypeName;

    DataType(String dataTypeName) {
        this.dataTypeName = dataTypeName;
    }

    @Override
    public String toString() {
        return dataTypeName;
    }

    public String getDataTypeName() {
        return dataTypeName;
    }
}
